package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDashboardResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioPersonaRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Siembra archivos XLSX de apoyo para la maqueta del panel admin en entorno local.
 */
@Service
@Profile("dev")
public class AdminDashboardDevSeedService {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardDevSeedService.class);
    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ESTADO_APROBADO = "APROBADO";
    private static final String DUMMY_UID_PREFIX = "SIM-";
    private static final int MIN_REGISTROS_POR_VISTA = 5;
    private static final int FILAS_VACIAS_POR_ARCHIVO = 5;
    private static final String APROBADOS_PREFIX = "aprobados/";
    private static final String CONSOLIDADOS_PREFIX = "consolidados/";
    private static final List<String> DUMMY_HEADERS = List.of(
            "TIPO_ID",
            "NIT",
            "OFICINA",
            "DOCUMENTO",
            "MONEDA",
            "MODALIDAD",
            "ANOINIOBL",
            "MESINIOBL",
            "DIAINIOBL",
            "ANOVCTO",
            "MESVCTO",
            "DIAVCTO",
            "ANOVCTOFIN",
            "MESVCTOFIN",
            "DIAVCTOFIN",
            "CTAPUC",
            "VLRINIOBL",
            "SALDO",
            "SDOOTRCTAS",
            "INTERESES",
            "SDOVENCIDO",
            "INTCTASORD",
            "USUARIO",
            "PRODUCTO",
            "CLASIFICACION");
    private static final Set<YearMonth> PERIODOS_OBJETIVO = Set.of(
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5));

    private final FileStorageService fileStorageService;
    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final UsuarioPersonaRepository usuarioPersonaRepository;
    private final ProductoRepository productoRepository;

    public AdminDashboardDevSeedService(FileStorageService fileStorageService,
                                        SiproDetalleCargaPlanillasRepository planillaRepository,
                                        UsuarioPersonaRepository usuarioPersonaRepository,
                                        ProductoRepository productoRepository) {
        this.fileStorageService = fileStorageService;
        this.planillaRepository = planillaRepository;
        this.usuarioPersonaRepository = usuarioPersonaRepository;
        this.productoRepository = productoRepository;
    }

    public void ensureDemoData() {
        PERIODOS_OBJETIVO.stream()
                .map(YearMonth::atEndOfMonth)
                .forEach(this::ensurePlanillasAprobadasDummy);
    }

    @Transactional
    public void purgeDemoData() {
        List<SiproDetalleCargaPlanillas> dummyActivas =
                planillaRepository.findByActivoTrueAndArchivoUidStartingWith(DUMMY_UID_PREFIX);
        if (!dummyActivas.isEmpty()) {
            LocalDateTime ahora = LocalDateTime.now();
            dummyActivas.forEach(planilla -> {
                planilla.setActivo(false);
                planilla.setFechaInactivacion(ahora);
            });
            planillaRepository.saveAll(dummyActivas);
            logger.info("Se inactivaron {} planillas dummy del panel admin.", dummyActivas.size());
        }

        PERIODOS_OBJETIVO.stream()
                .map(YearMonth::atEndOfMonth)
                .forEach(this::purgeDummyFiles);
    }

    public Optional<VentanaCargaService.VentanaCalculada> obtenerVentanaDummy(LocalDate periodo) {
        if (!aplicaPeriodo(periodo)) {
            return Optional.empty();
        }

        LocalDateTime apertura = periodo.minusDays(45).atTime(0, 0);
        LocalDateTime cierre = periodo.plusDays(45).atTime(23, 59, 59);
        return Optional.of(new VentanaCargaService.VentanaCalculada(
                periodo,
                apertura,
                cierre,
                cierre,
            "REGLA_GENERAL",
                null,
                null,
                "Ventana técnica de desarrollo para validar la consolidación manual del panel admin."));
    }

    public List<AdminDashboardResponse.ArchivoEstado> completarArchivosAConsolidar(
            LocalDate periodo,
            List<AdminDashboardResponse.ArchivoEstado> actuales) {
        return completar(periodo, actuales, false);
    }

    public List<LocalDate> completarPeriodosDisponibles(List<LocalDate> actuales) {
        TreeSet<LocalDate> periodos = new TreeSet<>(Comparator.reverseOrder());
        periodos.addAll(actuales);
        PERIODOS_OBJETIVO.stream()
                .map(YearMonth::atEndOfMonth)
                .forEach(periodos::add);
        return new ArrayList<>(periodos);
    }

    public List<AdminDashboardResponse.ArchivoEstado> completarArchivosConsolidados(
            LocalDate periodo,
            List<AdminDashboardResponse.ArchivoEstado> actuales) {
        return completar(periodo, actuales, true);
    }

    private List<AdminDashboardResponse.ArchivoEstado> completar(
            LocalDate periodo,
            List<AdminDashboardResponse.ArchivoEstado> actuales,
            boolean consolidados) {
        if (!aplicaPeriodo(periodo) || actuales.size() >= MIN_REGISTROS_POR_VISTA) {
            return actuales;
        }

        List<AdminDashboardResponse.ArchivoEstado> resultado = new ArrayList<>(actuales);
        int faltantes = MIN_REGISTROS_POR_VISTA - actuales.size();
        int inicio = actuales.size() + 1;

        for (int indice = inicio; indice < inicio + faltantes; indice++) {
            DummySeed dummy = asegurarArchivo(periodo, indice, consolidados);
            if (dummy == null) {
                continue;
            }

            resultado.add(new AdminDashboardResponse.ArchivoEstado(
                    dummy.id(),
                    dummy.nombreArchivo(),
                    consolidados ? "Consolidado simulado" : "Archivo aprobado simulado",
                    consolidados ? null : dummy.pesoBytes(),
                    consolidados ? FILAS_VACIAS_POR_ARCHIVO : null,
                    consolidados ? "CONSOLIDADO" : "APROBADO",
                    consolidados
                            ? "Archivo simulado para validar la pestaña de consolidados."
                            : "Archivo simulado en aprobados para validar la pestaña A consolidar.",
                    dummy.rutaRelativa(),
                    false,
                    null,
                    dummy.nombreArchivo()));
        }

        return resultado;
    }

    private boolean aplicaPeriodo(LocalDate periodo) {
        if (periodo == null) {
            return false;
        }
        return PERIODOS_OBJETIVO.contains(YearMonth.from(periodo));
    }

    private void ensurePlanillasAprobadasDummy(LocalDate periodo) {
        if (!aplicaPeriodo(periodo)) {
            return;
        }

        Optional<DummySeedContext> seedContextOpt = resolverSeedContext();
        if (seedContextOpt.isEmpty()) {
            logger.warn("No fue posible sembrar planillas dummy para {}: no hay usuario/persona o producto base disponible.", periodo);
            return;
        }

        DummySeedContext seedContext = seedContextOpt.get();

        List<SiproDetalleCargaPlanillas> actuales = planillaRepository
                .findByFechaCorteInformacionAndEstadoPlanillaAndActivoTrue(periodo, ESTADO_APROBADO);

        if (actuales.size() >= MIN_REGISTROS_POR_VISTA) {
            return;
        }

        List<SiproDetalleCargaPlanillas> nuevos = new ArrayList<>();
        int faltantes = MIN_REGISTROS_POR_VISTA - actuales.size();
        int indiceInicial = actuales.size() + 1;

        for (int indice = indiceInicial; indice < indiceInicial + faltantes; indice++) {
            DummySeed dummy = asegurarArchivo(periodo, indice, false);
            if (dummy == null) {
                continue;
            }
            nuevos.add(construirPlanillaDummy(periodo, indice, dummy, seedContext));
        }

        if (!nuevos.isEmpty()) {
            planillaRepository.saveAll(nuevos);
            logger.info("Se sembraron {} planillas dummy para el periodo {} en dev.", nuevos.size(), periodo);
        }
    }

    private SiproDetalleCargaPlanillas construirPlanillaDummy(LocalDate periodo,
                                                              int indice,
                                                              DummySeed dummy,
                                                              DummySeedContext seedContext) {
        Producto producto = seedContext.resolverProducto(indice);
        SiproDetalleCargaPlanillas planilla = new SiproDetalleCargaPlanillas();
        planilla.setFechaCorteInformacion(periodo);
        planilla.setYear(periodo.getYear());
        planilla.setMonth(periodo.getMonthValue());
        planilla.setDay(periodo.getDayOfMonth());
        planilla.setIdUsuarioCarga(seedContext.usuarioId());
        planilla.setIdLider(seedContext.usuarioId());
        planilla.setEstadoPlanilla(ESTADO_APROBADO);
        planilla.setActivo(true);
        planilla.setNoReportaDatos(false);
        planilla.setNombreArchivoFuente(dummy.nombreArchivo());
        planilla.setPesoArchivoFuente(dummy.pesoBytes());
        planilla.setRutaArchivoAlmacenamiento(dummy.rutaRelativa());
        planilla.setIdProducto(producto.getIdProducto());
        planilla.setProducto(producto.getTitulo());
        planilla.setDescripcionLarga("Planilla dummy de desarrollo para pruebas del panel admin.");
        planilla.setCorreoUsuarioCarga(seedContext.correoUsuario());
        planilla.setNombreUsuarioCarga(seedContext.nombreUsuario());
        planilla.setCorreoLider(seedContext.correoUsuario());
        planilla.setNombreLider(seedContext.nombreUsuario());
        planilla.setNombreArea("Simulación");
        planilla.setSegmento("SIMULACION");
        planilla.setArchivoUid("SIM-" + periodo + "-" + indice);
        planilla.setIdUsuarioAprobador(seedContext.usuarioId());
        planilla.setUsuarioAprobador(seedContext.usuarioLogin());
        planilla.setFechaAprobacion(OffsetDateTime.now());
        planilla.setIp("127.0.0.1");
        return planilla;
    }

    private Optional<DummySeedContext> resolverSeedContext() {
        Optional<UsuarioPersona> usuarioOpt = usuarioPersonaRepository.findAll().stream().findFirst();
        List<Producto> productos = productoRepository.findAllByOrderByTituloAsc();

        if (usuarioOpt.isEmpty() || productos.isEmpty()) {
            return Optional.empty();
        }

        UsuarioPersona usuario = usuarioOpt.get();
        String nombreUsuario = construirNombreUsuario(usuario);
        String correoUsuario = usuario.getCorreo() != null && !usuario.getCorreo().isBlank()
                ? usuario.getCorreo()
                : "admin.dev@local";
        String usuarioLogin = usuario.getUsuario() != null && !usuario.getUsuario().isBlank()
                ? usuario.getUsuario()
                : correoUsuario;

        return Optional.of(new DummySeedContext(
                usuario.getIdUsuario(),
                usuarioLogin,
                nombreUsuario,
                correoUsuario,
                productos));
    }

    private String construirNombreUsuario(UsuarioPersona usuario) {
        String nombres = usuario.getNombres() == null ? "" : usuario.getNombres().trim();
        String apellidos = usuario.getApellidos() == null ? "" : usuario.getApellidos().trim();
        String nombreCompleto = (nombres + " " + apellidos).trim();

        if (!nombreCompleto.isBlank()) {
            return nombreCompleto;
        }

        if (usuario.getUsuario() != null && !usuario.getUsuario().isBlank()) {
            return usuario.getUsuario();
        }

        return "Admin Dev";
    }

    private DummySeed asegurarArchivo(LocalDate periodo, int indice, boolean consolidado) {
        try {
            String rutaRelativa = construirRutaRelativa(periodo, indice, consolidado);
            String nombreArchivo = construirNombreArchivo(periodo, indice, consolidado);
            Path rutaAbsoluta = fileStorageService.getAbsolutePath(rutaRelativa);

            if (!Files.exists(rutaAbsoluta)) {
                byte[] contenido = construirExcelVacio(periodo, indice, consolidado);
                fileStorageService.storeBytes(contenido, rutaRelativa, CONTENT_TYPE_XLSX);
            }

            long pesoBytes = Files.exists(rutaAbsoluta) ? Files.size(rutaAbsoluta) : 0L;
            long baseId = consolidado ? -2_000_000L : -1_000_000L;
            long id = baseId - (periodo.getMonthValue() * 100L) - indice;
            return new DummySeed(id, nombreArchivo, rutaRelativa, pesoBytes);
        } catch (IOException exception) {
            logger.warn("No fue posible sembrar archivo dummy de admin para {} índice {}: {}",
                    periodo, indice, exception.getMessage());
            return null;
        }
    }

    private void purgeDummyFiles(LocalDate periodo) {
        for (int indice = 1; indice <= MIN_REGISTROS_POR_VISTA; indice++) {
            fileStorageService.delete(construirRutaRelativa(periodo, indice, false));
            fileStorageService.delete(construirRutaRelativa(periodo, indice, true));
        }
    }

    private String construirRutaRelativa(LocalDate periodo, int indice, boolean consolidado) {
        String directorio = consolidado ? CONSOLIDADOS_PREFIX : APROBADOS_PREFIX;
        return directorio + periodo + "/" + construirNombreArchivo(periodo, indice, consolidado);
    }

    private String construirNombreArchivo(LocalDate periodo, int indice, boolean consolidado) {
        String fecha = periodo.toString();
        String prefijo = consolidado ? "SIM_CONSOLIDADO" : "SIM_APROBADO";
        return "%s_%s_%02d.xlsx".formatted(prefijo, fecha, indice);
    }

    private byte[] construirExcelVacio(LocalDate periodo, int indice, boolean consolidado) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Datos");

            Row headerRow = sheet.createRow(0);
            for (int headerIndex = 0; headerIndex < DUMMY_HEADERS.size(); headerIndex++) {
                Cell cell = headerRow.createCell(headerIndex, CellType.STRING);
                cell.setCellValue(DUMMY_HEADERS.get(headerIndex));
            }

            for (int filaIndex = 1; filaIndex <= FILAS_VACIAS_POR_ARCHIVO; filaIndex++) {
                Row row = sheet.createRow(filaIndex);
                for (int columnIndex = 0; columnIndex < DUMMY_HEADERS.size(); columnIndex++) {
                    row.createCell(columnIndex, CellType.STRING).setCellValue("");
                }
            }

            Sheet meta = workbook.createSheet("Meta");
            Row tipoRow = meta.createRow(0);
            tipoRow.createCell(0, CellType.STRING).setCellValue("tipo");
            tipoRow.createCell(1, CellType.STRING).setCellValue(consolidado ? "consolidado" : "aprobado");

            Row periodoRow = meta.createRow(1);
            periodoRow.createCell(0, CellType.STRING).setCellValue("periodo");
            periodoRow.createCell(1, CellType.STRING).setCellValue(periodo.toString());

            Row indiceRow = meta.createRow(2);
            indiceRow.createCell(0, CellType.STRING).setCellValue("indice");
            indiceRow.createCell(1, CellType.NUMERIC).setCellValue(indice);

            workbook.setSheetHidden(workbook.getSheetIndex(meta), true);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private record DummySeed(Long id, String nombreArchivo, String rutaRelativa, Long pesoBytes) {
    }

    private record DummySeedContext(Long usuarioId,
                                    String usuarioLogin,
                                    String nombreUsuario,
                                    String correoUsuario,
                                    List<Producto> productos) {

        private Producto resolverProducto(int indice) {
            return productos.get(Math.floorMod(indice - 1, productos.size()));
        }
    }
}