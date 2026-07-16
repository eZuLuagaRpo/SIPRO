package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.infrastructure.config.MailNotificationProperties;
import com.bancolombia.sipro.validations.infrastructure.notification.MailTemplateNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioPersonaRepository;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionConsolidacionServiceTest {

        @SuppressWarnings("unchecked")
        private static ArgumentCaptor<Map<String, String>> captorMapStringString() {
                return (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        }

    @Mock
    private SiproUsuarioProductoRolRepository usuarioProductoRolRepository;

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;

    @Mock
    private UsuarioPersonaRepository usuarioPersonaRepository;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private MailTemplateNotificationService mailTemplateNotificationService;

    private MailNotificationProperties mailNotificationProperties;
    private NotificacionConsolidacionService service;

    @BeforeEach
    void setUp() {
        mailNotificationProperties = new MailNotificationProperties();
        service = new NotificacionConsolidacionService(
                usuarioProductoRolRepository,
                consolidacionRepository,
                consolidacionArchivoRepository,
                usuarioPersonaRepository,
                parametroUnicoService,
                mailNotificationProperties,
                mailTemplateNotificationService
        );
    }

    @Test
    void deberiaEnviarCorreoDeConsolidacionCompletadaAAdminsFuncionalesMasEjecutor() {
        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(55L);
        cabecera.setPeriodoValoracion(LocalDate.of(2026, 5, 31));
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setCantidadArchivosConsolidados(2);
        cabecera.setCantidadRegistrosConsolidados(150);
        cabecera.setCreadoPorId(3L);
        cabecera.setFechaHoraFin(OffsetDateTime.parse("2026-05-26T16:25:00-05:00"));

        UsuarioPersona ejecutor = new UsuarioPersona();
        ejecutor.setIdUsuario(3L);
        ejecutor.setNombres("Junior");
        ejecutor.setApellidos("Ortiz");
        ejecutor.setCorreo("junortiz@bancolombia.com.co");
        ejecutor.setUsuario("junortiz");

        SiproDetalleConsolidacionArchivo archivoA = new SiproDetalleConsolidacionArchivo();
        archivoA.setNombreArchivo("FACTORING_20260531.xlsx");
        archivoA.setCantidadRegistrosArchivo(1);
        SiproDetalleConsolidacionArchivo archivoB = new SiproDetalleConsolidacionArchivo();
        archivoB.setNombreArchivo("HIPOTECARIO_20260531.xlsx");
        archivoB.setCantidadRegistrosArchivo(1);

        when(consolidacionRepository.findById(55L)).thenReturn(Optional.of(cabecera));
        when(usuarioProductoRolRepository.findDistinctActiveEmailsByRolId(6))
                .thenReturn(List.of("admin.funcional1@bancolombia.com.co", "admin.funcional2@bancolombia.com.co"));
        when(usuarioPersonaRepository.findById(3L)).thenReturn(Optional.of(ejecutor));
        when(consolidacionArchivoRepository.findByIdConsolidacionOrderByIdCargaPlanillaAsc(55L))
                .thenReturn(List.of(archivoA, archivoB));
        when(parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "")).thenReturn("\\\\share\\creffsos");
        when(mailTemplateNotificationService.resolveBannerSrc()).thenReturn("cid:sipro-banner");
        when(mailTemplateNotificationService.renderPlanillaTemplate(eq("consolidacion-completada.html"), any()))
                .thenReturn("<html>ok</html>");
        when(mailTemplateNotificationService.sendHtml(any(), eq("consolidación completada"), eq("consolidacion 55")))
                .thenReturn(MailTemplateNotificationService.DeliveryResult.sent("smtp"));

        service.enviarConfirmacion(55L);

        ArgumentCaptor<Map<String, String>> modelCaptor = captorMapStringString();
        verify(mailTemplateNotificationService).renderPlanillaTemplate(eq("consolidacion-completada.html"), modelCaptor.capture());
        Map<String, String> model = modelCaptor.getValue();
        assertEquals("Mayo 2026 (2026-05-31)", model.get("periodo_titulo"));
        assertEquals("Junior Ortiz (junortiz)", model.get("ejecutado_por"));
        assertTrue(model.get("archivos_rows").contains("FACTORING_20260531.xlsx"));
        assertTrue(model.get("archivos_rows").contains("HIPOTECARIO_20260531.xlsx"));

        ArgumentCaptor<MailTemplateNotificationService.EmailPayload> emailCaptor = ArgumentCaptor.forClass(MailTemplateNotificationService.EmailPayload.class);
        verify(mailTemplateNotificationService).sendHtml(emailCaptor.capture(), eq("consolidación completada"), eq("consolidacion 55"));
        MailTemplateNotificationService.EmailPayload payload = emailCaptor.getValue();
        assertEquals("SIPRO - Consolidación completada: Mayo 2026", payload.subject());
        assertEquals(List.of(
                "admin.funcional1@bancolombia.com.co",
                "admin.funcional2@bancolombia.com.co",
                "junortiz@bancolombia.com.co"
        ), payload.recipients());
    }

    @Test
    void noDeberiaEnviarCorreoSiNoHayDestinatariosConfigurados() {
        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(56L);
        cabecera.setPeriodoValoracion(LocalDate.of(2026, 5, 31));
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setCreadoPorId(3L);

        when(consolidacionRepository.findById(56L)).thenReturn(Optional.of(cabecera));
        when(usuarioProductoRolRepository.findDistinctActiveEmailsByRolId(6)).thenReturn(List.of());
        when(usuarioPersonaRepository.findById(3L)).thenReturn(Optional.of(new UsuarioPersona()));

        service.enviarConfirmacion(56L);

        verify(mailTemplateNotificationService, never()).renderPlanillaTemplate(any(), any());
        verify(mailTemplateNotificationService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void deberiaEnviarCorreoDeEliminacionConActorPrincipalAunqueNoExistaUsuarioPersona() {
        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(88L);
        cabecera.setPeriodoValoracion(LocalDate.of(2026, 5, 31));

        SiproAuthenticatedUser principal = new SiproAuthenticatedUser(
                9L,
                "daeorti",
                "Daniella Escobar",
                "daeorti",
                "daeorti@bancolombia.com.co",
                null,
                Set.of(),
                false
        );

        when(usuarioProductoRolRepository.findDistinctActiveEmailsByRolId(6))
                .thenReturn(List.of("admin.funcional1@bancolombia.com.co"));
        when(usuarioPersonaRepository.findById(9L)).thenReturn(Optional.empty());
        when(mailTemplateNotificationService.resolveBannerSrc()).thenReturn("cid:sipro-banner");
        when(mailTemplateNotificationService.renderPlanillaTemplate(eq("consolidacion-eliminada.html"), any()))
                .thenReturn("<html>ok</html>");
        when(mailTemplateNotificationService.sendHtml(any(), eq("consolidación eliminada"), eq("consolidacion-eliminada-88")))
                .thenReturn(MailTemplateNotificationService.DeliveryResult.skipped("mail deshabilitado"));

        service.notificarEliminacion(cabecera, "Error en datos base", principal, 150L, 2, OffsetDateTime.parse("2026-05-27T10:30:00-05:00"));

        ArgumentCaptor<Map<String, String>> modelCaptor = captorMapStringString();
        verify(mailTemplateNotificationService).renderPlanillaTemplate(eq("consolidacion-eliminada.html"), modelCaptor.capture());
        Map<String, String> model = modelCaptor.getValue();
        assertEquals("Daniella Escobar (daeorti)", model.get("eliminado_por"));
        assertEquals("150", model.get("registros_eliminados"));
        assertEquals("2", model.get("archivos_eliminados"));

        ArgumentCaptor<MailTemplateNotificationService.EmailPayload> emailCaptor = ArgumentCaptor.forClass(MailTemplateNotificationService.EmailPayload.class);
        verify(mailTemplateNotificationService).sendHtml(emailCaptor.capture(), eq("consolidación eliminada"), eq("consolidacion-eliminada-88"));
        assertEquals(List.of("admin.funcional1@bancolombia.com.co", "daeorti@bancolombia.com.co"), emailCaptor.getValue().recipients());
    }

        @Test
        void deberiaEnviarCorreoDeConsolidacionFallidaConDetalleDelError() {
                SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
                cabecera.setIdConsolidacion(57L);
                cabecera.setPeriodoValoracion(LocalDate.of(2026, 5, 31));
                cabecera.setEstadoConsolidacion("ERROR");
                cabecera.setCantidadArchivosConsolidados(1);
                cabecera.setCantidadRegistrosConsolidados(25);
                cabecera.setCreadoPorId(3L);
                cabecera.setFechaHoraFin(OffsetDateTime.parse("2026-05-27T11:05:00-05:00"));
                cabecera.setMensajeError("IllegalStateException: fallo escribiendo CONSOLIDADO_2026-05-31.xlsx");
                cabecera.setObservacion("Reintento manual después de limpieza parcial");

                UsuarioPersona ejecutor = new UsuarioPersona();
                ejecutor.setIdUsuario(3L);
                ejecutor.setNombres("Junior");
                ejecutor.setApellidos("Ortiz");
                ejecutor.setCorreo("junortiz@bancolombia.com.co");
                ejecutor.setUsuario("junortiz");

                when(consolidacionRepository.findById(57L)).thenReturn(Optional.of(cabecera));
                when(usuarioProductoRolRepository.findDistinctActiveEmailsByRolId(6))
                                .thenReturn(List.of("admin.funcional1@bancolombia.com.co"));
                when(usuarioPersonaRepository.findById(3L)).thenReturn(Optional.of(ejecutor));
                when(mailTemplateNotificationService.resolveBannerSrc()).thenReturn("cid:sipro-banner");
                when(mailTemplateNotificationService.renderPlanillaTemplate(eq("consolidacion-fallida.html"), any()))
                                .thenReturn("<html>error</html>");
                when(mailTemplateNotificationService.sendHtml(any(), eq("consolidación fallida"), eq("consolidacion-error-57")))
                                .thenReturn(MailTemplateNotificationService.DeliveryResult.sent("smtp"));

                MailTemplateNotificationService.DeliveryResult resultado = service.notificarError(57L);

                assertTrue(resultado.sent());

                ArgumentCaptor<Map<String, String>> modelCaptor = captorMapStringString();
                verify(mailTemplateNotificationService).renderPlanillaTemplate(eq("consolidacion-fallida.html"), modelCaptor.capture());
                Map<String, String> model = modelCaptor.getValue();
                assertEquals("Mayo 2026 (2026-05-31)", model.get("periodo_titulo"));
                assertEquals("Junior Ortiz (junortiz)", model.get("ejecutado_por"));
                assertTrue(model.get("mensaje_error").contains("fallo escribiendo CONSOLIDADO_2026-05-31.xlsx"));
                assertTrue(model.get("observacion").contains("Reintento manual"));

                ArgumentCaptor<MailTemplateNotificationService.EmailPayload> emailCaptor = ArgumentCaptor.forClass(MailTemplateNotificationService.EmailPayload.class);
                verify(mailTemplateNotificationService).sendHtml(emailCaptor.capture(), eq("consolidación fallida"), eq("consolidacion-error-57"));
                MailTemplateNotificationService.EmailPayload payload = emailCaptor.getValue();
                assertEquals("SIPRO - Consolidación fallida: Mayo 2026", payload.subject());
                assertEquals(List.of("admin.funcional1@bancolombia.com.co", "junortiz@bancolombia.com.co"), payload.recipients());
                assertFalse(payload.htmlBody().isBlank());
        }
}
