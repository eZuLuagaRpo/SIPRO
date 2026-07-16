package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;

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
}