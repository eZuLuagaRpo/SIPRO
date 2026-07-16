package com.bancolombia.sipro.validations.infrastructure.entrypoint;

import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.Segmento;
import com.bancolombia.sipro.validations.domain.model.SiproExcepcionVentanaCarga;
import com.bancolombia.sipro.validations.domain.model.SiproReglaVentanaCarga;
import com.bancolombia.sipro.validations.domain.model.SiproRolesPermisos;
import com.bancolombia.sipro.validations.domain.service.ParametrosService;
import com.bancolombia.sipro.validations.domain.service.ParametrosService.*;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de la pantalla Cambio de Parámetros (SIPRO_Admin_Funcional).
 * Todos los endpoints requieren el rol administrador funcional.
 */
@RestController
@RequestMapping(value = "/api/parametros", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
public class ParametrosController {

    private final ParametrosService parametrosService;

    public ParametrosController(ParametrosService parametrosService) {
        this.parametrosService = parametrosService;
    }

    // ── Ventana de Carga: Regla Base ──────────────────────────────────────

    @GetMapping("/ventana-carga/regla-base")
    public ResponseEntity<Map<String, Object>> getReglaBase(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        SiproReglaVentanaCarga regla = parametrosService.obtenerReglaBase();
        return ResponseEntity.ok(Map.of(
                "reglaId", regla.getReglaId(),
                "offsetDiasApertura", regla.getOffsetDiasApertura(),
                "horaApertura", regla.getHoraApertura().toString(),
                "offsetDiasCierre", regla.getOffsetDiasCierre(),
                "horaCierre", regla.getHoraCierre().toString()
        ));
    }

    // ── Ventana de Carga: Excepciones ─────────────────────────────────────

    @GetMapping("/ventana-carga/excepciones")
    public ResponseEntity<List<Map<String, Object>>> getExcepciones(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        List<SiproExcepcionVentanaCarga> lista = parametrosService.listarExcepciones();
        List<Map<String, Object>> resp = lista.stream().map(e -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("periodoValoracion", e.getPeriodoValoracion().toString());
            m.put("periodoLabel", buildPeriodoLabel(e.getPeriodoValoracion()));
            m.put("fechaAperturaOverride", e.getFechaAperturaOverride() != null ? e.getFechaAperturaOverride().toString() : null);
            m.put("horaAperturaOverride", e.getHoraAperturaOverride() != null ? e.getHoraAperturaOverride().toString() : null);
            m.put("fechaCierreOverride", e.getFechaCierreOverride() != null ? e.getFechaCierreOverride().toString() : null);
            m.put("horaCierreOverride", e.getHoraCierreOverride() != null ? e.getHoraCierreOverride().toString() : null);
            m.put("motivo", e.getMotivo());
            m.put("creadoPorId", e.getCreadoPorId());
            m.put("creadoEn", e.getCreadoEn() != null ? e.getCreadoEn().toString() : null);
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/ventana-carga/excepciones")
    public ResponseEntity<Map<String, Object>> crearExcepcion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestBody ExcepcionRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.crearExcepcion(req, principal.idUsuario());
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Excepción creada correctamente."));
    }

    @PutMapping("/ventana-carga/excepciones/{periodo}")
    public ResponseEntity<Map<String, Object>> actualizarExcepcion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable String periodo,
            @RequestBody ExcepcionRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.actualizarExcepcion(periodo, req, principal.idUsuario());
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Excepción actualizada correctamente."));
    }

    @DeleteMapping("/ventana-carga/excepciones/{periodo}")
    public ResponseEntity<Map<String, Object>> eliminarExcepcion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable String periodo) {
        parametrosService.requireParametros(principal);
        parametrosService.eliminarExcepcion(periodo);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Excepción eliminada."));
    }

    @GetMapping("/ventana-carga/meses-disponibles")
    public ResponseEntity<List<MesDisponibleDto>> getMesesDisponibles(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        return ResponseEntity.ok(parametrosService.listarMesesDisponibles());
    }

    // ── Usuarios ──────────────────────────────────────────────────────────

    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioResumenDto>> getUsuarios(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        return ResponseEntity.ok(parametrosService.listarUsuarios());
    }

    @GetMapping("/usuarios/{idUsuario}/asignacion")
    public ResponseEntity<AsignacionDto> getAsignacion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable Long idUsuario) {
        parametrosService.requireParametros(principal);
        return ResponseEntity.ok(parametrosService.obtenerAsignacion(idUsuario));
    }

    @PutMapping("/usuarios/{idUsuario}/asignacion")
    public ResponseEntity<Map<String, Object>> guardarAsignacion(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable Long idUsuario,
            @RequestBody AsignacionRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.guardarAsignacion(idUsuario, req);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Asignación guardada correctamente."));
    }

    @GetMapping("/usuarios/{idLider}/pendientes-lider")
    public ResponseEntity<PendientesDto> getPendientesLider(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable Long idLider) {
        parametrosService.requireParametros(principal);
        return ResponseEntity.ok(parametrosService.verificarPendientesLider(idLider));
    }

    @PutMapping("/usuarios/cambio-lider")
    public ResponseEntity<Map<String, Object>> aplicarCambioLider(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestBody CambioLiderRequest req) {
        parametrosService.requireParametros(principal);
        ParametrosService.CambioLiderResultado resultado = parametrosService.aplicarCambioLider(req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "mensaje", resultado.totalTransferidas() > 0
                        ? "Líder actualizado. Se transfirieron " + resultado.totalTransferidas() + " planilla(s) pendiente(s) al nuevo líder."
                        : "Líder actualizado correctamente. No había planillas pendientes que transferir.",
                "totalTransferidas", resultado.totalTransferidas(),
                "detalles", resultado.detalles()
        ));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Object>> registrarUsuario(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestBody NuevoUsuarioRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.registrarNuevoUsuario(req, principal);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Usuario registrado exitosamente."));
    }

    /**
     * Consulta en tiempo real el rol del usuario en Azure Entra ID usando las credenciales
     * de aplicación configuradas ({@code AZURE_CLIENT_SECRET}).
     * Retorna {@code encontrado=false} si las credenciales no están configuradas o el usuario
     * no pertenece a ningún grupo SIPRO en el directorio.
     */
    @GetMapping("/usuarios/{idUsuario}/rol-azure")
    public ResponseEntity<Map<String, Object>> getRolAzure(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable Long idUsuario) {
        parametrosService.requireParametros(principal);
        return ResponseEntity.ok(parametrosService.resolverRolAzure(idUsuario));
    }

    /**
     * Consulta masiva del rol Azure para una lista de IDs de usuarios.
     * Útil para la sección 3 de Parámetros: valida que cada cargador realmente
     * pertenezca al grupo SIPRO que tiene asignado en PostgreSQL.
     * Ejemplo: GET /api/parametros/usuarios/validacion-azure?ids=1,2,3
     */
    @GetMapping("/usuarios/validacion-azure")
    public ResponseEntity<Map<String, Object>> getValidacionAzureMasiva(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestParam List<Long> ids) {
        parametrosService.requireParametros(principal);
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe indicar al menos un ID de usuario."));
        }
        Map<String, Object> validaciones = parametrosService.resolverRolAzureMasivo(ids);
        return ResponseEntity.ok(Map.of("validaciones", validaciones));
    }

    // ── Productos ─────────────────────────────────────────────────────────

    @GetMapping("/productos")
    public ResponseEntity<List<Map<String, Object>>> getProductos(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        List<Producto> productos = parametrosService.listarProductos();
        List<Map<String, Object>> resp = productos.stream().map(p -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("idProducto", p.getIdProducto());
            m.put("titulo", p.getTitulo());
            m.put("idSegmento", p.getIdSegmento());
            m.put("activo", p.getActivo() != null && p.getActivo() == 1);
            m.put("nombreArchivoPermitido", p.getNombreArchivoPermitido());
            m.put("nombreControlPermitido", p.getNombreControlPermitido());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/productos")
    public ResponseEntity<Map<String, Object>> crearProducto(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @RequestBody ProductoRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.crearProducto(req);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Producto creado correctamente."));
    }

    @PutMapping("/productos/{idProducto}")
    public ResponseEntity<Map<String, Object>> actualizarProducto(
            @AuthenticationPrincipal SiproAuthenticatedUser principal,
            @PathVariable Long idProducto,
            @RequestBody ProductoRequest req) {
        parametrosService.requireParametros(principal);
        parametrosService.actualizarProducto(idProducto, req);
        return ResponseEntity.ok(Map.of("success", true, "mensaje", "Producto actualizado correctamente."));
    }

    // ── Catálogos ─────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getRoles(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        List<SiproRolesPermisos> roles = parametrosService.listarRoles();
        List<Map<String, Object>> resp = roles.stream().map(r -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("idRol", r.getIdRol());
            m.put("rol", r.getRol());
            m.put("perfil", r.getPerfil());
            m.put("descripcion", r.getDescripcion());
            m.put("cargarArchivos", r.getCargarArchivos());
            m.put("aprobar", r.getAprobar());
            m.put("modificarParametros", r.getModificarParametros());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/segmentos")
    public ResponseEntity<List<Map<String, Object>>> getSegmentos(
            @AuthenticationPrincipal SiproAuthenticatedUser principal) {
        parametrosService.requireParametros(principal);
        List<Segmento> segmentos = parametrosService.listarSegmentos();
        List<Map<String, Object>> resp = segmentos.stream().map(s -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("nombre", s.getNombre());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(resp);
    }

    // ── Utilidades privadas ───────────────────────────────────────────────

    private String buildPeriodoLabel(java.time.LocalDate fecha) {
        String mes = fecha.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("es", "CO"));
        return mes.substring(0, 1).toUpperCase() + mes.substring(1) + " " + fecha.getYear();
    }
}
