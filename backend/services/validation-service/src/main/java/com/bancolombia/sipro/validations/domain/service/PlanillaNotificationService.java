package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato para enviar notificaciones del ciclo de vida de una planilla.
 */
public interface PlanillaNotificationService {

    /**
     * Notifica que una planilla fue enviada a aprobación.
     */
    void notificarSolicitud(SiproDetalleCargaPlanillas planilla);

    /**
     * Notifica que una planilla fue aprobada.
     */
    void notificarAprobacion(SiproDetalleCargaPlanillas planilla);

    /**
     * Notifica que una planilla fue rechazada junto con su motivo.
     */
    void notificarRechazo(SiproDetalleCargaPlanillas planilla, String motivoRechazo);

    /**
     * Notifica a un usuario (cargador o aprobador) que uno o más productos
     * incumplieron la carga o aprobación de planilla manual en el periodo indicado.
     */
    void notificarIncumplimiento(String nombreDestinatario,
                                 String correoDestinatario,
                                 List<String> productosIncumplidos,
                                 String segmento,
                                 LocalDate fechaCorte);
}