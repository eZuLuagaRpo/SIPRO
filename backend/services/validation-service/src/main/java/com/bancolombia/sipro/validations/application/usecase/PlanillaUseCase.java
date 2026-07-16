package com.bancolombia.sipro.validations.application.usecase;

import com.bancolombia.sipro.validations.application.dto.AprobacionesPendientesResponse;
import com.bancolombia.sipro.validations.application.dto.CargasPendientesResponse;
import com.bancolombia.sipro.validations.application.dto.PlanillaResponse;
import com.bancolombia.sipro.validations.application.dto.ResumenCargasResponse;
import com.bancolombia.sipro.validations.application.dto.SolicitudAprobacionRequest;
import com.bancolombia.sipro.validations.application.dto.TableroControlResponse;
import com.bancolombia.sipro.validations.application.dto.VentanaCargaResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.Segmento;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproUsuarioProductoRol;
import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import com.bancolombia.sipro.validations.domain.service.PlanillaNotificationService;
import com.bancolombia.sipro.validations.service.LoteMemoryStore;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SegmentoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleRechazosPlanillaRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleRechazosPlanilla;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maneja el ciclo principal de planillas: solicitud, aprobación, rechazo y consultas.
 *
 * Aquí vive la diferencia entre una planilla con archivo y una certificación "sin datos",
 * además del versionamiento para que cada producto y fecha conserve una sola versión activa.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Service
public class PlanillaUseCase {

    private static final Logger logger = LoggerFactory.getLogger(PlanillaUseCase.class);

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final SiproDetalleArchivoValidacionRepository validacionRepository;
    private final SiproDetalleRechazosPlanillaRepository rechazoRepository;
    private final SiproUsuarioProductoRolRepository uprRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final com.bancolombia.sipro.validations.domain.service.FileStorageService fileStorageService;
    private final com.bancolombia.sipro.validations.domain.service.ExcelMetadataService excelMetadataService;
    private final com.bancolombia.sipro.validations.domain.service.VentanaCargaService ventanaCargaService;
    private final com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService consolidacionPlanillasService;
    private final PlanillaNotificationService planillaNotificationService;
    private final LoteMemoryStore loteMemoryStore;
    private final ProductoRepository productoRepository;
    private final SegmentoRepository segmentoRepository;
    private final ParametroUnicoService parametroUnicoService;

    private static final String[] MESES_ES = {
        "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public PlanillaUseCase(SiproDetalleCargaPlanillasRepository planillaRepository,
            SiproDetalleArchivoValidacionRepository validacionRepository,
            SiproDetalleRechazosPlanillaRepository rechazoRepository,
            SiproUsuarioProductoRolRepository uprRepository,
            UsuarioLoginRepository usuarioLoginRepository,
            com.bancolombia.sipro.validations.domain.service.FileStorageService fileStorageService,
            com.bancolombia.sipro.validations.domain.service.ExcelMetadataService excelMetadataService,
            com.bancolombia.sipro.validations.domain.service.VentanaCargaService ventanaCargaService,
            com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService consolidacionPlanillasService,
            PlanillaNotificationService planillaNotificationService,
            LoteMemoryStore loteMemoryStore,
            ProductoRepository productoRepository,
            SegmentoRepository segmentoRepository,
            ParametroUnicoService parametroUnicoService) {
        this.planillaRepository = planillaRepository;
        this.validacionRepository = validacionRepository;
        this.rechazoRepository = rechazoRepository;
        this.uprRepository = uprRepository;
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.fileStorageService = fileStorageService;
        this.excelMetadataService = excelMetadataService;
        this.ventanaCargaService = ventanaCargaService;
        this.consolidacionPlanillasService = consolidacionPlanillasService;
        this.planillaNotificationService = planillaNotificationService;
        this.loteMemoryStore = loteMemoryStore;
        this.productoRepository = productoRepository;
        this.segmentoRepository = segmentoRepository;
        this.parametroUnicoService = parametroUnicoService;
    }

    @Transactional
    public Map<String, String> solicitarAprobacion(SolicitudAprobacionRequest request,
            org.springframework.web.multipart.MultipartFile archivo,
            org.springframework.web.multipart.MultipartFile archivoControl,
            String ipCliente) throws java.io.IOException {
        logger.info("Procesando solicitud de aprobación para usuario: {}", request.getUsuario());

        VentanaCargaResponse ventanaCarga = ventanaCargaService.validarVentana(request.getFechaCorte());
        if (!ventanaCarga.isDentroDeVentana()) {
            throw new IllegalStateException(
                "La carga solo se permite dentro de la ventana configurada. " + ventanaCarga.getMensaje());
        }

        // 1. Buscar datos completos del usuario
        UsuarioLogin usuario = usuarioLoginRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + request.getUsuario()));

        boolean isSinDatos = Boolean.TRUE.equals(request.getSinDatos());
        // Si el navegador ya validó un archivo antes, este contexto permite reutilizarlo
        // sin obligar al usuario a volverlo a subir al pedir la aprobación.
        CachedUploadContext cachedUploadContext = resolveCachedUploadContext(request, archivo);
        MultipartFile archivoSolicitud = cachedUploadContext.multipartFile();

        // Validación básica: archivo obligatorio si no es sinDatos
        if (!isSinDatos && (archivoSolicitud == null || archivoSolicitud.isEmpty())) {
            throw new IllegalArgumentException(
                "El archivo es obligatorio cuando no se certifica 'Sin Datos' o cuando no existe un archivo temporal validado.");
        }

        Long idProducto = request.getIdProducto();
        if (idProducto == null) {
            // Intento de recuperación si no viene el ID (por compatibilidad o error)
            // En un escenario ideal, esto debería fallar.
            // Para robustez, podríamos buscar el ID basado en el nombre, pero requeriría otro repositorio.
            // Asumiremos que el frontend siempre envía el ID.
            throw new IllegalArgumentException("El ID del producto es obligatorio.");
        }

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new IllegalArgumentException("El producto seleccionado no existe."));

        Long idSegmento = producto.getIdSegmento();
        if (request.getIdSegmento() != null && idSegmento != null && !request.getIdSegmento().equals(idSegmento)) {
            throw new IllegalArgumentException("El segmento seleccionado no coincide con el producto enviado.");
        }

        String nombreProducto = producto.getTitulo();
        String nombreSegmento = idSegmento == null
            ? request.getSegmento()
            : segmentoRepository.findById(idSegmento)
                .map(Segmento::getNombre)
                .orElseGet(request::getSegmento);

        // ========================================================================================
        // FASE A: Preparación (Generar UID y Rutas)
        // ========================================================================================
        String nuevoUid = UUID.randomUUID().toString();
        String directorioFecha = request.getFechaCorte().toString();
        String rutaS3 = "";
        String rutaArchivoControlFinal = null;
        String nombreFinalArchivo = "";
        Long pesoFinal = 0L;

        boolean isFullIfrs = Long.valueOf(2L).equals(idSegmento);

        if (isSinDatos) {
            // En "sin datos" se deja evidencia de la certificación.
            // Para Full IFRS (id_segmento=2) se crean automáticamente un xlsx vacío (solo headers)
            // y un archivo de control txt con solo el número 0.
            String prodSanitizado = nombreProducto != null ? nombreProducto.replaceAll("[^a-zA-Z0-9]", "")
                    : "GENERICO";
            String segSanitizado = nombreSegmento != null ? nombreSegmento.replaceAll("[^a-zA-Z0-9]", "")
                    : "GENERICO";

            if (isFullIfrs) {
                // Usar el patrón oficial del producto para que los archivos lleguen con el nombre correcto
                // a la carpeta compartida y sean reconocibles por los sistemas que los consumen.
                String fechaFormateada = directorioFecha.replace("-", "");
                String nombreBaseXlsx = (producto.getNombreArchivoPermitido() != null
                        && !producto.getNombreArchivoPermitido().isBlank())
                        ? producto.getNombreArchivoPermitido().replace("AAAAMMDD", fechaFormateada) + ".xlsx"
                        : "SD_" + prodSanitizado + "_" + segSanitizado + ".xlsx";
                String nombreBaseCtrl = (producto.getNombreControlPermitido() != null
                        && !producto.getNombreControlPermitido().isBlank())
                        ? producto.getNombreControlPermitido().replace("AAAAMMDD", fechaFormateada) + ".txt"
                        : "CTRL-SD_" + prodSanitizado + "_" + segSanitizado + ".txt";

                byte[] xlsxBytes = crearXlsxSinDatosFullIfrs();
                byte[] ctrlBytes = "0".getBytes(StandardCharsets.UTF_8);

                String customFileNameXlsx = nuevoUid + "__" + nombreBaseXlsx;
                String customFileNameCtrl  = nuevoUid + "__" + nombreBaseCtrl;

                rutaS3 = fileStorageService.storeBytes(
                        xlsxBytes, "pendientes/" + directorioFecha + "/" + customFileNameXlsx,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                rutaArchivoControlFinal = fileStorageService.storeBytes(
                        ctrlBytes, "pendientes/" + directorioFecha + "/" + customFileNameCtrl,
                        "text/plain");

                nombreFinalArchivo = nombreBaseXlsx;
                pesoFinal = (long) xlsxBytes.length;
                logger.info("Full IFRS Sin Datos: xlsx y control creados automáticamente.");
            } else {
                nombreFinalArchivo = "SD_" + prodSanitizado + "_" + segSanitizado + ".txt"; // SD = Sin Datos
                rutaS3 = null; // No hay archivo en S3
                pesoFinal = 0L;
            }
        } else {
            // Lógica CON ARCHIVO (Existente)
            String nombreOriginal = archivoSolicitud.getOriginalFilename();
            if (nombreOriginal == null || nombreOriginal.isEmpty()) {
                nombreOriginal = "archivo_" + System.currentTimeMillis() + ".xlsx";
            }
            
            // Saneamiento y construcción de ruta
            String nombreSaneado = nombreOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");
            String customFileName = nuevoUid + "__" + nombreSaneado;
            
            // Subir xlsx a S3
            rutaS3 = fileStorageService.store(archivoSolicitud, "pendientes/" + directorioFecha, customFileName);
            
            // Si es Full IFRS, guardar también el archivo de control
            if (isFullIfrs && archivoControl != null && !archivoControl.isEmpty()) {
                String nombreCtrlOriginal = archivoControl.getOriginalFilename();
                if (nombreCtrlOriginal == null || nombreCtrlOriginal.isEmpty()) {
                    // Derivar nombre del control a partir del xlsx: CTRL-NOMBRE.txt
                    String baseNombre = nombreOriginal.contains(".")
                            ? nombreOriginal.substring(0, nombreOriginal.lastIndexOf('.'))
                            : nombreOriginal;
                    nombreCtrlOriginal = "CTRL-" + baseNombre + ".txt";
                }
                String nombreCtrlSaneado = nombreCtrlOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");
                String customFileNameCtrl = nuevoUid + "__" + nombreCtrlSaneado;
                rutaArchivoControlFinal = fileStorageService.store(
                        archivoControl, "pendientes/" + directorioFecha, customFileNameCtrl);
                logger.info("Full IFRS: archivo control almacenado en {}", rutaArchivoControlFinal);
            }
            
            // Nombre y peso
            nombreFinalArchivo = request.getNombreArchivo();
            if (nombreFinalArchivo == null || nombreFinalArchivo.trim().isEmpty()) {
                nombreFinalArchivo = nombreOriginal;
            }
            pesoFinal = request.getPesoArchivo();
            if (pesoFinal == null || pesoFinal <= 0) {
                pesoFinal = archivoSolicitud.getSize();
            }
        }
        
        // ========================================================================================
        // FASE B: Transacción Atómica en BD (Inactivación Versionamiento)
        // ========================================================================================
        
        // 1. Bloquear y Consultar el Anterior (SELECT ... FOR UPDATE)
        Optional<SiproDetalleCargaPlanillas> anteriorOpt = planillaRepository
                .findActiveByFechaCorteAndIdProductoForUpdate(request.getFechaCorte(), idProducto);
        
        // 2. Apagar el Anterior (Si existe)
        if (anteriorOpt.isPresent()) {
            SiproDetalleCargaPlanillas anterior = anteriorOpt.get();
            // Inactivar en BD
            planillaRepository.inactivatePreviousActiveByFechaCorteAndIdProducto(
                    request.getFechaCorte(), idProducto);
            
            logger.info("Registro previo inactivado (ID: {}).", anterior.getId());
            
            // El archivo viejo solo se mueve cuando la nueva versión ya quedó confirmada en BD.
            // Así se evita borrar o mover evidencia si la transacción principal termina en rollback.
            final String rutaVieja = anterior.getRutaArchivoAlmacenamiento();
            final String rutaControlVieja = anterior.getRutaArchivoControl();
            if (rutaVieja != null && !rutaVieja.isEmpty() && !rutaVieja.equals("vacio")) {
                final String directorioFechaFinal = directorioFecha;
                
                // Registramos una tarea para ejecutar DESPUÉS de que la transacción de BD sea exitosa
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            fileStorageService.move(rutaVieja, "inactivos/" + directorioFechaFinal);
                            logger.info("Limpieza histórica completada: Archivo {} movido a inactivos.", rutaVieja);
                        } catch (Exception e) {
                            logger.warn("No se pudo mover el archivo antiguo a inactivos (post-commit): {}", e.getMessage());
                        }
                        // Mover también el archivo de control si existía
                        if (rutaControlVieja != null && !rutaControlVieja.isEmpty()) {
                            try {
                                fileStorageService.move(rutaControlVieja, "inactivos/" + directorioFechaFinal);
                                logger.info("Control histórico movido a inactivos: {}", rutaControlVieja);
                            } catch (Exception e) {
                                logger.warn("No se pudo mover el control antiguo a inactivos: {}", e.getMessage());
                            }
                        }
                    }
                });
            }
        }

        // 3. Crear Entidad Nueva
        SiproDetalleCargaPlanillas planilla = new SiproDetalleCargaPlanillas();
        planilla.setActivo(true);
        planilla.setArchivoUid(nuevoUid);
        planilla.setEstadoPlanilla("PENDIENTE"); // Siempre nace pendiente
        planilla.setNoReportaDatos(isSinDatos); // Campo nuevo (Boolean)
        
        // FK: id_usuario_carga → usuario_persona.id_usuario
        planilla.setIdUsuarioCarga(usuario.getIdUsuario());

        // Datos Usuario
        planilla.setNombreUsuarioCarga(
                usuario.getPersona() != null
                        ? usuario.getPersona().getNombres() + " " + usuario.getPersona().getApellidos()
                        : usuario.getUsuario());
        planilla.setCorreoUsuarioCarga(
                usuario.getPersona() != null ? usuario.getPersona().getCorreo() : "N/A");

        // Datos líder (id_usuario_lider → usuario_persona vía relación JPA)
        String nombreLider = "No asignado";
        String correoLider = "N/A";
        if (usuario.getArea() != null) {
            planilla.setNombreArea(usuario.getArea().getAreaNombre());
            if (usuario.getArea().getIdJefe() != null) {
                planilla.setIdLider(usuario.getArea().getIdJefe());
            }
            // Navegar relación JPA: UsuarioArea.jefe → UsuarioPersona
            String jefeNombre = usuario.getArea().getJefeNombreCompleto();
            if (jefeNombre != null && !jefeNombre.isEmpty()) {
                nombreLider = jefeNombre;
            }
            String jefeCorreo = usuario.getArea().getJefeCorreo();
            if (jefeCorreo != null && !jefeCorreo.isEmpty()) {
                correoLider = jefeCorreo;
            }
        } else {
            planilla.setNombreArea("No asignada");
        }
        planilla.setNombreLider(nombreLider);
        planilla.setCorreoLider(correoLider);

        // Datos Archivo
        planilla.setIdProducto(idProducto); // Seteamos el ID para futuras búsquedas
        planilla.setProducto(nombreProducto);
        planilla.setSegmento(nombreSegmento);
        planilla.setDescripcionLarga(request.getDescripcion());
        planilla.setFechaCorteInformacion(request.getFechaCorte());
        planilla.setNombreArchivoFuente(nombreFinalArchivo);
        planilla.setPesoArchivoFuente(pesoFinal);
        planilla.setRutaArchivoAlmacenamiento(rutaS3);
        planilla.setRutaArchivoControl(rutaArchivoControlFinal);
        planilla.setIp(ipCliente);

        // year/month/day deben reflejar el PERIODO REPORTADO (fechaCorte), no la fecha
        // de creación del registro. La fecha de creación ya la captura fecha_creacion
        // automáticamente con CURRENT_TIMESTAMP en base de datos.
        LocalDate fechaCorteInformacion = request.getFechaCorte();
        planilla.setYear(fechaCorteInformacion.getYear());
        planilla.setMonth(fechaCorteInformacion.getMonthValue());
        planilla.setDay(fechaCorteInformacion.getDayOfMonth());

        // 4. Insertar Nuevo
        planillaRepository.saveAndFlush(planilla);
        logger.info("Nueva versión guardada con éxito (ID: {}). Sin Datos: {}", planilla.getId(), isSinDatos);

        // 5. Metadatos (Solo si NO es sinDatos y hay archivo)
        if (!isSinDatos && archivoSolicitud != null) {
            excelMetadataService.processAndSaveMetadata(
                    archivoSolicitud,
                    planilla.getId(),
                    usuario.getUsuario(),
                    usuario.getIdUsuario(),
                    planilla.getProducto(),
                    idSegmento,
                    planilla.getFechaCorteInformacion(),
                    archivoControl);
        }

        if (cachedUploadContext.loteIdToRelease() != null) {
            String loteIdToRelease = cachedUploadContext.loteIdToRelease();
            runAfterCommit(() -> loteMemoryStore.releaseValidatedUpload(loteIdToRelease));
        }

        // Las notificaciones salen después del commit para no mandar correos de una solicitud
        // que finalmente no quedó persistida.
        runAfterCommit(() -> planillaNotificationService.notificarSolicitud(planilla));

        // Retorno
        Map<String, String> liderData = new java.util.HashMap<>();
        liderData.put("nombreLider", nombreLider);
        liderData.put("correoLider", correoLider);
        return liderData;
    }

    // =================== MÉTODOS DE APROBACIÓN ===================

    public List<PlanillaResponse> listarTodas() {
        // Solo traer registros activos (versión vigente)
        return planillaRepository.findAllByActivoTrueOrderByFechaCreacionDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PlanillaResponse> listarPendientes(String correoLider) {
        // Solo traer registros activos (versión vigente)
        if (correoLider == null || correoLider.isEmpty()) {
            return planillaRepository.findByEstadoPlanillaAndActivoTrue("PENDIENTE")
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        return planillaRepository.findByCorreoLiderAndEstadoPlanillaAndActivoTrue(correoLider, "PENDIENTE")
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista todas las planillas activas asignadas a un líder por id_lider.
     * Preferido sobre listarTodas() + filtro por correo.
     */
    public List<PlanillaResponse> listarPorLider(Long idLider) {
        return planillaRepository.findByIdLiderAndActivoTrueOrderByFechaCreacionDesc(idLider)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista las planillas visibles para un aprobador usando el líder asignado en la planilla.
     * La visibilidad se decide por id_lider, no por asignaciones en sipro_usuario_producto_rol.
     * Esto evita dejar sin bandeja a líderes válidos cuando el flujo organizacional sí los
     * tiene asignados en la planilla pero aún no existen filas de producto-rol para aprobación.
     */
    public List<PlanillaResponse> listarParaAprobador(Long idUsuario) {
        return planillaRepository.findByIdLiderAndActivoTrueOrderByFechaCreacionDesc(idUsuario)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PlanillaResponse obtenerDetalle(Long id) {
        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));
        return mapToResponse(planilla);
    }

    @Transactional
    public void aprobar(Long id, String usuarioAprobador, Long idUsuarioAprobador) throws java.io.IOException {
        logger.info("Aprobando planilla con ID: {}", id);

        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));

        validarLiderAsignado(planilla, idUsuarioAprobador, "aprobar");

        VentanaCargaResponse ventanaCarga = ventanaCargaService.validarVentana(planilla.getFechaCorteInformacion());
        if (!ventanaCarga.isDentroDeVentana()) {
            throw new IllegalStateException(
                    "La aprobación solo se permite dentro de la misma ventana de carga. "
                            + ventanaCarga.getMensaje());
        }

        // Validar que el registro esté activo (no haya sido reemplazado por una versión
        // más reciente)
        if (planilla.getActivo() == null || !planilla.getActivo()) {
            throw new IllegalStateException(
                    "No se puede aprobar: el registro está inactivo (fue reemplazado por una versión más reciente).");
        }

        if (!"PENDIENTE".equals(planilla.getEstadoPlanilla())) {
            throw new IllegalStateException("Solo se pueden aprobar planillas en estado PENDIENTE. Estado actual: "
                    + planilla.getEstadoPlanilla());
        }

        // Mover archivo xlsx de pendientes a aprobados
        String rutaActual = planilla.getRutaArchivoAlmacenamiento();
        String nuevaRutaXlsx = rutaActual;
        if (rutaActual != null && !rutaActual.isEmpty()) {
            // Mantener la estructura de fecha: aprobados/YYYY-MM-DD/
            String fechaCorteStr = planilla.getFechaCorteInformacion().toString();
            String subDirectorioDestino = "aprobados/" + fechaCorteStr;

            nuevaRutaXlsx = fileStorageService.move(rutaActual, subDirectorioDestino);
            planilla.setRutaArchivoAlmacenamiento(nuevaRutaXlsx);
            logger.info("Archivo xlsx movido de {} a {}", rutaActual, nuevaRutaXlsx);
        }

        // Mover archivo de control (Full IFRS) de pendientes a aprobados
        String rutaControlActual = planilla.getRutaArchivoControl();
        String nuevaRutaControl = rutaControlActual;
        if (rutaControlActual != null && !rutaControlActual.isEmpty()) {
            String fechaCorteStr = planilla.getFechaCorteInformacion().toString();
            try {
                nuevaRutaControl = fileStorageService.move(rutaControlActual, "aprobados/" + fechaCorteStr);
                planilla.setRutaArchivoControl(nuevaRutaControl);
                logger.info("Archivo control movido de {} a {}", rutaControlActual, nuevaRutaControl);
            } catch (Exception e) {
                logger.warn("No se pudo mover el archivo control a aprobados: {}", e.getMessage());
            }
        }

        // Cambiar estado
        planilla.setEstadoPlanilla("APROBADO");
        planilla.setUsuarioAprobador(usuarioAprobador);
        planilla.setIdUsuarioAprobador(idUsuarioAprobador);
        planilla.setFechaAprobacion(OffsetDateTime.now());
        planillaRepository.save(planilla);

        runAfterCommit(() -> planillaNotificationService.notificarAprobacion(planilla));

        // Determinar si es Full IFRS para copiar a carpeta compartida
        final String rutaXlsxAprobado = nuevaRutaXlsx;
        final String rutaCtrlAprobado = nuevaRutaControl;
        final String nombreArchivoXlsx = planilla.getNombreArchivoFuente();
        boolean isFullIfrsAprobacion = planilla.getSegmento() != null
                && (planilla.getSegmento().toLowerCase().contains("full")
                    || planilla.getSegmento().toLowerCase().contains("ifrs"));

        if (isFullIfrsAprobacion && rutaXlsxAprobado != null && !rutaXlsxAprobado.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    copiarACarpertaCompartidaFullIfrs(rutaXlsxAprobado, rutaCtrlAprobado, nombreArchivoXlsx);
                }
            });
        }

        // ═══════════════════════════════════════════════════════════════
        // CONSOLIDACIÓN POR PERÍODO: evaluar después del commit exitoso
        // ═══════════════════════════════════════════════════════════════
        final LocalDate fechaCorte = planilla.getFechaCorteInformacion();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                consolidacionPlanillasService.evaluarPeriodoPostAprobacion(fechaCorte);
            }
        });

        logger.info("Planilla {} aprobada exitosamente.", id);
    }

    @Transactional
    public void rechazar(Long id, String motivo, String usuarioRechazo, Long idUsuarioRechazo) {
        logger.info("Rechazando planilla con ID: {} por usuario: {} (idUsuario: {})", id, usuarioRechazo, idUsuarioRechazo);

        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));

        validarLiderAsignado(planilla, idUsuarioRechazo, "rechazar");

        // Validar que el registro esté activo (no haya sido reemplazado por una versión
        // más reciente)
        if (planilla.getActivo() == null || !planilla.getActivo()) {
            throw new IllegalStateException(
                    "No se puede rechazar: el registro está inactivo (fue reemplazado por una versión más reciente).");
        }

        if (!"PENDIENTE".equals(planilla.getEstadoPlanilla())) {
            throw new IllegalStateException("Solo se pueden rechazar planillas en estado PENDIENTE. Estado actual: "
                    + planilla.getEstadoPlanilla());
        }

        // 1. Insertar registro de auditoría (ANTES de cambiar estado para garantizar
        // atomicidad)
        SiproDetalleRechazosPlanilla rechazo = new SiproDetalleRechazosPlanilla();
        rechazo.setIdCargaPlanilla(id);
        rechazo.setMotivoRechazo(motivo);
        rechazo.setEtapaRechazo("Aprobación Líder");
        rechazo.setUsuarioRechazo(usuarioRechazo);

        // FK: id_usuario_rechazo → usuario_persona.id_usuario (usa el ID directo)
        if (idUsuarioRechazo != null) {
            rechazo.setIdUsuarioRechazo(idUsuarioRechazo);
        } else if (usuarioRechazo != null && !usuarioRechazo.isEmpty()) {
            // Fallback: intentar resolver por nombre de usuario
            usuarioLoginRepository.findByUsuario(usuarioRechazo)
                    .ifPresent(u -> rechazo.setIdUsuarioRechazo(u.getIdUsuario()));
        }

        rechazoRepository.save(rechazo);
        logger.info("Registro de rechazo guardado para planilla ID: {}", id);

        // 2. Mover xlsx a rechazados
        String rutaActual = planilla.getRutaArchivoAlmacenamiento();
        if (rutaActual != null && !rutaActual.isEmpty()) {
            try {
                // Mantener la estructura de fecha: rechazados/YYYY-MM-DD/
                String fechaCorteStr = planilla.getFechaCorteInformacion().toString();
                String subDirectorioDestino = "rechazados/" + fechaCorteStr;

                String nuevaRuta = fileStorageService.move(rutaActual, subDirectorioDestino);
                planilla.setRutaArchivoAlmacenamiento(nuevaRuta);
                logger.info("Archivo rechazado movido de {} a {}", rutaActual, nuevaRuta);
            } catch (java.io.IOException e) {
                logger.error("Error moviendo archivo rechazado: {}", e.getMessage());
            }
        }

        // Mover también el archivo de control (Full IFRS) a rechazados
        String rutaControlActual = planilla.getRutaArchivoControl();
        if (rutaControlActual != null && !rutaControlActual.isEmpty()) {
            try {
                String fechaCorteStr = planilla.getFechaCorteInformacion().toString();
                String nuevaRutaCtrl = fileStorageService.move(rutaControlActual, "rechazados/" + fechaCorteStr);
                planilla.setRutaArchivoControl(nuevaRutaCtrl);
                logger.info("Control rechazado movido a rechazados: {}", nuevaRutaCtrl);
            } catch (Exception e) {
                logger.warn("No se pudo mover el archivo control a rechazados: {}", e.getMessage());
            }
        }

        // 3. Cambiar estado
        planilla.setEstadoPlanilla("RECHAZADO");
        planillaRepository.save(planilla);

        runAfterCommit(() -> planillaNotificationService.notificarRechazo(planilla, motivo));

        logger.info("Planilla {} rechazada exitosamente.", id);
    }

    public void probarNotificacion(Long id, String tipo, String motivoRechazo) {
        logger.info("Probando notificacion {} para planilla {}", tipo, id);

        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));

        String tipoNormalizado = tipo == null ? "" : tipo.trim().toLowerCase();
        switch (tipoNormalizado) {
            case "solicitud" -> planillaNotificationService.notificarSolicitud(planilla);
            case "aprobacion" -> planillaNotificationService.notificarAprobacion(planilla);
            case "rechazo" -> planillaNotificationService.notificarRechazo(planilla, motivoRechazo);
            default -> throw new IllegalArgumentException("Tipo de notificacion no soportado: " + tipo);
        }
    }

    public java.nio.file.Path obtenerRutaArchivo(Long id) {
        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));

        String rutaRelativa = planilla.getRutaArchivoAlmacenamiento();
        if (rutaRelativa == null || rutaRelativa.isEmpty()) {
            throw new IllegalStateException("La planilla no tiene archivo asociado.");
        }

        return fileStorageService.getAbsolutePath(rutaRelativa);
    }

    public byte[] obtenerArchivoBytes(Long id) throws java.io.IOException {
        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));

        String rutaRelativa = planilla.getRutaArchivoAlmacenamiento();
        if (rutaRelativa == null || rutaRelativa.isEmpty()) {
            throw new IllegalStateException("La planilla no tiene archivo asociado.");
        }

        return fileStorageService.getFileAsBytes(rutaRelativa);
    }

    public String obtenerNombreArchivo(Long id) {
        SiproDetalleCargaPlanillas planilla = planillaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada con ID: " + id));
        return planilla.getNombreArchivoFuente();
    }

    // =================== RESUMEN DE CARGAS ===================

    /**
     * Obtiene el resumen de cargas (conteo por estado + última carga) para un usuario cargador.
     * @param correoUsuario Correo del usuario cargador
     * @return ResumenCargasResponse con los conteos y fecha de última carga
     */
    @Transactional(readOnly = true)
    public ResumenCargasResponse obtenerResumenCargas(String correoUsuario) {
        logger.info("Consultando resumen de cargas para: {}", correoUsuario);

        long pendientes = planillaRepository.countByCorreoUsuarioCargaAndEstadoPlanillaAndActivoTrue(correoUsuario, "PENDIENTE");
        long aprobados = planillaRepository.countByCorreoUsuarioCargaAndEstadoPlanillaAndActivoTrue(correoUsuario, "APROBADO");
        long rechazados = planillaRepository.countByCorreoUsuarioCargaAndEstadoPlanillaAndActivoTrue(correoUsuario, "RECHAZADO");
        LocalDateTime ultimaCarga = planillaRepository.findUltimaCargaByCorreoUsuario(correoUsuario).orElse(null);

        return new ResumenCargasResponse(pendientes, aprobados, rechazados, ultimaCarga);
    }

    /**
     * Obtiene el resumen de aprobaciones para un usuario aprobador.
     * Cuenta planillas por estado usando el líder asignado en la planilla.
     * @param idUsuario ID del usuario aprobador
     * @return ResumenCargasResponse con conteos desde perspectiva de aprobador
     */
    @Transactional(readOnly = true)
    public ResumenCargasResponse obtenerResumenAprobador(Long idUsuario) {
        logger.info("Consultando resumen de aprobaciones para usuario ID: {}", idUsuario);

        long pendientes = planillaRepository.countByIdLiderAndEstadoPlanillaAndActivoTrue(idUsuario, "PENDIENTE");
        long aprobados = planillaRepository.countByIdLiderAndEstadoPlanillaAndActivoTrue(idUsuario, "APROBADO");
        long rechazados = planillaRepository.countByIdLiderAndEstadoPlanillaAndActivoTrue(idUsuario, "RECHAZADO");
        LocalDateTime ultimaAprobacion = planillaRepository.findUltimaAprobacionByLider(idUsuario)
            .map(OffsetDateTime::toLocalDateTime)
            .orElse(null);

        logger.info("Resumen aprobador ID {}: pendientes={}, aprobados={}, rechazados={}", 
                     idUsuario, pendientes, aprobados, rechazados);
        return new ResumenCargasResponse(pendientes, aprobados, rechazados, ultimaAprobacion);
    }

    private void validarLiderAsignado(SiproDetalleCargaPlanillas planilla, Long idUsuarioAprobador, String accion) {
        if (idUsuarioAprobador == null) {
            return;
        }

        if (planilla.getIdLider() == null) {
            throw new IllegalStateException(
                    "No se puede " + accion + " la planilla porque no tiene líder asignado.");
        }

        if (!planilla.getIdLider().equals(idUsuarioAprobador)) {
            throw new IllegalStateException(
                    "Solo el líder asignado a la planilla puede " + accion + "la.");
        }
    }

    private void runAfterCommit(Runnable action) {
        // Cuando hay transacción activa, los efectos externos se patean al final para no dejar
        // correo, consolidación o limpieza ejecutados sobre cambios que luego se revierten.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }

    /**
     * Crea un xlsx mínimo con los 23 headers del segmento Full IFRS y sin filas de datos.
     * Se usa cuando el usuario certifica "Sin Datos" para el segmento id_segmento=2.
     */
    private byte[] crearXlsxSinDatosFullIfrs() throws IOException {
        String[] headers = {
            "NIT", "OFICINA", "DOCUMENTO", "MONEDA", "MODALIDAD",
            "ANOINIOBL", "MESINIOBL", "DIAINIOBL", "ANOVCTO", "MESVCTO",
            "DIAVCTO", "ANOVCTOFIN", "MESVCTOFIN", "DIAVCTOFIN", "CTAPUC",
            "VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES", "SDOVENCIDO",
            "INTCTASORD", "USUARIO", "PRODUCTO"
        };
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("DATOS");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            wb.write(baos);
            return baos.toByteArray();
        }
    }
    /**
     * Copia los archivos xlsx y de control (txt) aprobados de Full IFRS a la carpeta de red compartida.
     * Si la carpeta no está disponible (ambiente dev/local), registra una advertencia sin fallar.
     *
     * @param rutaXlsx      ruta relativa en storage del xlsx aprobado
     * @param rutaControl   ruta relativa en storage del archivo control aprobado (puede ser null)
     * @param nombreArchivo nombre original del xlsx (sin UUID prefix) para la carpeta compartida
     */
    private void copiarACarpertaCompartidaFullIfrs(String rutaXlsx, String rutaControl, String nombreArchivo) {
        String rutaRaiz = parametroUnicoService.getString("IFRS_PLANILLAS_RUTA_SALIDA", "");
        if (rutaRaiz.isBlank()) {
            logger.info("[Full IFRS] Parámetro IFRS_PLANILLAS_RUTA_SALIDA no configurado. Se omite la copia a carpeta compartida.");
            return;
        }
        try {
            Path raizDir = Path.of(rutaRaiz.trim());
            if (!Files.isDirectory(raizDir)) {
                logger.warn("[Full IFRS] Carpeta compartida no disponible: {}. Se omite la copia.", rutaRaiz);
                return;
            }
    
            // copiar xlsx a la raiz
            if (rutaXlsx != null && !rutaXlsx.isEmpty()) {
                byte[] xlsxBytes = fileStorageService.getFileAsBytes(rutaXlsx);
                String nombreDest = (nombreArchivo != null && !nombreArchivo.isBlank())
                        ? nombreArchivo
                        : extraerNombreLimpio(rutaXlsx);
                Files.write(raizDir.resolve(nombreDest), xlsxBytes,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                logger.info("[Full IFRS] xlsx copiado a: {}/{}", rutaRaiz, nombreDest);
            }

            // copiar control a la raiz
            if (rutaControl != null && !rutaControl.isEmpty()) {
                byte[] ctrlBytes = fileStorageService.getFileAsBytes(rutaControl);
                String nombreCtrlDest = extraerNombreLimpio(rutaControl);
                Files.write(raizDir.resolve(nombreCtrlDest), ctrlBytes,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                logger.info("[Full IFRS] control copiado a: {}/{}", rutaRaiz, nombreCtrlDest);
            }
        } catch (Exception e) {
            logger.warn("[Full IFRS] No se pudo copiar a la carpeta compartida '{}': {}",
                    rutaRaiz, e.getMessage());
        }
    }

    private static String extraerNombreLimpio(String ruta) {
        String nombre = ruta.contains("/") ? ruta.substring(ruta.lastIndexOf("/") + 1) : ruta;
        return nombre.contains("__") ? nombre.substring(nombre.indexOf("__") + 2) : nombre;
    }
    
    private CachedUploadContext resolveCachedUploadContext(
            SolicitudAprobacionRequest request,
            MultipartFile archivo) {
        // Si el usuario volvió a subir archivo, se usa ese inmediatamente y no se mira cache.
        if (archivo != null && !archivo.isEmpty()) {
            return new CachedUploadContext(archivo, null);
        }

        String validacionLoteId = request.getValidacionLoteId();
        if (validacionLoteId == null || validacionLoteId.trim().isEmpty()) {
            return new CachedUploadContext(archivo, null);
        }

        // El lote cacheado representa un archivo ya validado en memoria temporal. Se reutiliza
        // para que la aprobación trabaje sobre exactamente el mismo contenido aprobado en UI.
        LoteMemoryStore.CachedValidatedUpload cachedUpload = loteMemoryStore
                .getValidatedUpload(validacionLoteId, request.getUsuario())
                .orElseThrow(() -> new IllegalStateException(
                        "El archivo validado temporalmente ya no está disponible. Vuelva a validar el archivo antes de solicitar aprobación."));

        return new CachedUploadContext(
                new CachedPathMultipartFile(cachedUpload.filePath(), cachedUpload.originalFilename(), cachedUpload.size()),
                validacionLoteId);
    }

    private record CachedUploadContext(MultipartFile multipartFile, String loteIdToRelease) {
    }

    private static final class CachedPathMultipartFile implements MultipartFile {
        private final Path filePath;
        private final String originalFilename;
        private final long size;

        private CachedPathMultipartFile(Path filePath, String originalFilename, long size) {
            this.filePath = filePath;
            this.originalFilename = originalFilename;
            this.size = size;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return size <= 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(filePath);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(filePath);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.copy(filePath, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // =================== CARGAS PENDIENTES (MES ANTERIOR) ===================

    /**
     * Devuelve los productos del mes anterior que el usuario aún no ha cargado.
     * Lógica:
     *   1. Obtener todos los productos asignados al usuario (RBAC con rol de carga).
     *   2. Para cada producto, verificar si existe al menos un registro activo (no rechazado)
     *      cuya fecha_corte_informacion pertenezca al mes anterior.
     *   3. Los productos sin carga son los "pendientes".
     */
    @Transactional(readOnly = true)
    public CargasPendientesResponse obtenerCargasPendientes(Long idUsuario) {
        logger.info("Consultando cargas pendientes del mes anterior para usuario ID: {}", idUsuario);

        // Calcular primer y último día del mes anterior
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        LocalDate primerDia = mesAnterior.atDay(1);
        LocalDate ultimoDia = mesAnterior.atEndOfMonth();
        String nombreMes = MESES_ES[mesAnterior.getMonthValue()];

        // Obtener productos asignados al usuario (todos los roles activos, con producto pre-cargado)
        List<SiproUsuarioProductoRol> asignaciones = uprRepository.findActiveByUsuarioWithProducto(idUsuario);

        // Filtrar solo productos únicos con rol de carga (r.cargarArchivos = 1)
        // Si el rol tiene cargarArchivos, el usuario debe tener archivo cargado para ese producto
        List<CargasPendientesResponse.ProductoPendiente> pendientes = new ArrayList<>();

        // Usar Set para evitar duplicados por producto (un usuario puede tener múltiples roles en el mismo producto)
        java.util.Set<Long> productosEvaluados = new java.util.HashSet<>();

        for (SiproUsuarioProductoRol upr : asignaciones) {
            Long idProducto = upr.getId().getIdProducto();
            // Evitar evaluar el mismo producto dos veces
            if (!productosEvaluados.add(idProducto)) {
                continue;
            }

            // Solo evaluar si el rol permite carga de archivos
            if (upr.getRol() != null && upr.getRol().getCargarArchivos() != null
                    && upr.getRol().getCargarArchivos() == 1) {

                // Verificar si existe carga activa (no rechazada) para este producto en el mes anterior
                boolean tieneCarga = planillaRepository.existsCargaActivaForProductoInMonth(
                        idUsuario, idProducto, primerDia, ultimoDia);

                if (!tieneCarga) {
                    // Obtener título del producto desde la relación
                    String titulo = upr.getProducto() != null
                            ? upr.getProducto().getTitulo()
                            : "Producto #" + idProducto;
                    pendientes.add(new CargasPendientesResponse.ProductoPendiente(idProducto, titulo));
                }
            }
        }

        logger.info("Usuario ID {} tiene {} productos pendientes del mes {}", idUsuario, pendientes.size(), nombreMes);
        return new CargasPendientesResponse(ultimoDia, nombreMes, pendientes);
    }

    // =================== APROBACIONES PENDIENTES (APROBADOR — ÚLTIMOS 3 MESES) ===================

    /**
     * Devuelve las planillas PENDIENTE de aprobar para un aprobador, agrupadas por mes.
     * Busca únicamente planillas cuya columna id_lider coincide con el usuario aprobador.
     * Abarca los últimos 3 meses (mes actual inclusive hacia atrás).
     */
    @Transactional(readOnly = true)
    public AprobacionesPendientesResponse obtenerAprobacionesPendientes(Long idUsuario) {
        logger.info("Consultando aprobaciones pendientes (últimos 3 meses) para aprobador ID: {}", idUsuario);

        YearMonth mesActual = YearMonth.now();
        // Rango global: primer día de hace 2 meses → último día del mes actual
        YearMonth mesInicio = mesActual.minusMonths(2);
        LocalDate fechaInicioGlobal = mesInicio.atDay(1);
        LocalDate fechaFinGlobal = mesActual.atEndOfMonth();

        // Una sola consulta para traer todas las planillas pendientes del rango por productos
        List<SiproDetalleCargaPlanillas> todasPendientes =
        planillaRepository.findPendientesAprobacionByLider(idUsuario, fechaInicioGlobal, fechaFinGlobal);

        // Agrupar por mes (del más reciente al más antiguo)
        List<AprobacionesPendientesResponse.MesPendiente> meses = new ArrayList<>();

        for (int i = 0; i <= 2; i++) {
            YearMonth ym = mesActual.minusMonths(i);
            LocalDate primerDia = ym.atDay(1);
            LocalDate ultimoDia = ym.atEndOfMonth();
            String nombreMes = MESES_ES[ym.getMonthValue()];

            // Filtrar planillas de este mes
            List<AprobacionesPendientesResponse.ProductoPendienteAprobacion> productosMes = new ArrayList<>();
            for (SiproDetalleCargaPlanillas p : todasPendientes) {
                LocalDate fc = p.getFechaCorteInformacion();
                if (fc != null && !fc.isBefore(primerDia) && !fc.isAfter(ultimoDia)) {
                    String titulo = p.getProducto() != null ? p.getProducto() : "Producto #" + p.getIdProducto();
                    productosMes.add(new AprobacionesPendientesResponse.ProductoPendienteAprobacion(
                            p.getId(), p.getIdProducto(), titulo));
                }
            }

            meses.add(new AprobacionesPendientesResponse.MesPendiente(ultimoDia, nombreMes, ym.getYear(), productosMes));
        }

        long totalPendientes = meses.stream().mapToLong(m -> m.getProductosPendientes().size()).sum();
        logger.info("Aprobador ID {} tiene {} planillas pendientes de aprobar en 3 meses", 
                     idUsuario, totalPendientes);

        return new AprobacionesPendientesResponse(meses);
    }

    // =================== TABLERO DE CONTROL ===================

    private static final String[] MESES_ABREV = {
        "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    /**
     * Construye el tablero de control para un periodo dado.
     * Cruza todos los productos activos contra las planillas del periodo para derivar
     * el estado de carga y aprobación en cada segmento (Colgaap/Modificado y Full IFRS).
     */
    @Transactional(readOnly = true)
    public TableroControlResponse obtenerTableroControl(Integer anio, Integer mes) {
        YearMonth ahora = YearMonth.now();
        int anioFinal = anio != null ? anio : ahora.getYear();
        int mesFinal = mes != null ? mes : ahora.getMonthValue();

        // Todos los productos activos ordenados alfabéticamente
        List<Producto> productos = productoRepository.findAllByOrderByTituloAsc()
                .stream()
                .filter(p -> p.getActivo() == null || p.getActivo() == 1)
                .collect(Collectors.toList());

        // Planillas activas del periodo por segmento (id_segmento=1 Colgaap, id_segmento=2 Full IFRS)
        List<SiproDetalleCargaPlanillas> planillasColgaap =
                planillaRepository.findActivasByAnioMesAndSegmentoId(anioFinal, mesFinal, 1L);
        List<SiproDetalleCargaPlanillas> planillasIfrs =
                planillaRepository.findActivasByAnioMesAndSegmentoId(anioFinal, mesFinal, 2L);

        // Indexar por idProducto para búsqueda O(1); si hay duplicados se queda con el último
        Map<Long, SiproDetalleCargaPlanillas> mapColgaap = planillasColgaap.stream()
                .collect(Collectors.toMap(
                        SiproDetalleCargaPlanillas::getIdProducto,
                        p -> p,
                        (a, b) -> b));
        Map<Long, SiproDetalleCargaPlanillas> mapIfrs = planillasIfrs.stream()
                .collect(Collectors.toMap(
                        SiproDetalleCargaPlanillas::getIdProducto,
                        p -> p,
                        (a, b) -> b));

        // Agrupar por titulo (case-insensitive) preservando el orden alfabético; en BD cada producto tiene una fila por segmento
        Map<String, Map<Long, Long>> productosPorNombre = new java.util.LinkedHashMap<>();
        Map<String, String> displayPorClave = new java.util.LinkedHashMap<>();
        for (Producto p : productos) {
            String clave = p.getTitulo() == null ? "" : p.getTitulo().trim().toLowerCase();
            productosPorNombre
                    .computeIfAbsent(clave, k -> new java.util.HashMap<>())
                    .put(p.getIdSegmento(), p.getIdProducto());
            displayPorClave.putIfAbsent(clave, p.getTitulo() == null ? "" : p.getTitulo().trim());
        }

        // Una fila por nombre único (los 24 productos), con los estados de ambos segmentos
        List<TableroControlResponse.TableroFilaDto> filas = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Long>> entry : productosPorNombre.entrySet()) {
            String clave = entry.getKey();
            String nombre = displayPorClave.get(clave);
            Long idColgaap = entry.getValue().get(1L);
            Long idIfrs    = entry.getValue().get(2L);

            TableroControlResponse.TableroFilaDto fila = new TableroControlResponse.TableroFilaDto();
            fila.setIdProducto(idColgaap != null ? idColgaap : idIfrs);
            fila.setNombreProducto(nombre);

            if (esNoAplicaColgaap(nombre)) {
                fila.setEstadoCargadoColgaap("NO_APLICA");
                fila.setEstadoAprobadoColgaap("NO_APLICA");
            } else {
                SiproDetalleCargaPlanillas pColgaap = idColgaap != null ? mapColgaap.get(idColgaap) : null;
                fila.setEstadoCargadoColgaap(derivarEstadoCargado(pColgaap));
                fila.setEstadoAprobadoColgaap(derivarEstadoAprobado(pColgaap));
            }

            if (esNoAplicaFullIfrs(nombre)) {
                fila.setEstadoCargadoFullIfrs("NO_APLICA");
                fila.setEstadoAprobadoFullIfrs("NO_APLICA");
            } else {
                SiproDetalleCargaPlanillas pIfrs = idIfrs != null ? mapIfrs.get(idIfrs) : null;
                fila.setEstadoCargadoFullIfrs(derivarEstadoCargado(pIfrs));
                fila.setEstadoAprobadoFullIfrs(derivarEstadoAprobado(pIfrs));
            }

            filas.add(fila);
        }

        // Periodos disponibles (distinct anio+mes con planillas activas + mes actual)
        List<Object[]> rawPeriodos = planillaRepository.findDistinctAniosMeses();
        List<TableroControlResponse.PeriodoAnualDto> periodosDisponibles =
                construirPeriodosDisponibles(rawPeriodos, anioFinal, mesFinal);

        String periodoEtiqueta = MESES_ES[mesFinal] + " " + anioFinal;

        return new TableroControlResponse(periodosDisponibles, anioFinal, mesFinal, periodoEtiqueta, filas);
    }

    private boolean esNoAplicaColgaap(String nombreProducto) {
        if (nombreProducto == null) return false;
        String norm = nombreProducto.trim().toLowerCase();
        return norm.equals("recaudos") || norm.equals("seguridad");
    }

    private boolean esNoAplicaFullIfrs(String nombreProducto) {
        if (nombreProducto == null) return false;
        return nombreProducto.trim().toLowerCase().equals("tipz");
    }

    private String derivarEstadoCargado(SiproDetalleCargaPlanillas planilla) {
        if (planilla == null) return "PENDIENTE_CARGA";
        return Boolean.TRUE.equals(planilla.getNoReportaDatos()) ? "SIN_DATOS" : "ARCHIVO_CARGADO";
    }

    private String derivarEstadoAprobado(SiproDetalleCargaPlanillas planilla) {
        if (planilla == null) return "PENDIENTE_APROBACION";
        boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
        return switch (planilla.getEstadoPlanilla()) {
            case "PENDIENTE" -> "PENDIENTE_APROBACION";
            case "APROBADO" -> sinDatos ? "APROBACION_SIN_DATOS" : "ARCHIVO_APROBADO";
            case "RECHAZADO" -> sinDatos ? "RECHAZO_SIN_DATOS" : "ARCHIVO_RECHAZADO";
            default -> null;
        };
    }

    private List<TableroControlResponse.PeriodoAnualDto> construirPeriodosDisponibles(
            List<Object[]> rawPeriodos, int anioActual, int mesActual) {

        // Construir mapa anio → lista de meses (incluye siempre el periodo actual)
        java.util.TreeMap<Integer, java.util.TreeSet<Integer>> mapa = new java.util.TreeMap<>();

        for (Object[] row : rawPeriodos) {
            int a = ((Number) row[0]).intValue();
            int m = ((Number) row[1]).intValue();
            mapa.computeIfAbsent(a, k -> new java.util.TreeSet<>()).add(m);
        }

        // Garantizar que el periodo seleccionado aparezca aunque no tenga planillas todavía
        mapa.computeIfAbsent(anioActual, k -> new java.util.TreeSet<>()).add(mesActual);

        List<TableroControlResponse.PeriodoAnualDto> result = new ArrayList<>();
        for (Map.Entry<Integer, java.util.TreeSet<Integer>> entry : mapa.entrySet()) {
            int anio = entry.getKey();
            List<TableroControlResponse.MesDisponibleDto> meses = new ArrayList<>();
            for (int mes : entry.getValue()) {
                String etiqueta = MESES_ES[mes] + " " + anio;
                String abrev = MESES_ABREV[mes];
                String periodo = String.format("%d-%02d", anio, mes);
                meses.add(new TableroControlResponse.MesDisponibleDto(mes, etiqueta, abrev, periodo));
            }
            result.add(new TableroControlResponse.PeriodoAnualDto(anio, meses));
        }

        return result;
    }

    // =================== MAPPERS ===================

    private PlanillaResponse mapToResponse(SiproDetalleCargaPlanillas planilla) {
        PlanillaResponse response = new PlanillaResponse();
        response.setId(planilla.getId());
        response.setNombreResponsable(planilla.getNombreUsuarioCarga());
        response.setProducto(planilla.getProducto());
        response.setSegmento(planilla.getSegmento());
        response.setFechaCorte(planilla.getFechaCorteInformacion());
        response.setDescripcion(planilla.getDescripcionLarga());
        response.setEstado(planilla.getEstadoPlanilla());
        response.setNombreArchivo(planilla.getNombreArchivoFuente());
        response.setPesoArchivo(planilla.getPesoArchivoFuente());
        response.setFechaCreacion(planilla.getFechaCreacion());
        response.setFechaAprobacion(planilla.getFechaAprobacion() != null
            ? planilla.getFechaAprobacion().toLocalDateTime()
            : null);
        response.setIdLider(planilla.getIdLider());
        response.setCorreoLider(planilla.getCorreoLider());
        response.setActivo(planilla.getActivo());
        response.setSinDatos(Boolean.TRUE.equals(planilla.getNoReportaDatos()));

        // Obtener numeroFilas desde sipro_detalle_archivo_validacion (#8)
        try {
            Optional<SiproDetalleArchivoValidacion> validacion = validacionRepository
                    .findByIdCargaPlanilla(planilla.getId());
            validacion.ifPresent(v -> response.setNumeroFilas(v.getNumeroFilasDatos()));
        } catch (Exception e) {
            // Log error but don't fail the entire response
            logger.error("Error obteniendo detalles de validación para planilla {}: {}", planilla.getId(),
                    e.getMessage());
        }

        return response;
    }
}