INSERT INTO public.sipro_parametros_unico (
    clave,
    valor,
    tipo,
    descripcion
)
VALUES (
    'IFRS_PLANILLAS_RUTA_SALIDA',
    $$\\Sbcldwpifw01\IFRS9_Planillas_Manuales$$,
    'STRING',
    'Ruta de red compartida donde se publican las planillas manuales aprobadas del segmento Full IFRS.'
)
ON CONFLICT (clave) DO UPDATE
SET
    valor = EXCLUDED.valor,
    tipo = EXCLUDED.tipo,
    descripcion = EXCLUDED.descripcion,
    modificado_en = CURRENT_TIMESTAMP;