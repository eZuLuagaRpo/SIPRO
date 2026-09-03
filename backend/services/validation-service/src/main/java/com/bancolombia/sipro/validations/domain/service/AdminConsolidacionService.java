package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionRequest;
import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Gestiona la eliminación segura de consolidaciones desde el panel admin.
 */
@Service
public class AdminConsolidacionService {

    private static final Logger log = LoggerFactory.getLogger(AdminConsolidacionService.class);

    private static final List<String> ESTADOS_ELIMINABLES = List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS");
    private static final int DELETE_BATCH_SIZE = 10_000;
    private static final String CONFIRMACION_REQUERIDA = "Eliminar";
    private static final String CONSOLIDADOS_PREFIX = "consolidados/";

    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;
    private final SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;
    private final FileStorageService fileStorageService;
    private final ParametroUnicoService parametroUnicoService;
    private final NotificacionConsolidacionService notificacionConsolidacionService;
    private final ArchivosBloqueadosService archivosBloqueadosService;
    private final TransactionTemplate transactionTemplate;

    public AdminConsolidacionService(
            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
            SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository,
            SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository,
            FileStorageService fileStorageService,
            ParametroUnicoService parametroUnicoService,
            NotificacionConsolidacionService notificacionConsolidacionService,
            ArchivosBloqueadosService archivosBloqueadosService,
            PlatformTransactionManager transactionManager) {
        this.consolidacionRepository = consolidacionRepository;
        this.consolidacionArchivoRepository = consolidacionArchivoRepository;
        this.consolidadoRegistroRepository = consolidadoRegistroRepository;
        this.fileStorageService = fileStorageService;
        this.parametroUnicoService = parametroUnicoService;
        this.notificacionConsolidacionService = notificacionConsolidacionService;
        this.archivosBloqueadosService = archivosBloqueadosService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AdminDeleteConsolidacionResponse eliminarConsolidacion(
            Long idConsolidacion,
            AdminDeleteConsolidacionRequest request,
            SiproAuthenticatedUser principal) {
        AdminDeleteConsolidacionRequest safeRequest = request != null
                ? request
                : new AdminDeleteConsolidacionRequest(null, null);
        String motivo = normalizarMotivo(safeRequest.motivo());

        validarSolicitud(idConsolidacion, motivo, safeRequest.confirmacion());

        SiproDetalleConsolidacionesPlanillas cabecera = consolidacionRepository.findById(idConsolidacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la consolidación solicitada."));

        if (!ESTADOS_ELIMINABLES.contains(cabecera.getEstadoConsolidacion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden eliminar consolidaciones COMPLETADO o COMPLETADO_CON_ADVERTENCIAS.");
        }

        String usuario = resolverUsuario(principal);
        log.info("Eliminación de consolidación {} solicitada por usuario {}. Motivo: {}",
                idConsolidacion, usuario, motivo);

        DbDeleteSummary dbSummary = Objects.requireNonNull(transactionTemplate.execute(status ->
                eliminarDatosPersistidos(cabecera)));

        StorageCleanupSummary storageSummary = eliminarArchivosStorage(cabecera.getPeriodoValoracion());
        SharedCleanupSummary sharedSummary = eliminarArchivoCompartido(cabecera.getPeriodoValoracion());
        archivosBloqueadosService.eliminarPeriodo(cabecera.getPeriodoValoracion());

        log.info("Consolidación {} eliminada por usuario {}. Periodo: {}. Registros: {}. Archivos BD: {}. "
                        + "Storage eliminados: {}. Motivo: {}",
                idConsolidacion,
                usuario,
                cabecera.getPeriodoValoracion(),
                dbSummary.registrosEliminados(),
                dbSummary.archivosEliminados(),
                storageSummary.deletedObjects(),
                motivo);

            notificacionConsolidacionService.notificarEliminacion(
                cabecera,
                motivo,
                principal,
                dbSummary.registrosEliminados(),
                dbSummary.archivosEliminados(),
                OffsetDateTime.now());

        return new AdminDeleteConsolidacionResponse(
                true,
                construirMensaje(dbSummary, storageSummary, sharedSummary),
                dbSummary.registrosEliminados(),
                dbSummary.archivosEliminados());
    }

    private void validarSolicitud(Long idConsolidacion, String motivo, String confirmacion) {
        if (idConsolidacion == null || idConsolidacion <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes indicar una consolidación válida para eliminar.");
        }

        if (!CONFIRMACION_REQUERIDA.equals(confirmacion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La confirmación debe ser exactamente 'Eliminar'.");
        }

        if (motivo.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes ingresar un motivo de al menos 10 caracteres.");
        }
    }

    private DbDeleteSummary eliminarDatosPersistidos(SiproDetalleConsolidacionesPlanillas cabecera) {
        long registrosEliminados = 0L;
        int eliminadosLote;
        do {
            eliminadosLote = consolidadoRegistroRepository.deleteBatchByIdConsolidacion(
                    cabecera.getIdConsolidacion(),
                    DELETE_BATCH_SIZE);
            registrosEliminados += eliminadosLote;
        } while (eliminadosLote > 0);

        int archivosEliminados = consolidacionArchivoRepository.deleteByIdConsolidacion(cabecera.getIdConsolidacion());
        consolidacionRepository.delete(cabecera);
        consolidacionRepository.flush();

        return new DbDeleteSummary(registrosEliminados, archivosEliminados);
    }

    private StorageCleanupSummary eliminarArchivosStorage(LocalDate periodoValoracion) {
        if (periodoValoracion == null) {
            return new StorageCleanupSummary(0, 0, null);
        }

        String prefix = CONSOLIDADOS_PREFIX + periodoValoracion + "/";
        List<String> objects = fileStorageService.listObjects(prefix);
        int deleted = 0;

        for (String objectKey : objects) {
            try {
                if (fileStorageService.delete(objectKey)) {
                    deleted++;
                } else {
                    log.warn("No se pudo eliminar archivo generado del storage: {}", objectKey);
                }
            } catch (Exception ex) {
                log.warn("No se pudo eliminar archivo generado del storage {}: {}", objectKey, ex.getMessage());
            }
        }

        eliminarDirectorioLocalSilencioso(prefix);

        String warning = deleted == objects.size()
                ? null
                : "No se pudieron eliminar todos los archivos generados del storage.";

        return new StorageCleanupSummary(objects.size(), deleted, warning);
    }

    private SharedCleanupSummary eliminarArchivoCompartido(LocalDate periodoValoracion) {
        String sharedDir = parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "");
        if (sharedDir == null || sharedDir.isBlank()) {
            return new SharedCleanupSummary(0, null);
        }

        String fileName = parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx");
        Set<Path> candidates = new LinkedHashSet<>();
        candidates.add(Path.of(sharedDir.trim(), fileName));
        // Excel consolidado: mismo nombre fijo siempre (ver ConsolidacionPeriodoExecutor), se
        // sobreescribe en cada consolidación igual que CREFFSOS — no es específico del periodo.
        candidates.add(Path.of(sharedDir.trim(), "CONSOLIDADO.xlsx"));
        if (periodoValoracion != null) {
            candidates.add(Path.of(sharedDir.trim(), periodoValoracion.toString(), fileName));
        }

        int deleted = 0;
        String warning = null;
        for (Path candidate : candidates) {
            try {
                if (Files.deleteIfExists(candidate)) {
                    deleted++;
                }
            } catch (Exception ex) {
                warning = "No se pudieron eliminar todos los archivos de la ruta compartida.";
                log.warn("No se pudo eliminar archivo compartido {}: {}", candidate, ex.getMessage());
            }
        }

        return new SharedCleanupSummary(deleted, warning);
    }

    private void eliminarDirectorioLocalSilencioso(String prefix) {
        try {
            Path absolutePath = fileStorageService.getAbsolutePath(prefix);
            String normalized = absolutePath.toString().toLowerCase(Locale.ROOT);
            if (normalized.contains("s3:")) {
                return;
            }
            Files.deleteIfExists(absolutePath);
        } catch (Exception ex) {
            log.warn("No se pudo eliminar la carpeta local del consolidado {}: {}", prefix, ex.getMessage());
        }
    }

    private String construirMensaje(DbDeleteSummary dbSummary,
                                    StorageCleanupSummary storageSummary,
                                    SharedCleanupSummary sharedSummary) {
        StringBuilder mensaje = new StringBuilder("Consolidación eliminada. Se eliminaron ")
                .append(dbSummary.registrosEliminados())
                .append(dbSummary.registrosEliminados() == 1 ? " registro, " : " registros, ")
                .append(dbSummary.archivosEliminados())
                .append(dbSummary.archivosEliminados() == 1 ? " archivo y los ficheros generados." : " archivos y los ficheros generados.");

        if (storageSummary.warning() != null || sharedSummary.warning() != null) {
            mensaje.append(" La limpieza física terminó con advertencias; revise el log del backend.");
        }

        return mensaje.toString();
    }

    private String normalizarMotivo(String motivo) {
        return motivo == null ? "" : motivo.trim();
    }

    private String resolverUsuario(SiproAuthenticatedUser principal) {
        if (principal == null) {
            return "desconocido";
        }

        if (principal.usuario() != null && !principal.usuario().isBlank()) {
            return principal.usuario();
        }
        if (principal.alias() != null && !principal.alias().isBlank()) {
            return principal.alias();
        }
        if (principal.preferredUsername() != null && !principal.preferredUsername().isBlank()) {
            return principal.preferredUsername();
        }
        if (principal.email() != null && !principal.email().isBlank()) {
            return principal.email();
        }
        return principal.idUsuario() != null ? String.valueOf(principal.idUsuario()) : "desconocido";
    }

    private record DbDeleteSummary(long registrosEliminados, int archivosEliminados) {
    }

    private record StorageCleanupSummary(int totalObjects, int deletedObjects, String warning) {
    }

    private record SharedCleanupSummary(int deletedFiles, String warning) {
    }
}