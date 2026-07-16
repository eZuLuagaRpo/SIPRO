CREATE TABLE IF NOT EXISTS public.sipro_parametros_homologacion_colgaap (
    cuenta_sap character varying(50) PRIMARY KEY,
    cuenta_bv character varying(50) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sipro_parametros_homologacion_colgaap_cuenta_sap
    ON public.sipro_parametros_homologacion_colgaap (cuenta_sap);

UPDATE public.sipro_parametros_columnas_creffsos
SET columna_origen = 'ctapuc',
    funcion_java = 'resolverCuentaBankvision',
    parametros_json = jsonb_build_object(
        'sourceField', 'ctapuc',
        'lookupTable', 'public.sipro_parametros_homologacion_colgaap',
        'lookupKey', 'cuenta_sap',
        'lookupValue', 'cuenta_bv',
        'ifNotFound', 'NULL'
    ),
    observaciones = 'Cruza CTAPUC con public.sipro_parametros_homologacion_colgaap.cuenta_sap para traer cuenta_bv.'
WHERE nombre_columna = 'CTAPUC';

