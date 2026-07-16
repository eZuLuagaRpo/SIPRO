package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.VentanaCargaResponse;
import com.bancolombia.sipro.validations.domain.model.SiproExcepcionVentanaCarga;
import com.bancolombia.sipro.validations.domain.model.SiproParametroUnico;
import com.bancolombia.sipro.validations.domain.model.SiproReglaVentanaCarga;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproExcepcionVentanaCargaRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproReglaVentanaCargaRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Servicio que calcula la ventana de carga permitida para un periodo de valoración.
 *
 * Lógica de prioridad:
 * 1. Calcular apertura/cierre con la Regla vigente.
 * 2. Consultar si existe Excepción para el periodo.
 * 3. Si existe, sobrescribir solo los campos informados (excepciones parciales).
 */
@Service
public class VentanaCargaService {

    private static final Logger logger = LoggerFactory.getLogger(VentanaCargaService.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CLAVE_DIAS_GRACIA_VENTANA = "DIAS_GRACIA_VENTANA";

    private final SiproReglaVentanaCargaRepository reglaRepository;
    private final SiproExcepcionVentanaCargaRepository excepcionRepository;
    private final ParametroUnicoService parametroUnicoService;
    private final ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider;

    public VentanaCargaService(SiproReglaVentanaCargaRepository reglaRepository,
                                SiproExcepcionVentanaCargaRepository excepcionRepository,
                                ParametroUnicoService parametroUnicoService,
                                ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider) {
        this.reglaRepository = reglaRepository;
        this.excepcionRepository = excepcionRepository;
        this.parametroUnicoService = parametroUnicoService;
        this.adminDashboardDevSeedServiceProvider = adminDashboardDevSeedServiceProvider;
    }

    /**
     * Valida si la fecha/hora actual está dentro de la ventana de carga
     * para el periodo de valoración dado.
     *
     * @param periodoValoracion Último día del mes (fecha de corte), ej: 2026-02-28
     * @return VentanaCargaResponse con el resultado de la validación
     */
    public VentanaCargaResponse validarVentana(LocalDate periodoValoracion) {
        LocalDateTime ahora = LocalDateTime.now();
        logger.info("Validando ventana de carga para periodo={}, ahora={}", periodoValoracion, ahora);

        Optional<VentanaCalculada> ventanaOpt = obtenerVentana(periodoValoracion);
        if (ventanaOpt.isEmpty()) {
            logger.warn("No se encontró regla de ventana de carga vigente para periodo={}", periodoValoracion);
            return buildNoConfigResponse();
        }

        VentanaCalculada ventana = ventanaOpt.get();
        boolean dentroDeVentana = !ahora.isBefore(ventana.getFechaHoraApertura())
                && !ahora.isAfter(ventana.getFechaHoraCierre());
        String mensaje = buildMensaje(dentroDeVentana, ventana.getTipoVentanaRespuesta(),
                ventana.getFechaHoraApertura(), ventana.getFechaHoraCierre(), ahora);

        logger.info("Resultado ventana para periodo={}: dentro={}, tipo={}, apertura={}, cierre={}",
                periodoValoracion, dentroDeVentana, ventana.getTipoVentanaRespuesta(),
                ventana.getFechaHoraApertura(), ventana.getFechaHoraCierre());

        VentanaCargaResponse response = new VentanaCargaResponse();
        response.setDentroDeVentana(dentroDeVentana);
        response.setTipoVentana(ventana.getTipoVentanaRespuesta());
        response.setFechaHoraApertura(ventana.getFechaHoraApertura().format(ISO_FMT));
        response.setFechaHoraCierre(ventana.getFechaHoraCierre().format(ISO_FMT));
        response.setMensaje(mensaje);
        response.setMotivoExcepcion(ventana.getMotivoExcepcion());
        return response;
    }

    /** Fallback si no existe la clave MAX_EXTENSION_EXCEPCION_DIAS en la tabla de parámetros. */
    private static final int DEFAULT_MAX_EXTENSION_EXCEPCION_DIAS = 25;

    public Optional<VentanaCalculada> obtenerVentana(LocalDate periodoValoracion) {
        Optional<SiproReglaVentanaCarga> reglaOpt = reglaRepository.findReglaVigente(periodoValoracion);

        if (reglaOpt.isEmpty()) {
            AdminDashboardDevSeedService dummySeedService = adminDashboardDevSeedServiceProvider.getIfAvailable();
            if (dummySeedService != null) {
                Optional<VentanaCalculada> ventanaDummy = dummySeedService.obtenerVentanaDummy(periodoValoracion);
                if (ventanaDummy.isPresent()) {
                    logger.info("Usando ventana técnica dev para periodo {} al no existir regla vigente.", periodoValoracion);
                    return ventanaDummy;
                }
            }
            return Optional.empty();
        }

        SiproReglaVentanaCarga regla = reglaOpt.get();
        LocalDate fechaApertura = periodoValoracion.plusDays(regla.getOffsetDiasApertura());
        LocalTime horaApertura = regla.getHoraApertura();
    int offsetDiasCierre = resolveOffsetDiasCierre(regla);
    LocalDate fechaCierreRegla = periodoValoracion.plusDays(offsetDiasCierre);
        LocalTime horaCierreRegla = regla.getHoraCierre();

        // Cierre efectivo inicia como el de la regla
        LocalDate fechaCierre = fechaCierreRegla;
        LocalTime horaCierre = horaCierreRegla;

        String fuenteVentana = "REGLA_GENERAL";
        String motivoExcepcion = null;
        LocalDate periodoExcepcion = null;

        Optional<SiproExcepcionVentanaCarga> excOpt = excepcionRepository.findByPeriodoValoracion(periodoValoracion);
        if (excOpt.isPresent()) {
            SiproExcepcionVentanaCarga exc = excOpt.get();
            if (exc.getFechaAperturaOverride() != null) {
                fechaApertura = exc.getFechaAperturaOverride();
            }
            if (exc.getHoraAperturaOverride() != null) {
                horaApertura = exc.getHoraAperturaOverride();
            }
            if (exc.getFechaCierreOverride() != null) {
                fechaCierre = exc.getFechaCierreOverride();
            }
            if (exc.getHoraCierreOverride() != null) {
                horaCierre = exc.getHoraCierreOverride();
            }

            // Cap: la excepción no puede extender el cierre más de N días después del cierre de la regla.
            // Si MAX_EXTENSION_EXCEPCION_DIAS <= 0 el cap se considera desactivado y se respeta
            // exactamente lo que el administrador configuró en sipro_parametros_excepcionventanacarga.
                long maxExtensionDias = parametroUnicoService.getLong(
                    "MAX_EXTENSION_EXCEPCION_DIAS",
                    DEFAULT_MAX_EXTENSION_EXCEPCION_DIAS);
            if (maxExtensionDias > 0) {
                LocalDateTime cierreReglaDT = LocalDateTime.of(fechaCierreRegla, horaCierreRegla);
                LocalDateTime cierreEfectivoDT = LocalDateTime.of(fechaCierre, horaCierre);
                LocalDateTime limiteMaximo = cierreReglaDT.plusDays(maxExtensionDias);
                if (cierreEfectivoDT.isAfter(limiteMaximo)) {
                    logger.warn("Excepción para periodo={} excede el límite de {} días post-cierre regla. " +
                            "Cierre excepción={}, Límite={}. Se aplica el límite.",
                            periodoValoracion, maxExtensionDias, cierreEfectivoDT, limiteMaximo);
                    fechaCierre = limiteMaximo.toLocalDate();
                    horaCierre = limiteMaximo.toLocalTime();
                }
            } else {
                logger.info("Cap de extensión desactivado (MAX_EXTENSION_EXCEPCION_DIAS={}) para periodo={}. " +
                        "Se respeta la fecha de cierre configurada por el administrador: {}T{}.",
                        maxExtensionDias, periodoValoracion, fechaCierre, horaCierre);
            }

            if (exc.getFechaAperturaOverride() != null || exc.getHoraAperturaOverride() != null
                    || exc.getFechaCierreOverride() != null || exc.getHoraCierreOverride() != null) {
                fuenteVentana = "EXCEPCION";
                motivoExcepcion = exc.getMotivo();
                periodoExcepcion = exc.getPeriodoValoracion();
                logger.info("Excepción de ventana encontrada para periodo={}: motivo={}", periodoValoracion,
                        motivoExcepcion);
            }
        }

        return Optional.of(new VentanaCalculada(
                periodoValoracion,
                LocalDateTime.of(fechaApertura, horaApertura),
                LocalDateTime.of(fechaCierre, horaCierre),
                LocalDateTime.of(fechaCierreRegla, horaCierreRegla),
                fuenteVentana,
                regla.getReglaId(),
                periodoExcepcion,
                motivoExcepcion));
    }

    private int resolveOffsetDiasCierre(SiproReglaVentanaCarga regla) {
        int offsetRegla = regla.getOffsetDiasCierre();

        try {
            Optional<SiproParametroUnico> parametroOpt = parametroUnicoService.obtenerDirecto(CLAVE_DIAS_GRACIA_VENTANA);
            if (parametroOpt.isPresent()) {
                Integer diasGracia = parseDiasGraciaVentana(parametroOpt.get().getValor(), offsetRegla, "base de datos");
                if (diasGracia != null) {
                    logger.info("Usando {}={} desde sipro_parametros_unico para la regla {}. offset_dias_cierre={} queda como fallback.",
                            CLAVE_DIAS_GRACIA_VENTANA, diasGracia, regla.getReglaId(), offsetRegla);
                    return diasGracia;
                }
            }
        } catch (Exception ex) {
            logger.warn("No fue posible consultar {} directo desde BD: {}. Se intentará con caché.",
                    CLAVE_DIAS_GRACIA_VENTANA, ex.getMessage());
        }

        int diasGraciaCache = parametroUnicoService.getInt(CLAVE_DIAS_GRACIA_VENTANA, offsetRegla);
        if (diasGraciaCache < 0) {
            logger.warn("El parámetro {} tiene valor negativo en caché ({}). Se usa offset_dias_cierre={} de la regla {}.",
                    CLAVE_DIAS_GRACIA_VENTANA, diasGraciaCache, offsetRegla, regla.getReglaId());
            return offsetRegla;
        }

        if (diasGraciaCache != offsetRegla) {
            logger.info("Usando {}={} desde caché para la regla {}. offset_dias_cierre={} queda como fallback.",
                    CLAVE_DIAS_GRACIA_VENTANA, diasGraciaCache, regla.getReglaId(), offsetRegla);
        }

        return diasGraciaCache;
    }

    private Integer parseDiasGraciaVentana(String valor, int offsetRegla, String origen) {
        if (valor == null) {
            return null;
        }

        try {
            int diasGracia = Integer.parseInt(valor.trim());
            if (diasGracia < 0) {
                logger.warn("El parámetro {} tiene valor negativo '{}' en {}. Se usa offset_dias_cierre={} de la regla.",
                        CLAVE_DIAS_GRACIA_VENTANA, valor, origen, offsetRegla);
                return null;
            }
            return diasGracia;
        } catch (NumberFormatException ex) {
            logger.warn("El parámetro {} tiene valor no numérico '{}' en {}. Se usa offset_dias_cierre={} de la regla.",
                    CLAVE_DIAS_GRACIA_VENTANA, valor, origen, offsetRegla);
            return null;
        }
    }

    private String buildMensaje(boolean dentro, String tipo, LocalDateTime apertura,
                                 LocalDateTime cierre, LocalDateTime ahora) {
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

        if (dentro) {
            if ("EXCEPCION".equals(tipo)) {
                return String.format(
                        "Ventana de carga habilitada con extensión especial. Cierre ampliado hasta el %s.",
                        cierre.format(displayFmt));
            }
            return String.format(
                    "Ventana de carga habilitada. Disponible hasta el %s.",
                    cierre.format(displayFmt));
        }

        // Fuera de ventana
        if (ahora.isBefore(apertura)) {
            return String.format(
                    "La ventana de carga para este periodo aún no ha abierto. Se habilitará el %s.",
                    apertura.format(displayFmt));
        }

        return String.format(
                "La ventana de carga para este periodo se cerró el %s. No es posible cargar archivos para esta fecha de corte.",
                cierre.format(displayFmt));
    }

    private VentanaCargaResponse buildNoConfigResponse() {
        VentanaCargaResponse response = new VentanaCargaResponse();
        response.setDentroDeVentana(false);
        response.setTipoVentana("SIN_CONFIGURACION");
        response.setMensaje("No se encontró configuración de ventana de carga. Contacte al administrador.");
        return response;
    }

    public static class VentanaCalculada {
        private final LocalDate periodoValoracion;
        private final LocalDateTime fechaHoraApertura;
        private final LocalDateTime fechaHoraCierre;
        private final LocalDateTime fechaHoraCierreRegla;
        private final String fuenteVentana;
        private final Long idReglaVentana;
        private final LocalDate periodoExcepcionVentana;
        private final String motivoExcepcion;

        public VentanaCalculada(LocalDate periodoValoracion,
                                LocalDateTime fechaHoraApertura,
                                LocalDateTime fechaHoraCierre,
                                LocalDateTime fechaHoraCierreRegla,
                                String fuenteVentana,
                                Long idReglaVentana,
                                LocalDate periodoExcepcionVentana,
                                String motivoExcepcion) {
            this.periodoValoracion = periodoValoracion;
            this.fechaHoraApertura = fechaHoraApertura;
            this.fechaHoraCierre = fechaHoraCierre;
            this.fechaHoraCierreRegla = fechaHoraCierreRegla;
            this.fuenteVentana = fuenteVentana;
            this.idReglaVentana = idReglaVentana;
            this.periodoExcepcionVentana = periodoExcepcionVentana;
            this.motivoExcepcion = motivoExcepcion;
        }

        public LocalDate getPeriodoValoracion() {
            return periodoValoracion;
        }

        public LocalDateTime getFechaHoraApertura() {
            return fechaHoraApertura;
        }

        /** Cierre efectivo (puede estar extendido por excepción, con cap configurado). */
        public LocalDateTime getFechaHoraCierre() {
            return fechaHoraCierre;
        }

        /** Cierre según la regla general (sin excepción). */
        public LocalDateTime getFechaHoraCierreRegla() {
            return fechaHoraCierreRegla;
        }

        public OffsetDateTime getFechaHoraAperturaOffset() {
            return fechaHoraApertura.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }

        public OffsetDateTime getFechaHoraCierreOffset() {
            return fechaHoraCierre.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }

        public OffsetDateTime getFechaHoraCierreReglaOffset() {
            return fechaHoraCierreRegla.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }

        public String getFuenteVentana() {
            return fuenteVentana;
        }

        public boolean esExcepcion() {
            return "EXCEPCION".equals(fuenteVentana);
        }

        public String getTipoVentanaRespuesta() {
            return "EXCEPCION".equals(fuenteVentana) ? "EXCEPCION" : "REGLA";
        }

        public Long getIdReglaVentana() {
            return idReglaVentana;
        }

        public LocalDate getPeriodoExcepcionVentana() {
            return periodoExcepcionVentana;
        }

        public String getMotivoExcepcion() {
            return motivoExcepcion;
        }
    }
}
