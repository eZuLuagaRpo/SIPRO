package com.bancolombia.sipro.validations.infrastructure.entrypoint;

import com.bancolombia.sipro.validations.application.dto.AprobacionesPendientesResponse;
import com.bancolombia.sipro.validations.application.dto.CargasPendientesResponse;
import com.bancolombia.sipro.validations.application.dto.PlanillaResponse;
import com.bancolombia.sipro.validations.application.dto.ResumenCargasResponse;
import com.bancolombia.sipro.validations.application.dto.SolicitudAprobacionRequest;
import com.bancolombia.sipro.validations.application.dto.TableroControlResponse;
import com.bancolombia.sipro.validations.application.usecase.PlanillaUseCase;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expone el flujo completo de solicitud, consulta, aprobación y descarga de planillas.
 */
@RestController
@RequestMapping("/api/planillas")
public class PlanillaController {

    private static final Logger logger = LoggerFactory.getLogger(PlanillaController.class);

    private final PlanillaUseCase planillaUseCase;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PlanillaController(PlanillaUseCase planillaUseCase,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.planillaUseCase = planillaUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Recibe la solicitud de aprobación y el archivo opcional de la planilla.
     */
    @PostMapping(value = "/solicitar", consumes = { "multipart/form-data" })
    public ResponseEntity<Map<String, Object>> solicitarAprobacion(
            @RequestPart("datos") String datosJson,
            @RequestPart(value = "archivo", required = false) org.springframework.web.multipart.MultipartFile archivo,
            @RequestPart(value = "archivoControl", required = false) org.springframework.web.multipart.MultipartFile archivoControl,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        try {
            SolicitudAprobacionRequest request = objectMapper.readValue(datosJson, SolicitudAprobacionRequest.class);
            request.setUsuario(resolveAuthenticatedUsuario(request.getUsuario(), authentication, "solicitar aprobación"));

            String ipCliente = httpRequest.getRemoteAddr();
            String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                ipCliente = xForwardedFor.split(",")[0].trim();
            }

            Map<String, String> liderData = planillaUseCase.solicitarAprobacion(request, archivo, archivoControl, ipCliente);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mensaje", "Solicitud enviada correctamente.");
            response.put("nombreLider", liderData.get("nombreLider"));
            response.put("correoLider", liderData.get("correoLider"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace(); // Loguear para depuración
            
            // Buscar causa raíz para devolver mensaje más útil
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            
            String mensajeError = "Error al procesar la solicitud: " + e.getMessage();
            if (rootCause.getMessage() != null && !mensajeError.contains(rootCause.getMessage())) {
                mensajeError += " | Detalle: " + rootCause.getMessage();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("mensaje", mensajeError);
            return ResponseEntity.status(500).body(response);
        }
    }

    // =================== ENDPOINTS DE APROBACIÓN ===================

    /**
     * Devuelve el resumen de cargas del usuario solicitante.
     */
    @GetMapping("/resumen")
    public ResponseEntity<ResumenCargasResponse> resumenCargas(@RequestParam(required = false) String correo,
                                                               Authentication authentication) {
        ResumenCargasResponse resumen = planillaUseCase.obtenerResumenCargas(
                resolveAuthenticatedCorreo(correo, authentication, "consultar resumen de cargas"));
        return ResponseEntity.ok(resumen);
    }

    /**
     * Devuelve el resumen operativo del usuario aprobador.
     */
    @GetMapping("/resumen-aprobador")
    public ResponseEntity<ResumenCargasResponse> resumenAprobador(@RequestParam(required = false) Long idUsuario,
                                                                  Authentication authentication) {
        ResumenCargasResponse resumen = planillaUseCase.obtenerResumenAprobador(
                resolveAuthenticatedUserId(idUsuario, authentication, "consultar resumen de aprobador"));
        return ResponseEntity.ok(resumen);
    }

    /**
     * Lista las cargas pendientes del periodo anterior para un usuario.
     */
    @GetMapping("/cargas-pendientes")
    public ResponseEntity<CargasPendientesResponse> cargasPendientes(@RequestParam(required = false) Long idUsuario,
                                                                     Authentication authentication) {
        CargasPendientesResponse response = planillaUseCase.obtenerCargasPendientes(
                resolveAuthenticatedUserId(idUsuario, authentication, "consultar cargas pendientes"));
        return ResponseEntity.ok(response);
    }

    /**
     * Lista las aprobaciones que siguen pendientes para el usuario indicado.
     */
    @GetMapping("/aprobaciones-pendientes")
    public ResponseEntity<AprobacionesPendientesResponse> aprobacionesPendientes(@RequestParam(required = false) Long idUsuario,
                                                                                 Authentication authentication) {
        AprobacionesPendientesResponse response = planillaUseCase.obtenerAprobacionesPendientes(
                resolveAuthenticatedUserId(idUsuario, authentication, "consultar aprobaciones pendientes"));
        return ResponseEntity.ok(response);
    }

    /**
     * Devuelve el estado actual de planillas por producto y segmento para el periodo indicado.
     * Usado por el tablero de control del perfil administrador.
     */
    @GetMapping("/tablero-control")
    public ResponseEntity<TableroControlResponse> tableroControl(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {
        TableroControlResponse response = planillaUseCase.obtenerTableroControl(anio, mes);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todas las planillas visibles en el flujo general.
     */
    @GetMapping("/todas")
    public ResponseEntity<List<PlanillaResponse>> listarTodas() {
        List<PlanillaResponse> planillas = planillaUseCase.listarTodas();
        return ResponseEntity.ok(planillas);
    }

    /**
     * Lista todas las planillas activas de un líder por id_lider.
     * Preferido sobre /todas + filtro frontend por correo.
     */
    @GetMapping("/por-lider")
    public ResponseEntity<List<PlanillaResponse>> listarPorLider(@RequestParam(required = false) Long idLider,
                                                                 Authentication authentication) {
        List<PlanillaResponse> planillas = planillaUseCase.listarPorLider(
                resolveAuthenticatedUserId(idLider, authentication, "listar planillas por líder"));
        return ResponseEntity.ok(planillas);
    }

    /**
     * Lista las planillas que un aprobador puede revisar según sus permisos actuales.
     */
    @GetMapping("/para-aprobador")
    public ResponseEntity<List<PlanillaResponse>> listarParaAprobador(@RequestParam(required = false) Long idUsuario,
                                                                      Authentication authentication) {
        List<PlanillaResponse> planillas = planillaUseCase.listarParaAprobador(
                resolveAuthenticatedUserId(idUsuario, authentication, "listar planillas para aprobador"));
        return ResponseEntity.ok(planillas);
    }

    /**
     * Lista las planillas pendientes, opcionalmente filtradas por correo del líder.
     */
    @GetMapping("/pendientes")
    public ResponseEntity<List<PlanillaResponse>> listarPendientes(
            @RequestParam(required = false) String correoLider,
            Authentication authentication) {
        List<PlanillaResponse> planillas = planillaUseCase.listarPendientes(
                resolveAuthenticatedCorreo(correoLider, authentication, "listar planillas pendientes"));
        return ResponseEntity.ok(planillas);
    }

    /**
     * Devuelve el detalle completo de una planilla específica.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanillaResponse> obtenerDetalle(@PathVariable Long id) {
        try {
            PlanillaResponse planilla = planillaUseCase.obtenerDetalle(id);
            return ResponseEntity.ok(planilla);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Aprueba una planilla y registra el usuario aprobador cuando viene informado.
     */
    @PutMapping("/{id}/aprobar")
    public ResponseEntity<Map<String, Object>> aprobar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            String usuarioAprobador = resolveAuthenticatedUsuario(
                body != null ? (String) body.get("usuarioAprobador") : null,
                authentication,
                "aprobar planilla");
            Long idUsuarioAprobador = resolveAuthenticatedUserId(
                body != null && body.get("idUsuarioAprobador") != null
                    ? Long.valueOf(body.get("idUsuarioAprobador").toString())
                    : null,
                authentication,
                "aprobar planilla");

            planillaUseCase.aprobar(id, usuarioAprobador, idUsuarioAprobador);
            response.put("success", true);
            response.put("mensaje", "Planilla aprobada exitosamente.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("mensaje", "Planilla no encontrada: " + e.getMessage());
            return ResponseEntity.status(404).body(response);
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("mensaje", e.getMessage());
            return ResponseEntity.status(400).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("mensaje", "Error al aprobar: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Rechaza una planilla y guarda el motivo funcional del rechazo.
     */
    @PutMapping("/{id}/rechazar")
    public ResponseEntity<Map<String, Object>> rechazar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            String motivo = (String) body.get("motivo");
            String usuarioRechazo = resolveAuthenticatedUsuario(
                (String) body.get("usuarioRechazo"),
                authentication,
                "rechazar planilla");
            Long idUsuarioRechazo = resolveAuthenticatedUserId(
                body.get("idUsuarioRechazo") != null
                    ? Long.valueOf(body.get("idUsuarioRechazo").toString())
                    : null,
                authentication,
                "rechazar planilla");

            if (motivo == null || motivo.trim().isEmpty()) {
                response.put("success", false);
                response.put("mensaje", "El motivo del rechazo es obligatorio.");
                return ResponseEntity.status(400).body(response);
            }

            planillaUseCase.rechazar(id, motivo, usuarioRechazo, idUsuarioRechazo);
            response.put("success", true);
            response.put("mensaje", "Planilla rechazada.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("mensaje", "Planilla no encontrada: " + e.getMessage());
            return ResponseEntity.status(404).body(response);
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("mensaje", e.getMessage());
            return ResponseEntity.status(400).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("mensaje", "Error al rechazar: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Dispara una notificación de prueba para validar el flujo configurado de correos.
     */
    @PostMapping("/{id}/notificaciones/prueba")
    public ResponseEntity<Map<String, Object>> probarNotificacion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String tipo = body != null ? String.valueOf(body.getOrDefault("tipo", "solicitud")) : "solicitud";
            String motivo = body != null && body.get("motivo") != null
                    ? String.valueOf(body.get("motivo"))
                    : "Prueba manual de rechazo desde SIPRO";

            planillaUseCase.probarNotificacion(id, tipo, motivo);
            response.put("success", true);
            response.put("mensaje", "Notificacion enviada al flujo configurado.");
            response.put("tipo", tipo);
            response.put("planillaId", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("mensaje", e.getMessage());
            return ResponseEntity.status(400).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("mensaje", "Error al enviar notificacion de prueba: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Descarga el archivo original asociado a la planilla solicitada.
     */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        try {
            byte[] fileContent = planillaUseCase.obtenerArchivoBytes(id);
            String filename = planillaUseCase.obtenerNombreArchivo(id);

            if (filename == null || filename.isEmpty()) {
                filename = "archivo_" + id + ".xlsx";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileContent.length))
                    .body(fileContent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private SiproAuthenticatedUser requireAuthenticatedUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SiproAuthenticatedUser principal) {
            return principal;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión en SIPRO");
    }

    private Long resolveAuthenticatedUserId(Long requestedUserId, Authentication authentication, String operation) {
        SiproAuthenticatedUser principal = requireAuthenticatedUser(authentication);
        if (requestedUserId != null && !principal.idUsuario().equals(requestedUserId)) {
            logger.warn("Se ignoró idUsuario={} durante '{}' y se usó el usuario autenticado={}",
                    requestedUserId, operation, principal.idUsuario());
        }
        return principal.idUsuario();
    }

    private String resolveAuthenticatedUsuario(String requestedUsuario, Authentication authentication, String operation) {
        SiproAuthenticatedUser principal = requireAuthenticatedUser(authentication);
        if (requestedUsuario != null && !requestedUsuario.equalsIgnoreCase(principal.usuario())) {
            logger.warn("Se ignoró usuario='{}' durante '{}' y se usó el usuario autenticado='{}'",
                    requestedUsuario, operation, principal.usuario());
        }
        return principal.usuario();
    }

    private String resolveAuthenticatedCorreo(String requestedCorreo, Authentication authentication, String operation) {
        SiproAuthenticatedUser principal = requireAuthenticatedUser(authentication);
        if (requestedCorreo != null && principal.email() != null && !requestedCorreo.equalsIgnoreCase(principal.email())) {
            logger.warn("Se ignoró correo='{}' durante '{}' y se usó el correo autenticado='{}'",
                    requestedCorreo, operation, principal.email());
        }

        if (principal.email() == null || principal.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El usuario autenticado no tiene correo disponible para esta operación");
        }

        return principal.email();
    }
}