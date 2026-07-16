-- =============================================================================
-- 016-creffsos-parametros-update.sql
-- Sincroniza sipro_parametros_columnas_creffsos con la definicion canonica
-- del archivo 009-creffsos-parametrization.sql.
--
-- CAMBIOS FUNCIONALES:
--   - CTAPUC       : agrega funcion_java='resolverCuentaBankvision' y
--                    parametros_json completo para lookup en sipro_parametros_homologacion_colgaap.
--   - CLASEGTIA    : agrega funcion_java='resolverClaseGarantiaDesdeCenie' y
--                    parametros_json con filtros LZ/Impala.
--   - CALIFICPUC   : agrega funcion_java='resolverCalificacionDesdeCenie' y
--                    parametros_json con filtros LZ/Impala.
--   - CLASIFCPUC   : agrega array 'tiposClasificacionUno' al parametros_json.
--
-- NOTA TIPO DATO (confirmar con equipo BankVision antes de ejecutar en prod):
--   Los campos TASAINTVIG, TASAINTMRA, TASAREDESC, PORCRDSCTO, SALDOMES y
--   PROVCAPANT..GARCREDITO cambian de NUMERIC a INTEGER segun la definicion
--   canonica. Esto cambia el formato de salida de "0.00" a "0".
--   Si BankVision requiere decimales, mantener NUMERIC y ajustar aqui.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. NIT
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 20,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'nit',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.nit',
    parametros_json     = '{"sourceField": "nit"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'NIT del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'NIT';

-- ---------------------------------------------------------------------------
-- 2. OFICINA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'oficina',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.oficina',
    parametros_json     = '{"sourceField": "oficina"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Oficina del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'OFICINA';

-- ---------------------------------------------------------------------------
-- 3. GTECUENTA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'oficina',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarMismoValorSiInformado',
    expresion_sql       = 'CASE WHEN base.oficina IS NULL THEN NULL ELSE base.oficina END',
    parametros_json     = '{"dependsOnSource": "oficina", "when": "NOT_NULL", "copyFromSource": "oficina"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Replica OFICINA cuando el campo viene informado.',
    observaciones       = 'Regla funcional Campo 3 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'GTECUENTA';

-- ---------------------------------------------------------------------------
-- 4. ZONA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 5,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'oficina',
    alias_origen        = 'base',
    valor_constante     = '1',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.oficina IS NULL THEN NULL ELSE 1 END',
    parametros_json     = '{"dependsOnSource": "oficina", "when": "NOT_NULL", "valueIfTrue": "1"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Asigna 1 cuando OFICINA está informado.',
    observaciones       = 'Regla funcional Campo 4 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ZONA';

-- ---------------------------------------------------------------------------
-- 5. DOCUMENTO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 20,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'SECUENCIA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'documento',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'resolverDocumentoConsecutivo',
    expresion_sql       = NULL,
    parametros_json     = '{"sourceField": "documento", "useSequenceWhenNullOrZero": true, "currentSequenceParam": "CREFFSOS_DOCUMENTO_CONSECUTIVO_ACTUAL", "initialSequenceParam": "CREFFSOS_DOCUMENTO_CONSECUTIVO_INICIAL", "increment": 1}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Usa DOCUMENTO del origen y genera consecutivo si viene vacío o en cero.',
    observaciones       = 'El consecutivo queda externalizado en sipro_parametros_unico.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DOCUMENTO';

-- ---------------------------------------------------------------------------
-- 6. MONEDA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'moneda',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.moneda',
    parametros_json     = '{"sourceField": "moneda"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Moneda del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MONEDA';

-- ---------------------------------------------------------------------------
-- 7. MODALIDAD
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 255,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'modalidad',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.modalidad',
    parametros_json     = '{"sourceField": "modalidad"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Modalidad del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MODALIDAD';

-- ---------------------------------------------------------------------------
-- 8. ESTADOCR
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 5,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'modalidad',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.modalidad IS NULL OR base.modalidad = '''' THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "modalidad", "when": "NOT_BLANK", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Asigna 0 cuando MODALIDAD tiene valor.',
    observaciones       = 'Regla funcional Campo 8 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ESTADOCR';

-- ---------------------------------------------------------------------------
-- 9. ANOINIOBL
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 4,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'anoiniobl',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.anoiniobl',
    parametros_json     = '{"sourceField": "anoiniobl"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Año inicial de obligación.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ANOINIOBL';

-- ---------------------------------------------------------------------------
-- 10. MESINIOBL
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'mesiniobl',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.mesiniobl',
    parametros_json     = '{"sourceField": "mesiniobl"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Mes inicial de obligación.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MESINIOBL';

-- ---------------------------------------------------------------------------
-- 11. DIAINIOBL
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diainiobl',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.diainiobl',
    parametros_json     = '{"sourceField": "diainiobl"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Día inicial de obligación.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DIAINIOBL';

-- ---------------------------------------------------------------------------
-- 12. ANOVCTO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 4,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'anovcto',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.anovcto',
    parametros_json     = '{"sourceField": "anovcto"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Año de vencimiento.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ANOVCTO';

-- ---------------------------------------------------------------------------
-- 13. MESVCTO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'mesvcto',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.mesvcto',
    parametros_json     = '{"sourceField": "mesvcto"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Mes de vencimiento.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MESVCTO';

-- ---------------------------------------------------------------------------
-- 14. DIAVCTO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavcto',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.diavcto',
    parametros_json     = '{"sourceField": "diavcto"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Día de vencimiento.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DIAVCTO';

-- ---------------------------------------------------------------------------
-- 15. ANOVCTOFIN
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 4,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'anovctofin',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.anovctofin',
    parametros_json     = '{"sourceField": "anovctofin"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Año de vencimiento final.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ANOVCTOFIN';

-- ---------------------------------------------------------------------------
-- 16. MESVCTOFIN
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'mesvctofin',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.mesvctofin',
    parametros_json     = '{"sourceField": "mesvctofin"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Mes de vencimiento final.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MESVCTOFIN';

-- ---------------------------------------------------------------------------
-- 17. DIAVCTOFIN
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.diavctofin',
    parametros_json     = '{"sourceField": "diavctofin"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Día de vencimiento final.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DIAVCTOFIN';

-- ---------------------------------------------------------------------------
-- 18. ANOULTPAGO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 4,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Año último pago; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 18 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'ANOULTPAGO';

-- ---------------------------------------------------------------------------
-- 19. MESULTPAGO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Mes último pago; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 19 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'MESULTPAGO';

-- ---------------------------------------------------------------------------
-- 20. DIAULTPAGO
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 2,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Día último pago; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 20 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DIAULTPAGO';

-- ---------------------------------------------------------------------------
-- 21-24. TASAS Y PORCENTAJE  (NOTA: cambian de NUMERIC 8,2 a INTEGER segun
--        definicion canonica. Salida cambia de "0.00" a "0". Confirmar con BV.)
-- ---------------------------------------------------------------------------

-- 21. TASAINTVIG
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tasa interés vigente; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 21 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'TASAINTVIG';

-- 22. TASAINTMRA
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tasa interés mora; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 22 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'TASAINTMRA';

-- 23. TASAREDESC
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tasa redescuento; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 23 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'TASAREDESC';

-- 24. PORCRDSCTO
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Porcentaje redescuento; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 24 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PORCRDSCTO';

-- ---------------------------------------------------------------------------
-- 25. CTAPUC  [CORRECCION CRITICA: agrega funcion_java y parametros_json]
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 20,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'LOOKUP',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'ctapuc',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'resolverCuentaBankvision',
    expresion_sql       = NULL,
    parametros_json     = '{"sourceField": "ctapuc", "lookupTable": "public.sipro_parametros_homologacion_colgaap", "lookupKey": "cuenta_sap", "lookupValue": "cuenta_bv", "ifNotFound": "NULL"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Cuenta PUC homologada hacia Bankvision.',
    observaciones       = 'Cruza CTAPUC con public.sipro_parametros_homologacion_colgaap.cuenta_sap para traer cuenta_bv.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CTAPUC';

-- ---------------------------------------------------------------------------
-- 26. CLASETASA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 3,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'CONSTANTE',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '   ',
    funcion_java        = 'asignarConstante',
    expresion_sql       = '''   ''',
    parametros_json     = '{"value": "   "}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tres espacios en blanco según especificación funcional.',
    observaciones       = 'Regla funcional Campo 26 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CLASETASA';

-- ---------------------------------------------------------------------------
-- 27-32. VALORES DIRECTOS NUMERICOS (NUMERIC 18,2)
-- ---------------------------------------------------------------------------

-- 27. VLRINIOBL
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'vlriniobl',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.vlriniobl',
    parametros_json     = '{"sourceField": "vlriniobl"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Valor inicial de la obligación.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'VLRINIOBL';

-- 28. SALDO
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'saldo',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.saldo',
    parametros_json     = '{"sourceField": "saldo"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Saldo del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'SALDO';

-- 29. SDOOTRCTAS
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'sdootrctas',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.sdootrctas',
    parametros_json     = '{"sourceField": "sdootrctas"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Saldo otras cuentas.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'SDOOTRCTAS';

-- 30. INTERESES
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'intereses',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.intereses',
    parametros_json     = '{"sourceField": "intereses"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Intereses del registro consolidado.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INTERESES';

-- 31. SDOVENCIDO
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'sdovencido',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.sdovencido',
    parametros_json     = '{"sourceField": "sdovencido"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Saldo vencido.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'SDOVENCIDO';

-- 32. INTCTASORD
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'NUMERIC',
    longitud_maxima     = NULL,
    precision_numerica  = 18,
    escala_numerica     = 2,
    formato_salida      = '0.00',
    origen_dato         = 'DIRECTO',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'intctasord',
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'copiarDirecto',
    expresion_sql       = 'base.intctasord',
    parametros_json     = '{"sourceField": "intctasord"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Intereses cuentas orden.',
    observaciones       = 'Base principal: public.sipro_detalle_consolidado_registros.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INTCTASORD';

-- ---------------------------------------------------------------------------
-- 33. SALDOMES  (NOTA: cambia de NUMERIC 20,2 a INTEGER segun def. canonica)
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'diavctofin',
    alias_origen        = 'base',
    valor_constante     = '0',
    funcion_java        = 'asignarConstanteSiCampoInformado',
    expresion_sql       = 'CASE WHEN base.diavctofin IS NULL THEN NULL ELSE 0 END',
    parametros_json     = '{"dependsOnSource": "diavctofin", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Saldo mes; usa 0 cuando DIAVCTOFIN viene informado.',
    observaciones       = 'Regla funcional Campo 33 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'SALDOMES';

-- ---------------------------------------------------------------------------
-- 34-43. CADENA DE PROVISIONES (NOTA: cambian de NUMERIC 20,2 a INTEGER)
-- ---------------------------------------------------------------------------

-- 34. PROVCAPANT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "SALDOMES", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión capital anterior; depende de SALDOMES.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVCAPANT';

-- 35. PROVCAPACT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVCAPANT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión capital actual; depende de PROVCAPANT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVCAPACT';

-- 36. PROVINTANT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVCAPACT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión interés anterior; depende de PROVCAPACT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVINTANT';

-- 37. PROVINTACT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVINTANT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión interés actual; depende de PROVINTANT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVINTACT';

-- 38. PROVOTRANT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVINTACT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión otras anterior; depende de PROVINTACT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVOTRANT';

-- 39. PROVOTRACT
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVOTRANT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión otras actual; depende de PROVOTRANT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVOTRACT';

-- 40. PROVCAPUSA
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVOTRACT", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión capital USA; depende de PROVOTRACT.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVCAPUSA';

-- 41. PROVINTUSA
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVCAPUSA", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Provisión interés USA; depende de PROVCAPUSA.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PROVINTUSA';

-- 42. GARANTIA
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PROVINTUSA", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Garantía; depende de PROVINTUSA.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'GARANTIA';

-- 43. GARCREDITO
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "GARANTIA", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Garantía crédito; depende de GARANTIA.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'GARCREDITO';

-- ---------------------------------------------------------------------------
-- 44. CLASEGTIA  [CORRECCION CRITICA: agrega funcion_java y parametros_json LZ]
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'LOOKUP',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'documento',
    alias_origen        = 'base',
    valor_constante     = 'N',
    funcion_java        = 'resolverClaseGarantiaDesdeCenie',
    expresion_sql       = NULL,
    parametros_json     = '{
        "sourceField": "documento",
        "lookupSystem": "IMPALA",
        "lookupSchema": "s_productos",
        "lookupTable": "bvnc_visionry_cenie",
        "lookupKey": "ceac21",
        "lookupValue": "cein21",
        "filters": {
            "cest21NotIn": ["01", "02"],
            "cetr21": 1,
            "ceap21Trim": "C",
            "ultimaFechaIngestion": true
        },
        "defaultValue": "N"
    }'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Clase de garantía consultada en CENIE.',
    observaciones       = 'Requiere integración con Impala/LZ para la consulta externa.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CLASEGTIA';

-- ---------------------------------------------------------------------------
-- 45. DSTECONOM
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '000260',
    funcion_java        = 'asignarConstanteSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "CLASEGTIA", "when": "NOT_NULL", "valueIfTrue": "000260"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Destino económico; usa 000260 cuando CLASEGTIA tiene valor.',
    observaciones       = 'Regla funcional Campo 45 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'DSTECONOM';

-- ---------------------------------------------------------------------------
-- 46. CLASIFCPUC  [MEJORA: agrega tiposClasificacionUno al parametros_json]
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 5,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = NULL,
    alias_origen        = 'base',
    valor_constante     = NULL,
    funcion_java        = 'resolverClasificacionCpuc',
    expresion_sql       = 'CASE WHEN base.modalidad = ''HIP'' THEN 3 WHEN base.tipo_id IN (''FS003'', ''FS007'', ''FS008'') THEN 1 WHEN base.modalidad = ''DSC'' THEN 1 ELSE 2 END',
    parametros_json     = '{"sourceFields": ["modalidad", "tipo_id"], "modalidadHip": "HIP", "modalidadDsc": "DSC", "tiposClasificacionUno": ["FS003", "FS007", "FS008"], "defaultValue": 2}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Clasificación PUC según modalidad y tipo de identificación.',
    observaciones       = 'Regla funcional Campo 46 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CLASIFCPUC';

-- ---------------------------------------------------------------------------
-- 47. CLASIFCUSA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 5,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = NULL,
    funcion_java        = 'copiarSalidaSiInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "CLASIFCPUC", "when": "NOT_NULL", "copyFromOutput": "CLASIFCPUC"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Clasificación CUSA; replica CLASIFCPUC cuando existe.',
    observaciones       = 'Se parametriza así por la ambigüedad del documento funcional.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CLASIFCUSA';

-- ---------------------------------------------------------------------------
-- 48. CALIFICPUC  [CORRECCION CRITICA: agrega funcion_java y parametros_json LZ]
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'LOOKUP',
    tabla_origen        = 'sipro_detalle_consolidado_registros',
    columna_origen      = 'documento',
    alias_origen        = 'base',
    valor_constante     = 'A',
    funcion_java        = 'resolverCalificacionDesdeCenie',
    expresion_sql       = NULL,
    parametros_json     = '{
        "sourceField": "documento",
        "lookupSystem": "IMPALA",
        "lookupSchema": "s_productos",
        "lookupTable": "bvnc_visionry_cenie",
        "lookupKey": "ceac21",
        "lookupValue": "ceca21",
        "filters": {
            "cest21NotIn": ["01", "02"],
            "cetr21": 1,
            "ceap21Trim": "C",
            "ultimaFechaIngestion": true
        },
        "defaultValue": "A"
    }'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Calificación PUC consultada en CENIE.',
    observaciones       = 'Requiere integración con Impala/LZ para la consulta externa.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CALIFICPUC';

-- ---------------------------------------------------------------------------
-- 49. CALIFICUSA
-- ---------------------------------------------------------------------------
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'VACIO',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = NULL,
    funcion_java        = 'dejarVacio',
    expresion_sql       = 'NULL',
    parametros_json     = '{}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Campo vacío según especificación funcional.',
    observaciones       = 'Regla funcional Campo 49 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'CALIFICUSA';

-- ---------------------------------------------------------------------------
-- 50-56. CADENA PLAZO / VALORES / FECHAS (dependen de CTAPUC)
-- ---------------------------------------------------------------------------

-- 50. PLAZO
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "CTAPUC", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Plazo; usa 0 cuando CTAPUC está informado.',
    observaciones       = 'Regla funcional Campo 50 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'PLAZO';

-- 51. VALORUSU1
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "PLAZO", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Valor usuario 1; depende de PLAZO.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'VALORUSU1';

-- 52. VALORUSU2
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "VALORUSU1", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Valor usuario 2; depende de VALORUSU1.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'VALORUSU2';

-- 53. VALORUSU3
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "VALORUSU2", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Valor usuario 3; depende de VALORUSU2.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'VALORUSU3';

-- 54. VALORUSU4
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "VALORUSU3", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Valor usuario 4; depende de VALORUSU3.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'VALORUSU4';

-- 55. FECHAUSU1
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "VALORUSU4", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Fecha usuario 1; depende de VALORUSU4.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'FECHAUSU1';

-- 56. FECHAUSU2
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "FECHAUSU1", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Fecha usuario 2; depende de FECHAUSU1.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'FECHAUSU2';

-- ---------------------------------------------------------------------------
-- 57-58. INDICADORES CONSTANTES (3 espacios)
-- ---------------------------------------------------------------------------

-- 57. INDICUSU1
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 3,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'CONSTANTE',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '   ',
    funcion_java        = 'asignarConstante',
    expresion_sql       = '''   ''',
    parametros_json     = '{"value": "   "}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tres espacios en blanco según especificación funcional.',
    observaciones       = 'Regla funcional Campo 57 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INDICUSU1';

-- 58. INDICUSU2
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'STRING',
    longitud_maxima     = 3,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '@',
    origen_dato         = 'CONSTANTE',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '   ',
    funcion_java        = 'asignarConstante',
    expresion_sql       = '''   ''',
    parametros_json     = '{"value": "   "}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tres espacios en blanco según especificación funcional.',
    observaciones       = 'Regla funcional Campo 58 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INDICUSU2';

-- ---------------------------------------------------------------------------
-- 59-61. CADENA INDICADORES / TASA (dependen de CTAPUC via INDICUSU3)
-- ---------------------------------------------------------------------------

-- 59. INDICUSU3
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "CTAPUC", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Indicador usuario 3; depende de CTAPUC.',
    observaciones       = 'Regla funcional Campo 59 de CREFFSOS.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INDICUSU3';

-- 60. INDICUSU4
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "INDICUSU3", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Indicador usuario 4; depende de INDICUSU3.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'INDICUSU4';

-- 61. TASAUSU1
UPDATE public.sipro_parametros_columnas_creffsos SET
    tipo_dato_salida    = 'INTEGER',
    longitud_maxima     = 10,
    precision_numerica  = NULL,
    escala_numerica     = NULL,
    formato_salida      = '0',
    origen_dato         = 'REGLA',
    tabla_origen        = NULL,
    columna_origen      = NULL,
    alias_origen        = NULL,
    valor_constante     = '0',
    funcion_java        = 'asignarCeroSiSalidaInformada',
    expresion_sql       = NULL,
    parametros_json     = '{"dependsOnOutput": "INDICUSU4", "when": "NOT_NULL", "valueIfTrue": "0"}'::jsonb,
    obligatorio         = true,
    permite_nulo        = true,
    descripcion         = 'Tasa usuario 1; depende de INDICUSU4.',
    observaciones       = 'Regla encadenada por salida generada.',
    modificado_en       = CURRENT_TIMESTAMP
WHERE nombre_columna = 'TASAUSU1';

-- =============================================================================
-- Verificacion post-ejecucion: debe devolver 61 filas con estado=1
-- =============================================================================
-- SELECT nombre_columna, orden, funcion_java, origen_dato, parametros_json
-- FROM   public.sipro_parametros_columnas_creffsos
-- WHERE  estado = 1
-- ORDER  BY orden;