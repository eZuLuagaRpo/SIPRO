package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.UsuarioPermisosResponse;
import com.bancolombia.sipro.validations.application.dto.UsuarioPermisosResponse.ProductoRolResponse;
import com.bancolombia.sipro.validations.domain.model.SiproRolesPermisos;
import com.bancolombia.sipro.validations.domain.model.SiproUsuarioProductoRol;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproRolesPermisosRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.shared.utils.GroupNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de dominio para el control de acceso basado en roles por producto (RBAC).
 * Centraliza la lógica de verificación de permisos del usuario.
 */
@Service
public class RbacService {

    private static final int ROL_APROBADOR_ID = 2;
    private static final int ROL_SOPORTE_TECNICO_ID = 3;
    /** Usuario_Analista (4), Auditoria (5) y Admin_Permisos (6): acceso a /resumen y /tablero. */
    private static final Set<Integer> ROLES_CONSOLIDADOS_VISIBLES = Set.of(4, 5, 6);

    private static final Logger logger = LoggerFactory.getLogger(RbacService.class);

    private final SiproUsuarioProductoRolRepository uprRepository;
    private final SiproRolesPermisosRepository rolesPermisosRepository;

    public RbacService(SiproUsuarioProductoRolRepository uprRepository,
                       SiproRolesPermisosRepository rolesPermisosRepository) {
        this.uprRepository = uprRepository;
        this.rolesPermisosRepository = rolesPermisosRepository;
    }

    /**
     * Construye el resumen completo de permisos de un usuario.
     * Se utiliza tras el login para informar al frontend qué módulos habilitar.
     *
     * @param idUsuario ID del usuario autenticado
     * @return UsuarioPermisosResponse con los permisos consolidados
     */
    @Transactional(readOnly = true)
    public UsuarioPermisosResponse obtenerPermisosUsuario(Long idUsuario) {
        logger.info("Consultando permisos RBAC para usuario: {}", idUsuario);

        List<SiproUsuarioProductoRol> asignaciones = uprRepository.findActiveByUsuarioWithProducto(idUsuario);

        return buildPermisos(asignaciones, asignaciones.stream()
            .map(SiproUsuarioProductoRol::getRol)
            .filter(role -> role != null && role.getIdRol() != null)
            .collect(Collectors.toMap(
                SiproRolesPermisos::getIdRol,
                role -> role,
                (left, right) -> left,
                LinkedHashMap::new)), idUsuario, null);
        }

        /**
         * Construye permisos efectivos filtrando por los grupos que llegan desde Entra ID.
         */
        @Transactional(readOnly = true)
        public UsuarioPermisosResponse obtenerPermisosUsuario(Long idUsuario, Set<String> gruposAd) {
        logger.info("Consultando permisos RBAC para usuario {} con grupos AD: {}", idUsuario, gruposAd);

        List<SiproUsuarioProductoRol> asignaciones = uprRepository.findActiveByUsuarioWithProducto(idUsuario);
        if (gruposAd == null || gruposAd.isEmpty()) {
            logger.warn("El usuario {} no trae grupos AD. Se devolverán permisos vacíos.", idUsuario);
            return buildPermisos(List.of(), Map.of(), idUsuario, gruposAd);
        }

        Set<String> normalizedGroups = gruposAd.stream()
            .filter(group -> group != null && !group.isBlank())
            .map(GroupNameNormalizer::normalizeFunctionalGroupName)
            .filter(group -> group != null && !group.isBlank())
            .collect(Collectors.toSet());

        Map<Integer, SiproRolesPermisos> rolesPorGrupo = rolesPermisosRepository.findAll()
            .stream()
            .filter(role -> role.getGrupoAd() != null && !role.getGrupoAd().isBlank())
            .filter(role -> normalizedGroups.contains(
                GroupNameNormalizer.normalizeFunctionalGroupName(role.getGrupoAd())))
            .filter(role -> role.getIdRol() != null)
            .collect(Collectors.toMap(
                SiproRolesPermisos::getIdRol,
                role -> role,
                (left, right) -> left,
                LinkedHashMap::new));

        List<SiproUsuarioProductoRol> asignacionesEfectivas = asignaciones.stream()
            .filter(upr -> upr.getRol() != null && upr.getRol().getIdRol() != null)
            .filter(upr -> rolesPorGrupo.containsKey(upr.getRol().getIdRol()))
            .toList();

        return buildPermisos(asignacionesEfectivas, rolesPorGrupo, idUsuario, normalizedGroups);
        }

        private UsuarioPermisosResponse buildPermisos(List<SiproUsuarioProductoRol> asignaciones,
                              Map<Integer, SiproRolesPermisos> rolesEfectivos,
                              Long idUsuario,
                              Set<String> gruposAd) {

        UsuarioPermisosResponse permisos = new UsuarioPermisosResponse();

        boolean puedeCargar = false;
        boolean puedeAprobar = false;
        boolean puedeSolicitar = false;
        boolean puedeVisualizar = false;
        boolean puedeExportar = false;
        boolean puedeModificar = false;
        boolean puedeAccederAdminTecnico = false;
        boolean puedeVisualizarConsolidados = false;

        for (SiproRolesPermisos rol : rolesEfectivos.values()) {
            if (rol == null) {
                continue;
            }

            if (rol.puedeCargar()) puedeCargar = true;
            if (rol.puedeAprobar() || Integer.valueOf(ROL_APROBADOR_ID).equals(rol.getIdRol())) puedeAprobar = true;
            if (rol.puedeSolicitarAprobacion()) puedeSolicitar = true;
            if (rol.puedeVisualizar()) puedeVisualizar = true;
            if (rol.getExportarReportes() != null && rol.getExportarReportes() == 1) puedeExportar = true;
            if (rol.getModificarParametros() != null && rol.getModificarParametros() == 1) puedeModificar = true;
            if (Integer.valueOf(ROL_SOPORTE_TECNICO_ID).equals(rol.getIdRol())) puedeAccederAdminTecnico = true;
            if (ROLES_CONSOLIDADOS_VISIBLES.contains(rol.getIdRol())) puedeVisualizarConsolidados = true;
        }

        permisos.setPuedeCargar(puedeCargar);
        permisos.setPuedeAprobar(puedeAprobar);
        permisos.setPuedeSolicitarAprobacion(puedeSolicitar);
        permisos.setPuedeVisualizar(puedeVisualizar);
        permisos.setPuedeExportar(puedeExportar);
        permisos.setPuedeModificarParametros(puedeModificar);
        // puedeAccederPanelAdmin: acceso a /admin (dashboard técnico, consola SQL, logs), exclusivo
        // de Soporte Técnico (id_rol=3). No se deriva de un flag genérico porque, tras el rediseño de
        // roles, Soporte Técnico (3) y Auditoria (5) quedaron con exactamente los mismos flags en
        // sipro_roles_permisos — igual que ya ocurría con ROL_APROBADOR_ID, hace falta anclar por id_rol.
        permisos.setPuedeAccederPanelAdmin(puedeAccederAdminTecnico);
        // puedeVisualizarConsolidados: acceso a /resumen y /tablero (Usuario_Analista, Auditoria, Admin_Permisos).
        permisos.setPuedeVisualizarConsolidados(puedeVisualizarConsolidados);

        // Construir lista detallada de productos asignados
        List<ProductoRolResponse> productosAsignados = asignaciones.stream()
                .map(upr -> new ProductoRolResponse(
                        upr.getId().getIdProducto(),
                        upr.getProducto() != null ? upr.getProducto().getTitulo() : null,
                        upr.getRol() != null ? upr.getRol().getIdRol() : null,
                        upr.getRol() != null ? upr.getRol().getRol() : null,
                        upr.getOrdenFlujo()
                ))
                .collect(Collectors.toList());

        permisos.setProductosAsignados(productosAsignados);

            logger.info("Permisos RBAC para usuario {}: cargar={}, aprobar={}, roles={}, asignaciones={}, grupos={}",
                idUsuario, puedeCargar, puedeAprobar, rolesEfectivos.size(), asignaciones.size(), gruposAd);

        return permisos;
    }

    /**
     * Verifica si un usuario puede cargar archivos en cualquier producto.
     */
    @Transactional(readOnly = true)
    public boolean puedeCargar(Long idUsuario) {
        return uprRepository.canUserUploadAny(idUsuario);
    }

    /**
     * Verifica si un usuario puede aprobar en cualquier producto.
     */
    @Transactional(readOnly = true)
    public boolean puedeAprobar(Long idUsuario) {
        return uprRepository.canUserApproveAny(idUsuario);
    }

    /**
     * Verifica si un usuario puede cargar archivos para un producto específico.
     */
    @Transactional(readOnly = true)
    public boolean puedeCargarProducto(Long idUsuario, Long idProducto) {
        return uprRepository.canUserUploadForProduct(idUsuario, idProducto);
    }

    /**
     * Verifica si un usuario puede aprobar para un producto específico.
     */
    @Transactional(readOnly = true)
    public boolean puedeAprobarProducto(Long idUsuario, Long idProducto) {
        return uprRepository.canUserApproveForProduct(idUsuario, idProducto);
    }
}