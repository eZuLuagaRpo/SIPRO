-- Almacena un snapshot de las métricas del CREFFSOS generado al momento de la consolidación.
-- Permite que el resumen consolidado muestre la comparación correcta por periodo
-- sin depender del archivo físico en storage (que puede ser sobreescrito o eliminado).
ALTER TABLE public.sipro_detalle_consolidaciones_planillas
    ADD COLUMN IF NOT EXISTS creffsos_cantidad_registros BIGINT,
    ADD COLUMN IF NOT EXISTS creffsos_total_vlriniobl NUMERIC(18,2);
