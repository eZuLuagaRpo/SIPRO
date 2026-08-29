package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.HomologacionFullIfrs;
import com.bancolombia.sipro.validations.infrastructure.repository.HomologacionFullIfrsRepository;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HomologacionFullIfrsService {

    private static final Logger logger = LoggerFactory.getLogger(HomologacionFullIfrsService.class);

    private static final String PATRON_10_DIGITOS = "^[0-9]{10}$";
    private static final DateTimeFormatter FMT_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HomologacionFullIfrsRepository repo;
    private final ParametroUnicoService parametroUnicoService;

    public HomologacionFullIfrsService(HomologacionFullIfrsRepository repo,
                                       ParametroUnicoService parametroUnicoService) {
        this.repo = repo;
        this.parametroUnicoService = parametroUnicoService;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record HomologacionFullIfrsDto(
            Long id, String cuentaPlanilla, String cuentaSap, int estado,
            String creadoEn, String creadoPor) {}

    public record HomologacionFullIfrsErrorFila(int fila, String cuentaPlanilla, String error) {}

    public record HomologacionFullIfrsMasivaResultado(
            boolean success, String mensaje, int activados, int desactivados,
            List<HomologacionFullIfrsErrorFila> errores) {}

    public static class HomologacionFullIfrsRequest {
        private String cuentaPlanilla;
        private String cuentaSap;

        public String getCuentaPlanilla() { return cuentaPlanilla; }
        public void setCuentaPlanilla(String v) { this.cuentaPlanilla = v; }
        public String getCuentaSap() { return cuentaSap; }
        public void setCuentaSap(String v) { this.cuentaSap = v; }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<HomologacionFullIfrsDto> listar() {
        return repo.findAllByOrderByEstadoDescCreadoEnDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void crear(HomologacionFullIfrsRequest req, String creadoPor) {
        String cuentaPlanilla = trimRequerido(req.getCuentaPlanilla(), "cuenta planilla");
        String cuentaSap = trimRequerido(req.getCuentaSap(), "cuenta SAP");
        validarFormato(cuentaSap);
        if (repo.existsByCuentaPlanilla(cuentaPlanilla)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con el código de planilla '" + cuentaPlanilla + "'.");
        }
        repo.save(buildNueva(cuentaPlanilla, cuentaSap, creadoPor));
        logger.info("[HomologacionFullIfrs] Cuenta planilla {} creada por {}", cuentaPlanilla, creadoPor);
    }

    @Transactional
    public void desactivar(Long id) {
        HomologacionFullIfrs cuenta = findOrThrow(id);
        if (!Integer.valueOf(1).equals(cuenta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de planilla '" + cuenta.getCuentaPlanilla() + "' ya está inactiva.");
        }
        cuenta.setEstado(0);
        repo.save(cuenta);
        logger.info("[HomologacionFullIfrs] Cuenta planilla {} (id={}) desactivada", cuenta.getCuentaPlanilla(), id);
    }

    @Transactional
    public void modificar(Long id, HomologacionFullIfrsRequest req, String creadoPor) {
        HomologacionFullIfrs antigua = findOrThrow(id);
        if (!Integer.valueOf(1).equals(antigua.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden modificar cuentas activas.");
        }
        String nuevaCuentaPlanilla = trimRequerido(req.getCuentaPlanilla(), "cuenta planilla");
        String nuevaCuentaSap = trimRequerido(req.getCuentaSap(), "cuenta SAP");
        validarFormato(nuevaCuentaSap);

        boolean planillaCambia = !nuevaCuentaPlanilla.equals(antigua.getCuentaPlanilla());

        if (planillaCambia) {
            if (repo.findActivaByCuentaPlanilla(nuevaCuentaPlanilla).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una cuenta activa con el código de planilla '" + nuevaCuentaPlanilla + "'.");
            }
            antigua.setEstado(0);
            repo.save(antigua);
            repo.save(buildNueva(nuevaCuentaPlanilla, nuevaCuentaSap, creadoPor));
            logger.info("[HomologacionFullIfrs] Cuenta id={} planilla modificada: {} -> {} SAP={} por {}",
                    id, antigua.getCuentaPlanilla(), nuevaCuentaPlanilla, nuevaCuentaSap, creadoPor);
        } else {
            antigua.setCuentaSap(nuevaCuentaSap);
            repo.save(antigua);
            logger.info("[HomologacionFullIfrs] Cuenta id={} SAP actualizado: planilla={} SAP={} por {}",
                    id, antigua.getCuentaPlanilla(), nuevaCuentaSap, creadoPor);
        }
    }

    @Transactional
    public HomologacionFullIfrsMasivaResultado procesarCargaMasiva(MultipartFile archivo, String creadoPor) {
        if (archivo == null || archivo.isEmpty()) {
            return new HomologacionFullIfrsMasivaResultado(false, "El archivo no puede estar vacío.", 0, 0, List.of());
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            return new HomologacionFullIfrsMasivaResultado(false, "El archivo debe ser de formato .xlsx.", 0, 0, List.of());
        }

        List<List<String>> filas = new ArrayList<>();
        try {
            XlsxStreamingReader.readFirstSheet(archivo.getInputStream(),
                    (rowNum, valores) -> filas.add(new ArrayList<>(valores)));
        } catch (IOException e) {
            return new HomologacionFullIfrsMasivaResultado(false, "No fue posible leer el archivo: " + e.getMessage(), 0, 0, List.of());
        }

        if (filas.isEmpty()) {
            return new HomologacionFullIfrsMasivaResultado(false, "El archivo está vacío.", 0, 0, List.of());
        }

        List<String> cabecera = filas.get(0);
        if (cabecera.size() != 3) {
            return new HomologacionFullIfrsMasivaResultado(false,
                    "El archivo tiene " + cabecera.size() + " columna(s). Debe tener exactamente 3: cuenta_planilla, cuenta_SAP, estado.",
                    0, 0, List.of());
        }
        if (!cabecera.get(0).trim().equalsIgnoreCase("cuenta_planilla")
                || !cabecera.get(1).trim().equalsIgnoreCase("cuenta_SAP")
                || !cabecera.get(2).trim().equalsIgnoreCase("estado")) {
            return new HomologacionFullIfrsMasivaResultado(false,
                    "Los nombres de las columnas no son correctos. Se esperan: cuenta_planilla, cuenta_SAP, estado. " +
                    "Se encontraron: " + cabecera.stream().map(String::trim).collect(Collectors.joining(", ")) + ".",
                    0, 0, List.of());
        }

        record FilaDato(int fila, String cuentaPlanilla, String cuentaSap, int estado) {}
        List<FilaDato> datos = new ArrayList<>();
        List<HomologacionFullIfrsErrorFila> erroresFormato = new ArrayList<>();

        for (int i = 1; i < filas.size(); i++) {
            List<String> fila = filas.get(i);
            int numFila = i + 1;
            String cuentaPlanilla = fila.size() > 0 ? fila.get(0).trim() : "";
            String cuentaSap = fila.size() > 1 ? fila.get(1).trim() : "";
            String estadoStr = fila.size() > 2 ? fila.get(2).trim() : "";

            if (cuentaPlanilla.isEmpty() && cuentaSap.isEmpty() && estadoStr.isEmpty()) continue;

            String estadoNorm = estadoStr.toLowerCase();
            if (!estadoNorm.equals("activar") && !estadoNorm.equals("inactivar")) {
                erroresFormato.add(new HomologacionFullIfrsErrorFila(numFila, cuentaPlanilla,
                        "La columna estado solo acepta 'Activar' o 'Inactivar'. El valor ingresado fue: '" + estadoStr + "'."));
                continue;
            }
            datos.add(new FilaDato(numFila, cuentaPlanilla, cuentaSap, estadoNorm.equals("activar") ? 1 : 0));
        }

        if (!erroresFormato.isEmpty()) {
            return new HomologacionFullIfrsMasivaResultado(false, null, 0, 0, erroresFormato);
        }
        if (datos.isEmpty()) {
            return new HomologacionFullIfrsMasivaResultado(false, "El archivo no contiene filas de datos (solo cabecera).", 0, 0, List.of());
        }

        Set<String> cuentasParaActivar = datos.stream()
                .filter(d -> d.estado() == 1).map(FilaDato::cuentaPlanilla).collect(Collectors.toSet());
        Set<String> cuentasParaDesactivar = datos.stream()
                .filter(d -> d.estado() == 0).map(FilaDato::cuentaPlanilla).collect(Collectors.toSet());

        Set<String> existenActivasActivar = cuentasParaActivar.isEmpty() ? Set.of()
                : repo.findAllExistingCuentasPlanilla(cuentasParaActivar);
        Set<String> existenActivasDesactivar = cuentasParaDesactivar.isEmpty() ? Set.of()
                : repo.findExistingCuentasPlanilla(cuentasParaDesactivar);

        List<HomologacionFullIfrsErrorFila> errores = new ArrayList<>();
        for (FilaDato dato : datos) {
            if (dato.estado() == 1) {
                if (dato.cuentaPlanilla().isEmpty()) {
                    errores.add(new HomologacionFullIfrsErrorFila(dato.fila(), "",
                            "La cuenta planilla es obligatoria para activar una cuenta."));
                    continue;
                }
                if (!dato.cuentaSap().matches(PATRON_10_DIGITOS)) {
                    errores.add(new HomologacionFullIfrsErrorFila(dato.fila(), dato.cuentaPlanilla(),
                            "La cuenta SAP '" + dato.cuentaSap() + "' no es válida. Debe contener exactamente 10 dígitos numéricos."));
                    continue;
                }
                if (existenActivasActivar.contains(dato.cuentaPlanilla())) {
                    errores.add(new HomologacionFullIfrsErrorFila(dato.fila(), dato.cuentaPlanilla(),
                            "Ya existe una cuenta con el código de planilla '" + dato.cuentaPlanilla() + "'. No se pueden crear cuentas duplicadas."));
                }
            } else {
                if (!existenActivasDesactivar.contains(dato.cuentaPlanilla())) {
                    errores.add(new HomologacionFullIfrsErrorFila(dato.fila(), dato.cuentaPlanilla(),
                            "No se encontró ninguna cuenta activa con el código de planilla '" + dato.cuentaPlanilla() + "' para inactivar."));
                }
            }
        }

        if (!errores.isEmpty()) {
            return new HomologacionFullIfrsMasivaResultado(false, null, 0, 0, errores);
        }

        int activados = 0;
        int desactivados = 0;
        for (FilaDato dato : datos) {
            if (dato.estado() == 1) {
                repo.save(buildNueva(dato.cuentaPlanilla(), dato.cuentaSap(), creadoPor));
                activados++;
            } else {
                repo.findActivaByCuentaPlanilla(dato.cuentaPlanilla()).ifPresent(c -> {
                    c.setEstado(0);
                    repo.save(c);
                });
                desactivados++;
            }
        }

        String mensaje = "Carga completada: " + activados + " cuenta(s) activada(s) y " + desactivados + " inactivada(s).";
        logger.info("[HomologacionFullIfrs] Carga masiva por {}: {} activadas, {} desactivadas", creadoPor, activados, desactivados);
        return new HomologacionFullIfrsMasivaResultado(true, mensaje, activados, desactivados, List.of());
    }

    // ── Generación TXT ────────────────────────────────────────────────────────

    public void generarYPublicarTxt(LocalDate fechaCorte) {
        String rutaRaiz = parametroUnicoService.getString("IFRS_PLANILLAS_RUTA_SALIDA", "");
        if (rutaRaiz.isBlank()) {
            logger.info("[HomologacionFullIfrs] IFRS_PLANILLAS_RUTA_SALIDA no configurado. Se omite generación del TXT.");
            return;
        }

        Path carpeta = Path.of(rutaRaiz);
        if (!Files.isDirectory(carpeta)) {
            logger.warn("[HomologacionFullIfrs] La carpeta '{}' no existe o no es accesible. Se omite generación del TXT.", rutaRaiz);
            return;
        }

        List<HomologacionFullIfrs> registros = repo.findActivasOrdenadas();
        if (registros.isEmpty()) {
            logger.info("[HomologacionFullIfrs] No hay cuentas activas. TXT no generado para {}.", fechaCorte);
            return;
        }

        String nombreArchivo = "BanColom-Cxc-Homolo-" + FMT_YYYYMMDD.format(fechaCorte) + ".txt";
        Path destino = carpeta.resolve(nombreArchivo);

        StringBuilder sb = new StringBuilder();
        for (HomologacionFullIfrs h : registros) {
            sb.append(h.getCuentaPlanilla()).append('\t').append(h.getCuentaSap()).append('\n');
        }

        try {
            Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            logger.info("[HomologacionFullIfrs] TXT generado: {} ({} registros)", destino, registros.size());
        } catch (IOException e) {
            logger.error("[HomologacionFullIfrs] No se pudo escribir el TXT '{}': {}", destino, e.getMessage(), e);
        }
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    private HomologacionFullIfrs findOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta con id " + id + " no encontrada."));
    }

    private HomologacionFullIfrs buildNueva(String cuentaPlanilla, String cuentaSap, String creadoPor) {
        HomologacionFullIfrs h = new HomologacionFullIfrs();
        h.setCuentaPlanilla(cuentaPlanilla);
        h.setCuentaSap(cuentaSap);
        h.setEstado(1);
        h.setCreadoEn(LocalDateTime.now());
        h.setCreadoPor(creadoPor);
        return h;
    }

    private void validarFormato(String cuentaSap) {
        if (!cuentaSap.matches(PATRON_10_DIGITOS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta SAP debe contener exactamente 10 dígitos numéricos.");
        }
    }

    private String trimRequerido(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    private HomologacionFullIfrsDto toDto(HomologacionFullIfrs h) {
        String creadoEn = h.getCreadoEn() != null ? h.getCreadoEn().format(FMT_DISPLAY) : null;
        return new HomologacionFullIfrsDto(
                h.getIdHomologacionFullIfrs(),
                h.getCuentaPlanilla(),
                h.getCuentaSap(),
                h.getEstado() != null ? h.getEstado() : 0,
                creadoEn,
                h.getCreadoPor());
    }
}
