package com.bancolombia.sipro.validations.infrastructure.entrypoint;

import com.bancolombia.sipro.validations.application.dto.AdminDashboardResponse;
import com.bancolombia.sipro.validations.application.dto.AdminDiagnosticResponse;
import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionRequest;
import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionResponse;
import com.bancolombia.sipro.validations.application.dto.AdminLogStreamResponse;
import com.bancolombia.sipro.validations.application.dto.AdminSqlExecuteRequest;
import com.bancolombia.sipro.validations.application.dto.AdminSqlExecuteResponse;
import com.bancolombia.sipro.validations.application.dto.ConsolidacionManualStatusResponse;
import com.bancolombia.sipro.validations.domain.service.AdminAccessService;
import com.bancolombia.sipro.validations.domain.service.AdminConsolidacionService;
import com.bancolombia.sipro.validations.domain.service.AdminDashboardService;
import com.bancolombia.sipro.validations.domain.service.AdminDiagnosticService;
import com.bancolombia.sipro.validations.domain.service.AdminLogBufferService;
import com.bancolombia.sipro.validations.domain.service.AdminSqlService;
import com.bancolombia.sipro.validations.domain.service.ConsolidacionManualAsyncService;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.List;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Endpoints del panel de administrador SIPRO.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final AdminAccessService adminAccessService;
    private final AdminConsolidacionService adminConsolidacionService;
    private final AdminDashboardService adminDashboardService;
    private final ConsolidacionManualAsyncService consolidacionManualAsyncService;
    private final AdminSqlService adminSqlService;
    private final AdminLogBufferService adminLogBufferService;
    private final AdminDiagnosticService diagnosticService;

    public AdminController(AdminAccessService adminAccessService,
                           AdminConsolidacionService adminConsolidacionService,
                           AdminDashboardService adminDashboardService,
                           ConsolidacionManualAsyncService consolidacionManualAsyncService,
                           AdminSqlService adminSqlService,
                           AdminLogBufferService adminLogBufferService,
                           AdminDiagnosticService diagnosticService) {
        this.adminAccessService = adminAccessService;
        this.adminConsolidacionService = adminConsolidacionService;
        this.adminDashboardService = adminDashboardService;
        this.consolidacionManualAsyncService = consolidacionManualAsyncService;
        this.adminSqlService = adminSqlService;
        this.adminLogBufferService = adminLogBufferService;
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/diagnostico")
    public ResponseEntity<AdminDiagnosticResponse> capturarDiagnostico(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestParam(value = "periodo", required = false) String periodo) {
        adminAccessService.requireAdmin(principal);
        return ResponseEntity.ok(diagnosticService.capturar(parsePeriodo(periodo, false)));
    }

    @GetMapping(value = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<AdminDashboardResponse> obtenerDashboard(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestParam(value = "periodo", required = false) String periodo) {
        adminAccessService.requireAdmin(principal);
        LocalDate periodoValoracion = parsePeriodo(periodo, false);
        return ResponseEntity.ok(adminDashboardService.obtenerDashboard(periodoValoracion));
    }

    @PostMapping(value = "/consolidacion/manual", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<ConsolidacionManualStatusResponse> consolidarManual(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestParam("periodo") String periodo,
            @RequestParam("observacion") String observacion) {
        // Ejecutar consolidación manual es la única acción de /admin restringida a Admin_Permisos
        // (id_rol=6). Soporte Técnico (id_rol=3) ve el resto del panel pero no puede ejecutar esto.
        adminAccessService.requireAdminPermisos(principal);
        LocalDate periodoValoracion = parsePeriodo(periodo, true);

        if (observacion == null || observacion.isBlank()) {
            return ResponseEntity.badRequest().body(crearRespuestaInvalida(periodoValoracion.toString(),
                    "Debes registrar el motivo de la consolidación manual."));
        }

        ConsolidacionManualStatusResponse response = consolidacionManualAsyncService
                .iniciar(periodoValoracion, principal.idUsuario(), observacion.trim());

        if ("NO_EJECUTADA".equalsIgnoreCase(response.getEstado())) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(value = "/consolidacion/{idConsolidacion}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<AdminDeleteConsolidacionResponse> eliminarConsolidacion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable("idConsolidacion") Long idConsolidacion,
            @RequestBody(required = false) AdminDeleteConsolidacionRequest request) {
        adminAccessService.requireAdmin(principal);

        AdminDeleteConsolidacionResponse response = adminConsolidacionService
                .eliminarConsolidacion(idConsolidacion, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/sql/execute", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<AdminSqlExecuteResponse> ejecutarSql(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestBody AdminSqlExecuteRequest request) {
        adminAccessService.requireAdmin(principal);

        long inicio = System.nanoTime();
        try {
            String operation = request != null && request.tipoOperacion() != null
                    ? request.tipoOperacion()
                    : "DESCONOCIDA";

            String sqlTexto = request != null && request.sql() != null ? request.sql().trim() : "";
            boolean esCreateIndex = sqlTexto.toUpperCase(Locale.ROOT).startsWith("CREATE INDEX");

            if (!esCreateIndex && "SELECT".equalsIgnoreCase(operation)) {
                return ResponseEntity.ok(adminSqlService.ejecutarLectura(request, principal));
            }

            return ResponseEntity.ok(adminSqlService.ejecutarEscritura(request, principal));
        } catch (DataAccessException ex) {
            Throwable root = ex.getMostSpecificCause();
            String state = null;
            Throwable cause = ex;
            for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
                if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                    state = sqlException.getSQLState();
                    break;
                }
            }
            String tipo = root.getClass().getSimpleName();
            String mensaje = describirErrorSql(state);
            logger.warn("ADMIN_SQL_ERROR usuario={} tipo={} SQLSTATE={}", principal.idUsuario(), tipo, state);
            String operation = request != null ? request.tipoOperacion() : "DESCONOCIDA";
            return ResponseEntity.badRequest().body(new AdminSqlExecuteResponse(false, operation, mensaje,
                    null, List.of(), List.of(), null, false,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio), state, tipo));
        } catch (IllegalArgumentException ex) {
            String operation = request != null && request.tipoOperacion() != null
                    ? request.tipoOperacion()
                    : "DESCONOCIDA";
            return ResponseEntity.badRequest().body(AdminSqlExecuteResponse.failure(operation, ex.getMessage()));
        }
    }

    private String describirErrorSql(String state) {
        if (state == null) {
            return "Fallo de acceso a BD. Revise el tipo de error y los logs de esta instancia.";
        }
        if (state.startsWith("08")) {
            return "Fallo de conexion con PostgreSQL. Revise SQLSTATE y la disponibilidad de la BD.";
        }
        return switch (state) {
            case "42501" -> "El usuario tecnico de BD no tiene permisos para esta operacion.";
            case "42P01" -> "La tabla o vista no existe en la BD/esquema consultados.";
            case "42703" -> "La consulta referencia una columna que no existe.";
            case "42601" -> "Error de sintaxis SQL. Revise la sentencia.";
            case "57014" -> "La BD cancelo la consulta (por tiempo limite o cancelacion).";
            case "0A000" -> "La operacion o un tipo del resultado no esta soportado por el panel.";
            default -> "La BD rechazo o no pudo completar la operacion. Revise SQLSTATE y el tipo de error.";
        };
    }

    @GetMapping(value = "/logs", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<AdminLogStreamResponse> obtenerLogs(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestParam(value = "afterId", required = false) Long afterId,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "scope", required = false) String scope) {
        adminAccessService.requireAdmin(principal);
        return ResponseEntity.ok(adminLogBufferService.obtenerLogs(afterId, level, limit, scope));
    }

    private LocalDate parsePeriodo(String periodo, boolean required) {
        if ((periodo == null || periodo.isBlank()) && !required) {
            return null;
        }

        try {
            return LocalDate.parse(periodo);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de periodo inválido. Use yyyy-MM-dd.");
        }
    }

    private ConsolidacionManualStatusResponse crearRespuestaInvalida(String periodo, String mensaje) {
        ConsolidacionManualStatusResponse response = new ConsolidacionManualStatusResponse();
        response.setPeriodo(periodo);
        response.setEstado("NO_EJECUTADA");
        response.setMensaje(mensaje);
        response.setTerminal(true);
        response.setExito(false);
        response.setCantidadArchivosConsolidados(0);
        response.setCantidadRegistrosConsolidados(0);
        response.setMensajeError(mensaje);
        return response;
    }
}