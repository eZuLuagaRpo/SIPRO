INSERT INTO public.sipro_parametros_unico (
    clave,
    valor,
    tipo,
    descripcion
)
VALUES (
    'ARCHIVOS_BLOQUEADOS_RUTA_SALIDA',
    '\\SBMDEBNS03\BFT\SIPRO\ArchivosBloqueados',
    'STRING',
    'Ruta de red compartida donde se publican copias protegidas contra edicion (CREFFSOS xlsx, planillas manuales aprobadas de Full IFRS y sus archivos de control convertidos a Word), organizadas en subcarpetas por periodo y comprimidas al cierre de cada periodo.'
)
ON CONFLICT (clave) DO UPDATE
SET
    valor = EXCLUDED.valor,
    tipo = EXCLUDED.tipo,
    descripcion = EXCLUDED.descripcion,
    modificado_en = CURRENT_TIMESTAMP;