package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.*;
import com.bancolombia.sipro.validations.infrastructure.notification.MailTemplateNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.*;
import com.bancolombia.sipro.validations.infrastructure.security.MicrosoftGraphDirectoryService;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import com.bancolombia.sipro.validations.shared.utils.GroupNameNormalizer;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
/**
 * Servicio de dominio para la pantalla de Cambio de Parámetros.
 * Solo accesible por usuarios con rol SIPRO_Admin_Permisos (id_rol=6, modificar_parametros=1).
 */
@Service
@Transactional(readOnly = true)
public class ParametrosService {

    private static final Logger logger = LoggerFactory.getLogger(ParametrosService.class);
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ROL_LIDER_POR_DEFECTO = "Líder";
    private static final DateTimeFormatter FORMATTER_FECHA_CORTE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("es-CO"));
    private static final int MESES_DISPONIBLES_ADELANTE = 6;
    private static final long ID_SEGMENTO_FULL_IFRS = 2L;
    private static final String SUFIJO_MASCARA_FECHA = "_AAAAMMDD";
    private static final String PREFIJO_NOMBRE_CONTROL = "CTRL-";

    private final SiproReglaVentanaCargaRepository reglaRepo;
    private final SiproExcepcionVentanaCargaRepository excepcionRepo;
    private final UsuarioPersonaRepository personaRepo;
    private final UsuarioAreaRepository areaRepo;
    private final SiproUsuarioProductoRolRepository uprRepo;
    private final SiproDetalleCargaPlanillasRepository planillasRepo;
    private final UsuarioLoginRepository loginRepo;
    private final ProductoRepository productoRepo;
    private final SegmentoRepository segmentoRepo;
    private final SiproRolesPermisosRepository rolesRepo;
    private final AdminAccessService adminAccessService;
    private final EntityManager entityManager;
    private final MicrosoftGraphDirectoryService graphDirectoryService;
    private final MailTemplateNotificationService mailTemplateService;

    public ParametrosService(
            SiproReglaVentanaCargaRepository reglaRepo,
            SiproExcepcionVentanaCargaRepository excepcionRepo,
            UsuarioPersonaRepository personaRepo,
            UsuarioAreaRepository areaRepo,
            SiproUsuarioProductoRolRepository uprRepo,
            SiproDetalleCargaPlanillasRepository planillasRepo,
            UsuarioLoginRepository loginRepo,
            ProductoRepository productoRepo,
            SegmentoRepository segmentoRepo,
            SiproRolesPermisosRepository rolesRepo,
            AdminAccessService adminAccessService,
            EntityManager entityManager,
            MicrosoftGraphDirectoryService graphDirectoryService,
            MailTemplateNotificationService mailTemplateService) {
        this.reglaRepo = reglaRepo;
        this.excepcionRepo = excepcionRepo;
        this.personaRepo = personaRepo;
        this.areaRepo = areaRepo;
        this.uprRepo = uprRepo;
        this.planillasRepo = planillasRepo;
        this.loginRepo = loginRepo;
        this.productoRepo = productoRepo;
        this.segmentoRepo = segmentoRepo;
        this.rolesRepo = rolesRepo;
        this.adminAccessService = adminAccessService;
        this.entityManager = entityManager;
        this.graphDirectoryService = graphDirectoryService;
        this.mailTemplateService = mailTemplateService;
    }

    // ── Guard centralizado ─────────────────────────────────────────────────

    public void requireParametros(SiproAuthenticatedUser principal) {
        adminAccessService.requireAdminPermisos(principal);
    }

    // ── Rol Azure: resolución en tiempo real ──────────────────────────────

    /**
     * Consulta los grupos de un usuario en Azure Entra ID en tiempo real y los mapea
     * al rol SIPRO correspondiente según {@code sipro_roles_permisos.grupo_ad}.
     * <p>
     * Si el usuario no tiene correo registrado o las credenciales de Graph no están
     * configuradas, retorna {@code encontrado=false} sin lanzar excepción.
     *
     * @param idUsuario ID del usuario SIPRO a consultar
     * @return mapa con {@code encontrado}, {@code idRol}, {@code nombreRol}, {@code grupoAd}
     */
    public Map<String, Object> resolverRolAzure(Long idUsuario) {
        UsuarioPersona persona = personaRepo.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario " + idUsuario + " no encontrado."));

        String correo = persona.getCorreo();
        if (correo == null || correo.isBlank()) {
            logger.warn("El usuario {} no tiene correo registrado; no se puede consultar Azure.", idUsuario);
            return buildRolAzureNoEncontrado("El usuario no tiene correo registrado en SIPRO.");
        }

        Set<String> grupos = graphDirectoryService.resolveUserGroupsByUpn(correo);
        if (grupos.isEmpty()) {
            return buildRolAzureNoEncontrado(
                    "No se encontraron grupos en Azure Entra ID para " + correo + ". " +
                    "Verifique que las credenciales de Graph estén configuradas y que el usuario exista en el directorio.");
        }

        Set<String> gruposNormalizados = grupos.stream()
                .filter(g -> g != null && !g.isBlank())
            .map(GroupNameNormalizer::normalizeFunctionalGroupName)
            .filter(g -> g != null && !g.isBlank())
                .collect(Collectors.toSet());

        return rolesRepo.findAll().stream()
                .filter(r -> r.getGrupoAd() != null && !r.getGrupoAd().isBlank())
            .filter(r -> gruposNormalizados.contains(
                GroupNameNormalizer.normalizeFunctionalGroupName(r.getGrupoAd())))
                .findFirst()
                .map(r -> {
                    Map<String, Object> resultado = new LinkedHashMap<>();
                    resultado.put("encontrado", true);
                    resultado.put("idRol", r.getIdRol());
                    resultado.put("nombreRol", r.getRol());
                    resultado.put("grupoAd", r.getGrupoAd());
                    resultado.put("correoConsultado", correo);
                    return resultado;
                })
                .orElseGet(() -> buildRolAzureNoEncontrado(
                        "El usuario " + correo + " tiene " + grupos.size() + " grupo(s) en Azure, " +
                        "pero ninguno corresponde a un rol SIPRO configurado en sipro_roles_permisos.grupo_ad."));
    }

    private Map<String, Object> buildRolAzureNoEncontrado(String mensaje) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("encontrado", false);
        resultado.put("idRol", null);
        resultado.put("nombreRol", null);
        resultado.put("grupoAd", null);
        resultado.put("mensaje", mensaje);
        return resultado;
    }

    /**
     * Consulta en paralelo el rol Azure de una lista de IDs de usuarios.
     * Cada entrada del mapa retornado usa el idUsuario como clave (en String para JSON).
     * Si un ID no existe, retorna {@code encontrado=false} para ese usuario.
     */
    public Map<String, Object> resolverRolAzureMasivo(List<Long> ids) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        for (Long id : ids) {
            try {
                resultado.put(String.valueOf(id), resolverRolAzure(id));
            } catch (Exception ex) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("encontrado", false);
                err.put("idRol", null);
                err.put("nombreRol", null);
                err.put("grupoAd", null);
                err.put("mensaje", "Error al consultar: " + ex.getMessage());
                resultado.put(String.valueOf(id), err);
            }
        }
        return resultado;
    }

    // ── Ventana de Carga: Regla Base ──────────────────────────────────────

    public SiproReglaVentanaCarga obtenerReglaBase() {
        return reglaRepo.findReglaVigente(LocalDate.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe regla de ventana de carga vigente."));
    }

    // ── Ventana de Carga: Excepciones ─────────────────────────────────────

    public List<SiproExcepcionVentanaCarga> listarExcepciones() {
        return excepcionRepo.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "periodoValoracion"));
    }

    @Transactional
    public SiproExcepcionVentanaCarga crearExcepcion(ExcepcionRequest req, Long idUsuario) {
        LocalDate periodo = parseLocalDate(req.getPeriodoValoracion());
        if (excepcionRepo.existsById(periodo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una excepción para el período " + req.getPeriodoValoracion());
        }
        SiproExcepcionVentanaCarga exc = buildExcepcion(req, periodo, idUsuario);
        exc.setCreadoPorId(idUsuario);
        exc.setCreadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        return excepcionRepo.save(exc);
    }

    @Transactional
    public SiproExcepcionVentanaCarga actualizarExcepcion(String periodoStr, ExcepcionRequest req, Long idUsuario) {
        LocalDate periodo = parseLocalDate(periodoStr);
        SiproExcepcionVentanaCarga exc = excepcionRepo.findById(periodo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe excepción para el período " + periodoStr));
        actualizarCampos(exc, req);
        exc.setModificadoPorId(idUsuario);
        exc.setModificadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        return excepcionRepo.save(exc);
    }

    @Transactional
    public void eliminarExcepcion(String periodoStr) {
        LocalDate periodo = parseLocalDate(periodoStr);
        if (!excepcionRepo.existsById(periodo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe excepción para el período " + periodoStr);
        }
        excepcionRepo.deleteById(periodo);
    }

    /** Genera la lista de meses disponibles en los próximos N meses para crear excepciones. */
    public List<MesDisponibleDto> listarMesesDisponibles() {
        List<SiproExcepcionVentanaCarga> existentes = excepcionRepo.findAll();
        Set<LocalDate> yaDefinidos = existentes.stream()
                .map(SiproExcepcionVentanaCarga::getPeriodoValoracion)
                .collect(Collectors.toSet());

        List<MesDisponibleDto> result = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        for (int i = 1; i <= MESES_DISPONIBLES_ADELANTE; i++) {
            LocalDate inicio = hoy.withDayOfMonth(1).plusMonths(i);
            LocalDate ultimoDia = inicio.withDayOfMonth(inicio.lengthOfMonth());
            if (!yaDefinidos.contains(ultimoDia)) {
                String label = inicio.getMonth()
                        .getDisplayName(TextStyle.FULL, new Locale("es", "CO")) +
                        " " + inicio.getYear();
                result.add(new MesDisponibleDto(
                        ultimoDia.toString(),
                        label.substring(0, 1).toUpperCase() + label.substring(1)));
            }
        }
        return result;
    }

    // ── Usuarios ──────────────────────────────────────────────────────────

    public List<UsuarioResumenDto> listarUsuarios() {
        List<UsuarioPersona> personas = personaRepo.findAll();
        List<UsuarioArea> areas = areaRepo.findAll();
        Map<Long, UsuarioArea> areaMap = areas.stream()
                .collect(Collectors.toMap(UsuarioArea::getIdUsuario, a -> a));

        List<SiproUsuarioProductoRol> todasAsignaciones = uprRepo.findAll().stream()
                .filter(upr -> Boolean.TRUE.equals(upr.getActivo()))
                .collect(Collectors.toList());
        Map<Long, List<SiproUsuarioProductoRol>> asigMap = todasAsignaciones.stream()
                .collect(Collectors.groupingBy(upr -> upr.getId().getIdUsuario()));

        return personas.stream().map(p -> {
            UsuarioArea area = areaMap.get(p.getIdUsuario());
            List<SiproUsuarioProductoRol> asigs = asigMap.getOrDefault(p.getIdUsuario(), List.of());
            Integer idRol = asigs.isEmpty() ? null : asigs.get(0).getId().getIdRol();
            String nombreRol = asigs.isEmpty() || asigs.get(0).getRol() == null
                    ? null : asigs.get(0).getRol().getPerfil();
            String grupoAdEsperado = asigs.isEmpty() || asigs.get(0).getRol() == null
                    ? null : asigs.get(0).getRol().getGrupoAd();
            List<Long> segmentos = asigs.stream()
                    .map(SiproUsuarioProductoRol::getIdSegmento)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());

            Long idLider = null;
            String nombreLider = null;
            String areaNombre = null;
            String cargoJefe = null;
            if (area != null) {
                areaNombre = area.getAreaNombre();
                cargoJefe = area.getCargoJefe();
                if (area.getJefe() != null) {
                    idLider = area.getJefe().getIdUsuario();
                    nombreLider = area.getJefe().getNombres() + " " + area.getJefe().getApellidos();
                }
            }

            return new UsuarioResumenDto(
                    p.getIdUsuario(), p.getUsuario(), p.getNombres(), p.getApellidos(),
                    (p.getNombres() + " " + p.getApellidos()).trim(),
                    p.getCorreo(), areaNombre, cargoJefe, idLider, nombreLider,
                    idRol, nombreRol, grupoAdEsperado, segmentos);
        }).sorted(Comparator.comparing(UsuarioResumenDto::nombreCompleto))
                .collect(Collectors.toList());
    }

    public AsignacionDto obtenerAsignacion(Long idUsuario) {
        List<SiproUsuarioProductoRol> asigs = uprRepo.findActiveByUsuarioWithProducto(idUsuario);
        if (asigs.isEmpty()) {
            return new AsignacionDto(null, List.of());
        }
        Integer idRol = asigs.get(0).getId().getIdRol();
        Map<Long, List<Long>> porSegmento = new LinkedHashMap<>();
        for (SiproUsuarioProductoRol upr : asigs) {
            Long idSegmento = upr.getIdSegmento();
            Producto p = upr.getProducto();
            if (p != null && idSegmento != null) {
                porSegmento.computeIfAbsent(idSegmento, k -> new ArrayList<>()).add(p.getIdProducto());
            }
        }
        List<ProductosPorSegmentoDto> pps = porSegmento.entrySet().stream()
                .map(e -> new ProductosPorSegmentoDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new AsignacionDto(idRol, pps);
    }

    @Transactional
    public void guardarAsignacion(Long idUsuario, AsignacionRequest req) {
        personaRepo.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
        SiproRolesPermisos rol = obtenerRol(req.getIdRol());
        List<SiproUsuarioProductoRol> actuales = uprRepo.findActiveByUsuario(idUsuario);
        Integer idRolActual = actuales.isEmpty() ? null : actuales.get(0).getId().getIdRol();
        validarCambioRolPermitido(idUsuario, idRolActual, rol);

        // ── Determinar lista de productos a aplicar ──────────────────────────────
        // El rol es asignado por Azure Entra ID; esta sección solo gestiona productos
        // y segmentos. No se permite cambio de rol desde aquí.
        List<ProductosPorSegmentoRequest> productosNormalizados;
        if (rol.puedeCargar()) {
            // Rol cargador: la matriz de productos es obligatoria.
            List<ProductosPorSegmentoRequest> solicitados = Optional.ofNullable(req.getProductosPorSegmento()).orElse(List.of());
            boolean tieneProductos = solicitados.stream()
                    .anyMatch(pps -> pps != null
                            && pps.getIdsProductosPermitidos() != null
                            && !pps.getIdsProductosPermitidos().isEmpty());
            if (!tieneProductos) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El usuario cargador debe tener al menos un producto asignado.");
            }
            productosNormalizados = solicitados;
        } else {
            // Rol no-cargador: conservar la base actual para no romper permisos vigentes.
            productosNormalizados = normalizarProductosAsignacion(req, actuales, rol);
        }

        // Índice de registros actuales por (idProducto:idRol:idSegmento) para upsert eficiente.
        // Permite reutilizar la entidad existente (preservando creado_en) en lugar de
        // crear un registro nuevo con la misma PK compuesta, lo que causaría un UPDATE
        // con creado_en=null violando la restricción NOT NULL de la columna.
        Map<String, SiproUsuarioProductoRol> actualesMap = actuales.stream()
                .collect(Collectors.toMap(
                        upr -> upr.getId().getIdProducto() + ":" + upr.getId().getIdRol() + ":" + upr.getId().getIdSegmento(),
                        upr -> upr,
                        (a, b) -> a));

        LinkedHashSet<Long> idsProductos = normalizarIds(
            productosNormalizados.stream()
                        .filter(Objects::nonNull)
                        .map(ProductosPorSegmentoRequest::getIdsProductosPermitidos)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .toList());
        Map<Long, Producto> productosPorId = cargarProductos(idsProductos);

        // Calcular las llaves (producto:rol:segmento) que deben quedar activas
        Set<String> llavesSolicitadas = new LinkedHashSet<>();
        List<SiproUsuarioProductoRol> nuevos = new ArrayList<>();

        for (ProductosPorSegmentoRequest pps : productosNormalizados) {
            if (pps == null || pps.getIdSegmento() == null || pps.getIdsProductosPermitidos() == null) {
                continue;
            }
            for (Long idProducto : pps.getIdsProductosPermitidos()) {
                Producto producto = productosPorId.get(idProducto);
                if (producto == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Producto " + idProducto + " no encontrado.");
                }
                if (!Objects.equals(producto.getIdSegmento(), pps.getIdSegmento())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El producto " + idProducto + " no pertenece al segmento " + pps.getIdSegmento() + ".");
                }
                String llave = idProducto + ":" + rol.getIdRol() + ":" + producto.getIdSegmento();
                if (!llavesSolicitadas.add(llave)) {
                    continue; // deduplicar
                }
                // Solo crear registro nuevo si no existe ya con esa PK compuesta.
                // Si ya existe, se reactiva más abajo sin tocar creado_en.
                if (!actualesMap.containsKey(llave)) {
                    nuevos.add(new SiproUsuarioProductoRol(
                            idUsuario, idProducto, rol.getIdRol(), producto.getIdSegmento(), (short) 1));
                }
            }
        }

        // Sincronizar activo en los registros existentes:
        //   - Los que están en el request → reactivar (activo=true)
        //   - Los que ya no están en el request → desactivar (activo=false)
        // creado_en nunca se toca: está marcado updatable=false en la entidad.
        actuales.forEach(upr -> {
            String llave = upr.getId().getIdProducto() + ":" + upr.getId().getIdRol() + ":" + upr.getId().getIdSegmento();
            upr.setActivo(llavesSolicitadas.contains(llave));
        });

        uprRepo.saveAll(actuales);
        if (!nuevos.isEmpty()) {
            uprRepo.saveAll(nuevos);
        }
        logger.info("Asignación actualizada para usuario {}: {} entradas activas", idUsuario, llavesSolicitadas.size());
    }

    public PendientesDto verificarPendientesLider(Long idLider) {
        long cantidad = planillasRepo.countByIdLiderAndEstadoPlanillaAndActivoTrue(idLider, ESTADO_PENDIENTE);
        return new PendientesDto(cantidad > 0, cantidad);
    }

    @Transactional
    public CambioLiderResultado aplicarCambioLider(CambioLiderRequest req) {
        if (req.getIdsUsuariosAfectados() == null || req.getIdsUsuariosAfectados().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un usuario.");
        }
        if (req.getIdNuevoLider() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar el nuevo líder.");
        }
        List<Long> idsUsuariosAfectados = normalizarIds(req.getIdsUsuariosAfectados()).stream().toList();
        UsuarioPersona nuevoLider = personaRepo.findById(req.getIdNuevoLider())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nuevo líder no encontrado."));

        List<UsuarioArea> areas = areaRepo.findAllById(idsUsuariosAfectados);
        if (areas.size() != idsUsuariosAfectados.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Uno o más usuarios afectados no tienen configuración de área.");
        }

        // Capturar el líder anterior de cada cargador (antes de sobreescribirlo)
        Map<Long, Long> liderAnteriorPorCargador = new LinkedHashMap<>();
        for (UsuarioArea area : areas) {
            if (area.getJefe() != null) {
                liderAnteriorPorCargador.put(area.getIdUsuario(), area.getJefe().getIdUsuario());
            }
        }

        // Agrupar cargadores por líder anterior para transferir planillas por lote
        Map<Long, List<Long>> cargadoresPorLiderAnterior = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> entry : liderAnteriorPorCargador.entrySet()) {
            cargadoresPorLiderAnterior
                    .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        String nombreNuevoLider = construirNombreCompleto(nuevoLider);
        String correoNuevoLider = nuevoLider.getCorreo() != null ? nuevoLider.getCorreo() : "";
        String rolLider = resolverEtiquetaLider(nuevoLider.getIdUsuario());

        // Consultar planillas a transferir ANTES de actualizar, para el correo de notificación
        List<SiproDetalleCargaPlanillas> planillasATransferir = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : cargadoresPorLiderAnterior.entrySet()) {
            Long idLiderAnterior = entry.getKey();
            // No transferir si el líder anterior ya es el nuevo líder
            if (idLiderAnterior.equals(req.getIdNuevoLider())) continue;
            planillasATransferir.addAll(
                    planillasRepo.findPendientesYRechazadosPorCargadoresYLider(entry.getValue(), idLiderAnterior));
        }

        // Actualizar área (líder) de los cargadores afectados
        for (UsuarioArea area : areas) {
            area.setJefe(nuevoLider);
            area.setCargoJefe(rolLider);
        }
        areaRepo.saveAll(areas);

        // Transferir planillas PENDIENTE y RECHAZADO al nuevo líder (agrupadas por líder anterior)
        int totalTransferidas = 0;
        for (Map.Entry<Long, List<Long>> entry : cargadoresPorLiderAnterior.entrySet()) {
            Long idLiderAnterior = entry.getKey();
            if (idLiderAnterior.equals(req.getIdNuevoLider())) continue;
            totalTransferidas += planillasRepo.transferirPlanillasActivasAlNuevoLider(
                    entry.getValue(),
                    idLiderAnterior,
                    req.getIdNuevoLider(),
                    nombreNuevoLider,
                    correoNuevoLider);
        }

        int transferidasFinal = totalTransferidas;
        List<SiproDetalleCargaPlanillas> planillasParaCorreo = List.copyOf(planillasATransferir);
        String correoDestinatario = correoNuevoLider;
        String nombreDestinatario = nombreNuevoLider;

        logger.info("Cambio de líder a {} ({}) aplicado a {} usuarios. Planillas transferidas: {}. Usuarios afectados={} Planillas={}.",
            req.getIdNuevoLider(),
            nombreNuevoLider,
            areas.size(),
            transferidasFinal,
            idsUsuariosAfectados,
            planillasATransferir.stream()
                .map(SiproDetalleCargaPlanillas::getId)
                .filter(Objects::nonNull)
                .toList());

        // Enviar correo al nuevo líder de forma no bloqueante (post-commit)
        if (!planillasParaCorreo.isEmpty() && !correoDestinatario.isBlank()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarCorreoTransferenciaLider(
                            correoDestinatario,
                            nombreDestinatario,
                            req.getIdNuevoLider(),
                            idsUsuariosAfectados,
                            planillasParaCorreo);
                }
            });
        }

        // Construir resultado resumido
        List<PlanillaTransferidaDto> detalles = planillasATransferir.stream()
                .map(p -> new PlanillaTransferidaDto(
                        p.getProducto() != null ? p.getProducto() : "—",
                        p.getFechaCorteInformacion() != null
                                ? p.getFechaCorteInformacion().format(FORMATTER_FECHA_CORTE) : "—",
                        p.getEstadoPlanilla() != null ? p.getEstadoPlanilla() : "—",
                        p.getNombreUsuarioCarga() != null ? p.getNombreUsuarioCarga() : "—"))
                .toList();

        return new CambioLiderResultado(transferidasFinal, detalles);
    }

    /** Envía correo de notificación al nuevo líder informando las planillas transferidas. */
    private void enviarCorreoTransferenciaLider(
            String correoNuevoLider,
            String nombreNuevoLider,
            Long idNuevoLider,
            List<Long> idsUsuariosAfectados,
            List<SiproDetalleCargaPlanillas> planillas) {
        try {
            StringBuilder filas = new StringBuilder();

            for (SiproDetalleCargaPlanillas p : planillas) {
                String fechaStr = p.getFechaCorteInformacion() != null
                        ? p.getFechaCorteInformacion().format(FORMATTER_FECHA_CORTE) : "—";

                filas.append("<tr>")
                 .append(tableCell(p.getNombreUsuarioCarga(), "left"))
                 .append(tableCell(p.getProducto(), "center"))
                 .append(tableCell(fechaStr, "center"))
                 .append(tableCell(p.getEstadoPlanilla(), "center"))
                     .append("</tr>");
            }

            Map<String, String> model = new java.util.HashMap<>();
            model.put("banner_url", mailTemplateService.resolveBannerSrc());
            model.put("nombre_lider", nombreNuevoLider);
            model.put("total_planillas", String.valueOf(planillas.size()));
            model.put("detalle_cambio", safeHtml("Revise la siguiente relación y gestione su aprobación en SIPRO."));
            model.put("url_sipro", safeHtml(mailTemplateService.resolveActionUrl()));
            model.put("filas_planillas", filas.toString());

            String html = mailTemplateService.renderPlanillaTemplate("transferencia-lider.html", model);
            MailTemplateNotificationService.EmailPayload payload =
                    new MailTemplateNotificationService.EmailPayload(
                            "SIPRO | Planillas transferidas por cambio de líder", List.of(correoNuevoLider), html);
            mailTemplateService.sendHtml(payload, "transferencia-lider", correoNuevoLider);
            logger.info("Correo único de transferencia de líder enviado a {} para líder {} (id={}) con {} planillas y usuarios afectados={}",
                    correoNuevoLider,
                    nombreNuevoLider,
                    idNuevoLider,
                    planillas.size(),
                    idsUsuariosAfectados);
        } catch (Exception ex) {
            logger.warn("No se pudo enviar correo de transferencia de líder a {}: {}",
                    correoNuevoLider, ex.getMessage());
        }
    }

        private static String tableCell(String text, String align) {
        return "<td style='background:#f3f3f3;border:1px solid #8a8a8a;padding:6px 10px;text-align:" + align + ";'>"
            + safeHtml(text) + "</td>";
        }

    private static String safeHtml(String text) {
        if (text == null) return "—";
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    @Transactional
    public void registrarNuevoUsuario(NuevoUsuarioRequest req, SiproAuthenticatedUser principal) {
        String usuario = normalizarTextoObligatorio(req.getUsuario(), "usuario");
        String correo = normalizarTextoObligatorio(req.getCorreo(), "correo");
        String areaNombre = normalizarTextoObligatorio(req.getAreaNombre(), "área");
        String nombres = normalizarTextoObligatorio(req.getNombres(), "nombres");
        String apellidos = normalizarTextoObligatorio(req.getApellidos(), "apellidos");
        SiproRolesPermisos rolDestino = obtenerRol(req.getIdRol());

        // Segmentos y productos solo son obligatorios para el rol cargador
        LinkedHashSet<Long> idsSegmentos;
        LinkedHashSet<Long> idsProductos;
        if (rolDestino.puedeCargar()) {
            if (req.getIdLider() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El líder aprobador es obligatorio para usuarios cargadores.");
            }
            idsSegmentos = normalizarIds(req.getIdsSegmentos());
            idsProductos = normalizarIds(req.getIdsProductos());
            if (idsSegmentos.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe seleccionar al menos un segmento para el usuario cargador.");
            }
            if (idsProductos.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe seleccionar al menos un producto para el usuario cargador.");
            }
        } else {
            // Aprobador, admin u otro: productos y segmentos no aplican
            idsSegmentos = new LinkedHashSet<>();
            idsProductos = new LinkedHashSet<>();
        }

        // Validación de unicidad
        if (loginRepo.findByUsuarioIgnoreCase(usuario).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con login '" + usuario + "'.");
        }
        if (personaRepo.existsByCorreoIgnoreCase(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con correo '" + correo + "'.");
        }

        Map<Long, Producto> productosPorId = cargarProductos(idsProductos);
        if (!idsProductos.isEmpty()) {
            validarProductosPorSegmento(productosPorId.values(), idsSegmentos);
        }

        UsuarioLogin login = new UsuarioLogin(usuario, null);
        login = loginRepo.save(login);

        UsuarioPersona persona = new UsuarioPersona();
        persona.setUsuarioLogin(login);
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        persona.setCorreo(correo);
        persona.setUsuario(usuario);
        login.setPersona(persona);
        entityManager.persist(persona);

        // Resolver líder según el tipo de rol
        final UsuarioPersona liderAsignado;
        if (esRolLider(rolDestino)) {
            // Aprobador / admin: es su propio líder
            liderAsignado = persona;
        } else if (rolDestino.puedeCargar()) {
            // Cargador: líder explícito enviado desde el frontend
            Long idLiderReq = req.getIdLider();
            liderAsignado = personaRepo.findById(idLiderReq)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "El líder con id " + idLiderReq + " no existe en SIPRO."));
        } else {
            // Cualquier otro rol: usar heurística por área
            liderAsignado = resolverLiderParaNuevoUsuario(areaNombre, principal);
        }

        UsuarioArea area = new UsuarioArea();
        area.setUsuarioLogin(login);
        area.setAreaNombre(areaNombre);
        area.setJefe(liderAsignado);
        area.setCargoJefe(esRolLider(rolDestino)
                ? resolverEtiquetaRol(rolDestino)
                : resolverEtiquetaLider(liderAsignado.getIdUsuario()));
        login.setArea(area);
        entityManager.persist(area);

        List<SiproUsuarioProductoRol> asignaciones = new ArrayList<>();
        for (Producto producto : productosPorId.values()) {
            SiproUsuarioProductoRol upr = new SiproUsuarioProductoRol(
                    persona.getIdUsuario(), producto.getIdProducto(), rolDestino.getIdRol(), producto.getIdSegmento(), (short) 1);
            asignaciones.add(upr);
        }
        if (!asignaciones.isEmpty()) {
            uprRepo.saveAll(asignaciones);
        }
        logger.info("Nuevo usuario '{}' registrado con id {} y {} asignaciones de producto",
                usuario, persona.getIdUsuario(), asignaciones.size());
    }

    // ── Productos ─────────────────────────────────────────────────────────

    public List<Producto> listarProductos() {
        return productoRepo.findAllByOrderByTituloAsc();
    }

    @Transactional
    public Producto crearProducto(ProductoRequest req) {
        ProductoRequest productoNormalizado = normalizarProductoRequest(req);
        // Unicidad: (nombreArchivoPermitido, idSegmento) como llave de negocio
        productoRepo.findByNombreArchivoPermitidoAndIdSegmento(
                productoNormalizado.getNombreArchivoPermitido(),
                Long.valueOf(productoNormalizado.getIdSegmento()))
            .ifPresent(existente -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe el producto «ert;" + existente.getTitulo() + "&gt;" +
                " con ese nombre de archivo en ese segmento (id=" + existente.getIdProducto() + ")."); });
        Producto p = new Producto();
        aplicarProductoRequest(p, productoNormalizado);
        p.setCreadoEn(java.time.LocalDateTime.now());
        return productoRepo.save(p);
    }

    @Transactional
    public Producto actualizarProducto(Long idProducto, ProductoRequest req) {
        ProductoRequest productoNormalizado = normalizarProductoRequest(req);
        Producto p = productoRepo.findById(idProducto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto " + idProducto + " no encontrado."));
        // Unicidad al editar: otro producto diferente no puede tener la misma llave
        productoRepo.findByNombreArchivoPermitidoAndIdSegmento(
                productoNormalizado.getNombreArchivoPermitido(),
                Long.valueOf(productoNormalizado.getIdSegmento()))
            .filter(existente -> !existente.getIdProducto().equals(idProducto))
            .ifPresent(existente -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe el producto &laquo;" + existente.getTitulo() + "&raquo;" +
                " con ese nombre de archivo en ese segmento (id=" + existente.getIdProducto() + ")."); });
        aplicarProductoRequest(p, productoNormalizado);
        return productoRepo.save(p);
    }

    // ── Catálogos ─────────────────────────────────────────────────────────

    public List<Segmento> listarSegmentos() {
        return segmentoRepo.findAllByOrderByNombreAsc();
    }

    public List<SiproRolesPermisos> listarRoles() {
        return rolesRepo.findAll();
    }

    // ── DTOs internos ─────────────────────────────────────────────────────

    public record MesDisponibleDto(String valor, String label) {}

    public record UsuarioResumenDto(
            Long idUsuario, String usuario, String nombres, String apellidos,
            String nombreCompleto, String correo, String areaNombre, String cargoJefe,
            Long idLider, String nombreLider, Integer idRolActual, String nombreRolActual,
            String grupoAdEsperado,
            List<Long> segmentos) {}

    public record AsignacionDto(Integer idRol, List<ProductosPorSegmentoDto> productosPorSegmento) {}

    public record ProductosPorSegmentoDto(Long idSegmento, List<Long> idsProductosPermitidos) {}

    public record PendientesDto(boolean tienePendientes, long cantidad) {}

    /** Detalle de una planilla transferida al nuevo líder. */
    public record PlanillaTransferidaDto(
            String producto,
            String fechaCorte,
            String estadoPlanilla,
            String nombreCargador) {}

    /** Resultado de la operación de cambio masivo de líder. */
    public record CambioLiderResultado(int totalTransferidas, List<PlanillaTransferidaDto> detalles) {}

    // ── Request POJOs ─────────────────────────────────────────────────────

    public static class ExcepcionRequest {
        private String periodoValoracion;
        private String fechaAperturaOverride;
        private String horaAperturaOverride;
        private String fechaCierreOverride;
        private String horaCierreOverride;
        private String motivo;

        public String getPeriodoValoracion() { return periodoValoracion; }
        public void setPeriodoValoracion(String v) { this.periodoValoracion = v; }
        public String getFechaAperturaOverride() { return fechaAperturaOverride; }
        public void setFechaAperturaOverride(String v) { this.fechaAperturaOverride = v; }
        public String getHoraAperturaOverride() { return horaAperturaOverride; }
        public void setHoraAperturaOverride(String v) { this.horaAperturaOverride = v; }
        public String getFechaCierreOverride() { return fechaCierreOverride; }
        public void setFechaCierreOverride(String v) { this.fechaCierreOverride = v; }
        public String getHoraCierreOverride() { return horaCierreOverride; }
        public void setHoraCierreOverride(String v) { this.horaCierreOverride = v; }
        public String getMotivo() { return motivo; }
        public void setMotivo(String v) { this.motivo = v; }
    }

    public static class AsignacionRequest {
        private Integer idRol;
        private List<ProductosPorSegmentoRequest> productosPorSegmento;

        public Integer getIdRol() { return idRol; }
        public void setIdRol(Integer v) { this.idRol = v; }
        public List<ProductosPorSegmentoRequest> getProductosPorSegmento() { return productosPorSegmento; }
        public void setProductosPorSegmento(List<ProductosPorSegmentoRequest> v) { this.productosPorSegmento = v; }
    }

    public static class ProductosPorSegmentoRequest {
        private Long idSegmento;
        private List<Long> idsProductosPermitidos;

        public Long getIdSegmento() { return idSegmento; }
        public void setIdSegmento(Long v) { this.idSegmento = v; }
        public List<Long> getIdsProductosPermitidos() { return idsProductosPermitidos; }
        public void setIdsProductosPermitidos(List<Long> v) { this.idsProductosPermitidos = v; }
    }

    public static class CambioLiderRequest {
        private List<Long> idsUsuariosAfectados;
        private Long idNuevoLider;

        public List<Long> getIdsUsuariosAfectados() { return idsUsuariosAfectados; }
        public void setIdsUsuariosAfectados(List<Long> v) { this.idsUsuariosAfectados = v; }
        public Long getIdNuevoLider() { return idNuevoLider; }
        public void setIdNuevoLider(Long v) { this.idNuevoLider = v; }
    }

    public static class NuevoUsuarioRequest {
        private String nombres;
        private String apellidos;
        private String correo;
        private String usuario;
        private String areaNombre;
        private Integer idRol;
        private List<Long> idsSegmentos;
        private List<Long> idsProductos;
        /** Obligatorio cuando el rol es cargador. ID del líder aprobador existente en SIPRO. */
        private Long idLider;

        public String getNombres() { return nombres; }
        public void setNombres(String v) { this.nombres = v; }
        public String getApellidos() { return apellidos; }
        public void setApellidos(String v) { this.apellidos = v; }
        public String getCorreo() { return correo; }
        public void setCorreo(String v) { this.correo = v; }
        public String getUsuario() { return usuario; }
        public void setUsuario(String v) { this.usuario = v; }
        public String getAreaNombre() { return areaNombre; }
        public void setAreaNombre(String v) { this.areaNombre = v; }
        public Integer getIdRol() { return idRol; }
        public void setIdRol(Integer v) { this.idRol = v; }
        public List<Long> getIdsSegmentos() { return idsSegmentos; }
        public void setIdsSegmentos(List<Long> v) { this.idsSegmentos = v; }
        public List<Long> getIdsProductos() { return idsProductos; }
        public void setIdsProductos(List<Long> v) { this.idsProductos = v; }
        public Long getIdLider() { return idLider; }
        public void setIdLider(Long v) { this.idLider = v; }
    }

    public static class ProductoRequest {
        private String titulo;
        private Integer idSegmento;
        private boolean activo;
        private String nombreArchivoPermitido;
        private String nombreControlPermitido;

        public String getTitulo() { return titulo; }
        public void setTitulo(String v) { this.titulo = v; }
        public Integer getIdSegmento() { return idSegmento; }
        public void setIdSegmento(Integer v) { this.idSegmento = v; }
        public boolean isActivo() { return activo; }
        public void setActivo(boolean v) { this.activo = v; }
        public String getNombreArchivoPermitido() { return nombreArchivoPermitido; }
        public void setNombreArchivoPermitido(String v) { this.nombreArchivoPermitido = v; }
        public String getNombreControlPermitido() { return nombreControlPermitido; }
        public void setNombreControlPermitido(String v) { this.nombreControlPermitido = v; }
    }

    // ── Utilidades privadas ───────────────────────────────────────────────

    private SiproExcepcionVentanaCarga buildExcepcion(ExcepcionRequest req, LocalDate periodo, Long idUsuario) {
        SiproExcepcionVentanaCarga exc = new SiproExcepcionVentanaCarga();
        exc.setPeriodoValoracion(periodo);
        actualizarCampos(exc, req);
        exc.setModificadoPorId(idUsuario);
        exc.setModificadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        return exc;
    }

    private void actualizarCampos(SiproExcepcionVentanaCarga exc, ExcepcionRequest req) {
        exc.setFechaAperturaOverride(req.getFechaAperturaOverride() != null
                ? parseLocalDate(req.getFechaAperturaOverride()) : null);
        exc.setHoraAperturaOverride(req.getHoraAperturaOverride() != null
                ? LocalTime.parse(req.getHoraAperturaOverride()) : null);
        exc.setFechaCierreOverride(req.getFechaCierreOverride() != null
                ? parseLocalDate(req.getFechaCierreOverride()) : null);
        exc.setHoraCierreOverride(req.getHoraCierreOverride() != null
                ? LocalTime.parse(req.getHoraCierreOverride()) : null);
        exc.setMotivo(req.getMotivo());
    }

    private void aplicarProductoRequest(Producto producto, ProductoRequest req) {
        producto.setTitulo(req.getTitulo());
        producto.setIdSegmento(Long.valueOf(req.getIdSegmento()));
        producto.setActivo(req.isActivo() ? 1 : 0);
        producto.setNombreArchivoPermitido(req.getNombreArchivoPermitido());
        producto.setNombreControlPermitido(req.getNombreControlPermitido());
    }

    private ProductoRequest normalizarProductoRequest(ProductoRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud del producto es obligatoria.");
        }

        Integer idSegmento = req.getIdSegmento();
        if (idSegmento == null || idSegmento <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un segmento válido.");
        }

        String titulo = normalizarTituloProducto(Optional.ofNullable(req.getTitulo()).orElse(""));
        String nombreArchivoPermitido = Optional.ofNullable(req.getNombreArchivoPermitido())
            .orElse("")
            .trim()
            .toUpperCase(Locale.ROOT);
        String nombreControlPermitido = Optional.ofNullable(req.getNombreControlPermitido())
            .orElse("")
            .trim()
            .toUpperCase(Locale.ROOT);
        boolean requiereControl = Objects.equals(Long.valueOf(idSegmento), ID_SEGMENTO_FULL_IFRS);

        if (titulo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título del producto es obligatorio.");
        }

        if (nombreArchivoPermitido.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del archivo permitido es obligatorio.");
        }

        if (!nombreArchivoPermitido.endsWith(SUFIJO_MASCARA_FECHA)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El Nombre Archivo Permitido debe terminar estrictamente en " + SUFIJO_MASCARA_FECHA + ".");
        }

        // Formato: solo letras, dígitos y GUION SIMPLE entre palabras (TEXT_TEXT, nunca TEXT__TEXT)
        // El patrón exige que entre segmentos haya exactamente un guion bajo.
        if (!nombreArchivoPermitido.matches("^[A-Z0-9]+(_[A-Z0-9]+)*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El Nombre Archivo Permitido solo admite letras mayúsculas, dígitos y guiones bajos simples " +
                    "entre palabras (TEXT_TEXT). No se permiten guiones bajos dobles ni consecutivos.");
        }

        if (requiereControl) {
            if (nombreControlPermitido.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El Nombre Control Permitido es obligatorio para Full IFRS.");
            }
            if (!nombreControlPermitido.startsWith(PREFIJO_NOMBRE_CONTROL)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El Nombre Control Permitido debe iniciar estrictamente con " + PREFIJO_NOMBRE_CONTROL + ".");
            }
            if (!nombreControlPermitido.endsWith(SUFIJO_MASCARA_FECHA)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El Nombre Control Permitido debe terminar estrictamente en " + SUFIJO_MASCARA_FECHA + ".");
            }
            // Mismo patrón de guion simple (CTRL- es el prefijo fijo con guión medio, el resto va con guión bajo)
            if (!nombreControlPermitido.matches("^CTRL-[A-Z0-9]+(_[A-Z0-9]+)*$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El Nombre Control Permitido solo admite letras mayúsculas, dígitos y guiones bajos simples " +
                        "entre palabras (CTRL-TEXT_TEXT). No se permiten guiones bajos dobles ni consecutivos.");
            }
        } else {
            nombreControlPermitido = "";
        }

        req.setTitulo(titulo);
        req.setIdSegmento(idSegmento);
        req.setNombreArchivoPermitido(nombreArchivoPermitido);
        req.setNombreControlPermitido(nombreControlPermitido);
        return req;
    }

    private String normalizarTituloProducto(String valor) {
        String texto = Optional.ofNullable(valor).orElse("").stripLeading();
        if (texto.isBlank()) {
            return "";
        }

        String[] tokens = texto.split("\\s+");
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < tokens.length; i++) {
            joiner.add(normalizarTokenTituloProducto(tokens[i], i == 0));
        }
        return joiner.toString();
    }

    private String normalizarTokenTituloProducto(String token, boolean primerToken) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.matches("[\\p{Lu}0-9]{2,4}")) {
            return token;
        }

        String tokenNormalizado = token.toLowerCase(Locale.ROOT);
        if (!primerToken) {
            return tokenNormalizado;
        }
        return Character.toUpperCase(tokenNormalizado.charAt(0)) + tokenNormalizado.substring(1);
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha vacía o nula.");
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de fecha inválido: " + s);
        }
    }

    private SiproRolesPermisos obtenerRol(Integer idRol) {
        if (idRol == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar el rol.");
        }
        return rolesRepo.findById(idRol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rol " + idRol + " no encontrado."));
    }

    private Map<Long, Producto> cargarProductos(Collection<Long> idsProductos) {
        if (idsProductos == null || idsProductos.isEmpty()) {
            return Map.of();
        }

        List<Producto> productos = productoRepo.findAllById(idsProductos);
        Map<Long, Producto> productosPorId = productos.stream()
                .collect(Collectors.toMap(Producto::getIdProducto, producto -> producto, (left, right) -> left, LinkedHashMap::new));

        List<Long> faltantes = idsProductos.stream()
                .filter(id -> !productosPorId.containsKey(id))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existen los productos " + faltantes + ".");
        }

        return productosPorId;
    }

    private void validarCambioRolPermitido(Long idUsuario, Integer idRolActual, SiproRolesPermisos rolDestino) {
        if (idRolActual == null || Objects.equals(idRolActual, rolDestino.getIdRol())) {
            return;
        }

        SiproRolesPermisos rolActual = obtenerRol(idRolActual);

        if (rolActual.puedeCargar()) {
            long pendientesCarga = planillasRepo.countByIdUsuarioCargaAndEstadoPlanillaAndActivoTrue(idUsuario, ESTADO_PENDIENTE);
            if (pendientesCarga > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No es posible cambiar el rol mientras el usuario cargador tenga planillas pendientes.");
            }
        }

        long pendientesComoLider = planillasRepo.countByIdLiderAndEstadoPlanillaAndActivoTrue(idUsuario, ESTADO_PENDIENTE);
        if (pendientesComoLider > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No es posible cambiar el rol mientras el usuario tenga planillas pendientes como líder aprobador.");
        }
    }

    private List<ProductosPorSegmentoRequest> normalizarProductosAsignacion(
            AsignacionRequest req,
            List<SiproUsuarioProductoRol> actuales,
            SiproRolesPermisos rolDestino) {

        List<ProductosPorSegmentoRequest> solicitados = Optional.ofNullable(req.getProductosPorSegmento()).orElse(List.of());
        if (rolDestino.puedeCargar() || !solicitados.isEmpty()) {
            return solicitados;
        }

        Map<Long, LinkedHashSet<Long>> porSegmento = new LinkedHashMap<>();
        for (SiproUsuarioProductoRol actual : actuales) {
            if (actual.getProducto() == null || actual.getProducto().getIdProducto() == null || actual.getIdSegmento() == null) {
                continue;
            }

            porSegmento
                    .computeIfAbsent(actual.getIdSegmento(), ignored -> new LinkedHashSet<>())
                    .add(actual.getProducto().getIdProducto());
        }

        return porSegmento.entrySet().stream()
                .map(entry -> {
                    ProductosPorSegmentoRequest productosPorSegmento = new ProductosPorSegmentoRequest();
                    productosPorSegmento.setIdSegmento(entry.getKey());
                    productosPorSegmento.setIdsProductosPermitidos(new ArrayList<>(entry.getValue()));
                    return productosPorSegmento;
                })
                .toList();
    }

    private void validarProductosPorSegmento(Collection<Producto> productos, Set<Long> idsSegmentos) {
        List<Long> fueraDeSegmento = productos.stream()
                .filter(producto -> producto.getIdSegmento() == null || !idsSegmentos.contains(producto.getIdSegmento()))
                .map(Producto::getIdProducto)
                .toList();
        if (!fueraDeSegmento.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los productos " + fueraDeSegmento + " no pertenecen a los segmentos seleccionados.");
        }
    }

    private LinkedHashSet<Long> normalizarIds(Collection<Long> ids) {
        LinkedHashSet<Long> normalizados = new LinkedHashSet<>();
        if (ids == null) {
            return normalizados;
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalizados.add(id);
            }
        }
        return normalizados;
    }

    private String normalizarTextoObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    private UsuarioPersona resolverLiderParaNuevoUsuario(String areaNombre, SiproAuthenticatedUser principal) {
        List<SiproRolesPermisos> roles = rolesRepo.findAll();
        Set<Integer> rolesLider = roles.stream()
                .filter(this::esRolLider)
                .map(SiproRolesPermisos::getIdRol)
                .collect(Collectors.toSet());

        Optional<Long> candidatoMismaArea = listarUsuarios().stream()
                .filter(usuario -> mismaArea(usuario.areaNombre(), areaNombre))
                .filter(usuario -> usuario.idRolActual() != null && rolesLider.contains(usuario.idRolActual()))
                .map(UsuarioResumenDto::idUsuario)
                .findFirst();

        if (candidatoMismaArea.isPresent()) {
            return personaRepo.findById(candidatoMismaArea.get())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No fue posible resolver el líder del área seleccionada."));
        }

        if (principal != null && principal.idUsuario() != null) {
            return personaRepo.findById(principal.idUsuario())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No fue posible resolver el administrador autenticado como líder de respaldo."));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No fue posible resolver un líder activo para el área seleccionada.");
    }

    private boolean mismaArea(String areaActual, String areaObjetivo) {
        if (areaActual == null || areaObjetivo == null) {
            return false;
        }
        return areaActual.trim().equalsIgnoreCase(areaObjetivo.trim());
    }

    private boolean esRolLider(SiproRolesPermisos rol) {
        if (rol == null) {
            return false;
        }
        return rol.puedeAprobar() || (rol.getModificarParametros() != null && rol.getModificarParametros() == 1);
    }

    private String resolverEtiquetaLider(Long idUsuario) {
        List<SiproUsuarioProductoRol> asignaciones = uprRepo.findActiveByUsuario(idUsuario);
        Optional<SiproRolesPermisos> rolLider = asignaciones.stream()
                .map(SiproUsuarioProductoRol::getRol)
                .filter(Objects::nonNull)
                .filter(this::esRolLider)
                .findFirst();

        if (rolLider.isPresent()) {
            return resolverEtiquetaRol(rolLider.get());
        }

        return asignaciones.stream()
                .map(SiproUsuarioProductoRol::getRol)
                .filter(Objects::nonNull)
                .map(this::resolverEtiquetaRol)
                .findFirst()
                .orElse(ROL_LIDER_POR_DEFECTO);
    }

    private String resolverEtiquetaRol(SiproRolesPermisos rol) {
        if (rol == null) {
            return ROL_LIDER_POR_DEFECTO;
        }
        if (rol.getPerfil() != null && !rol.getPerfil().isBlank()) {
            return rol.getPerfil().trim();
        }
        if (rol.getRol() != null && !rol.getRol().isBlank()) {
            return rol.getRol().trim();
        }
        return ROL_LIDER_POR_DEFECTO;
    }

    private String construirNombreCompleto(UsuarioPersona persona) {
        String nombres = persona.getNombres() != null ? persona.getNombres().trim() : "";
        String apellidos = persona.getApellidos() != null ? persona.getApellidos().trim() : "";
        String nombreCompleto = (nombres + " " + apellidos).trim();
        return nombreCompleto.isBlank() ? ROL_LIDER_POR_DEFECTO : nombreCompleto;
    }
}