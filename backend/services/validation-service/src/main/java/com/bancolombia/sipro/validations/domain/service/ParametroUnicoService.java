package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproParametroUnico;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproParametroUnicoRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio que lee parámetros únicos de la tabla sipro_parametros_unico
 * y los cachea en memoria al arrancar. Provee métodos tipados para leer
 * valores int, long, String con fallback a un default si no existe la clave.
 *
 * Cuando la BD no está disponible (DEV local sin PostgreSQL), el servicio
 * busca el valor en las propiedades de Spring con prefijo {@code sipro.param.<CLAVE>}.
 * Configurar en application-dev.yml o como variables de entorno.
 *
 * PARÁMETROS CRÍTICOS DE SEGURIDAD (solo desde config, nunca desde BD):
 * - AZURE_TENANT_ID: identificador del tenant de Entra ID
 * - AZURE_CLIENT_ID: identificador de la aplicación registrada en Entra ID
 * - AZURE_CLIENT_SECRET: credencial secreta de Entra ID (crítica)
 * - URL_PDN: URL base del frontend en producción
 *
 * Estos 4 parámetros SIEMPRE se leen desde propiedades Spring (config files + env vars),
 * nunca desde la tabla sipro_parametros_unico en la BD, incluso si existe un valor ahí.
 * Esto asegura que en PDN estos valores críticos vengan EXCLUSIVAMENTE del pipeline,
 * del Variable Group de Azure DevOps, o de Secrets Manager.
 */
@Service
public class ParametroUnicoService {

    private static final Logger logger = LoggerFactory.getLogger(ParametroUnicoService.class);
    private static final String ENV_PREFIX = "sipro.param.";

    /** Tiempo maximo que se confia en un valor cacheado antes de revalidarlo contra la BD. */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /** Parámetros que NUNCA deben venir de la BD — solo de config/env vars */
    private static final java.util.Set<String> CRITICAL_CONFIG_ONLY_PARAMS = java.util.Set.of(
            "AZURE_TENANT_ID",
            "AZURE_CLIENT_ID",
            "AZURE_CLIENT_SECRET",
            "URL_PDN"
    );

    /** Valor cacheado junto con el instante (epoch millis) en que se guardo. */
    private record CachedValue(String valor, long guardadoEn) {}

    private final SiproParametroUnicoRepository repository;
    private final Environment environment;
    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> sequenceLocks = new ConcurrentHashMap<>();

    public ParametroUnicoService(SiproParametroUnicoRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @PostConstruct
    public void cargarParametros() {
        try {
            long ahora = System.currentTimeMillis();
            repository.findAll().forEach(p -> cache.put(p.getClave(), new CachedValue(p.getValor(), ahora)));
            logger.info("Parámetros únicos cargados: {} entradas", cache.size());
        } catch (Exception e) {
            logger.warn("No fue posible cargar sipro_parametros_unico al arranque: {}. Se usarán defaults hasta recargar.",
                    e.getMessage());
        }
    }

    /** Recarga todos los parámetros desde la BD. */
    public void recargar() {
        cache.clear();
        cargarParametros();
    }

    public int cantidadParametros() {
        return cache.size();
    }

    public Optional<String> getString(String clave) {
        // Si es un parámetro crítico, leer SOLO desde config
        if (CRITICAL_CONFIG_ONLY_PARAMS.contains(clave)) {
            return getConfigOnly(clave);
        }

        Optional<String> val = resolveValue(clave);
        if (val.isPresent() && !val.get().isBlank()) return val;
        String fromEnv = resolveFromEnvironment(clave);
        return fromEnv != null ? Optional.of(fromEnv) : Optional.empty();
    }

    public String getString(String clave, String defaultValue) {
        // Si es un parámetro crítico, leer SOLO desde config
        if (CRITICAL_CONFIG_ONLY_PARAMS.contains(clave)) {
            return getConfigOnly(clave).orElse(defaultValue);
        }

        Optional<String> val = resolveValue(clave);
        if (val.isPresent() && !val.get().isBlank()) return val.get();
        String fromEnv = resolveFromEnvironment(clave);
        return fromEnv != null ? fromEnv : defaultValue;
    }

    /**
     * Resuelve el valor mas reciente de una clave:
     *  - Si la copia en cache tiene menos de CACHE_TTL_MS, se usa tal cual (rapido, sin ir a BD).
     *  - Si no esta en cache o ya vencio, se consulta la BD SOLO por esa clave puntual y se
     *    refresca la cache con el valor y la hora actuales.
     *  - Si la consulta a la BD falla (ej: BD no disponible) o la clave no existe ahi, se usa
     *    lo que hubiera en cache aunque este vencido — mejor un valor desactualizado que nada.
     */
    private Optional<String> resolveValue(String clave) {
        CachedValue cached = cache.get(clave);
        boolean fresco = cached != null
                && (System.currentTimeMillis() - cached.guardadoEn()) < CACHE_TTL_MS;
        if (fresco) {
            return Optional.of(cached.valor());
        }

        try {
            Optional<SiproParametroUnico> desdeDb = repository.findByClave(clave);
            if (desdeDb.isPresent()) {
                String valor = desdeDb.get().getValor();
                cache.put(clave, new CachedValue(valor, System.currentTimeMillis()));
                return Optional.of(valor);
            }
        } catch (Exception e) {
            logger.warn("No se pudo refrescar el parámetro '{}' desde BD: {}. Se usa el valor en cache (si existe).",
                    clave, e.getMessage());
        }

        return cached != null ? Optional.of(cached.valor()) : Optional.empty();
    }

    /**
     * Obtiene un valor EXCLUSIVAMENTE desde propiedades Spring (config + env vars).
     * No consulta la BD bajo ninguna circunstancia.
     * Usado para parámetros críticos de seguridad que NO deben estar en la BD.
     */
    private Optional<String> getConfigOnly(String clave) {
        String val = environment.getProperty(ENV_PREFIX + clave);
        if (val != null && !val.isBlank()) {
            logger.debug("Parámetro crítico '{}' resuelto desde propiedades (config-only)", clave);
            return Optional.of(val.trim());
        }
        return Optional.empty();
    }

    /**
     * Resuelve el valor desde las propiedades Spring como fallback cuando la BD no está disponible.
     * Busca {@code sipro.param.<CLAVE>} en el Environment (application-*.yml o variables de entorno).
     */
    private String resolveFromEnvironment(String clave) {
        String val = environment.getProperty(ENV_PREFIX + clave);
        if (val != null && !val.isBlank()) {
            logger.debug("Parámetro '{}' resuelto desde propiedades de entorno (BD no disponible)", clave);
            return val.trim();
        }
        return null;
    }

    public int getInt(String clave, int defaultValue) {
        Optional<String> val = resolveValue(clave);
        if (val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val.get().trim());
        } catch (NumberFormatException e) {
            logger.warn("Parámetro '{}' tiene valor no numérico '{}'. Usando default: {}", clave, val.get(), defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String clave, long defaultValue) {
        Optional<String> val = resolveValue(clave);
        if (val.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(val.get().trim());
        } catch (NumberFormatException e) {
            logger.warn("Parámetro '{}' tiene valor no numérico '{}'. Usando default: {}", clave, val.get(), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Obtiene el valor directo de la BD (sin caché) para un parámetro.
     * Útil cuando se necesita el valor más reciente tras un cambio manual.
     */
    public Optional<SiproParametroUnico> obtenerDirecto(String clave) {
        return repository.findByClave(clave);
    }

    @Transactional
    public void setString(String clave, String valor) {
        repository.findByClave(clave).ifPresentOrElse(parametro -> {
            parametro.setValor(valor);
            repository.save(parametro);
            cache.put(clave, new CachedValue(valor, System.currentTimeMillis()));
        }, () -> {
            SiproParametroUnico nuevoParametro = new SiproParametroUnico();
            nuevoParametro.setClave(clave);
            nuevoParametro.setValor(valor);
            nuevoParametro.setTipo("STRING");
            repository.save(nuevoParametro);
            cache.put(clave, new CachedValue(valor, System.currentTimeMillis()));
        });
    }

    /**
     * Reserva en una sola transacción un bloque de consecutivos para uso intensivo en generación de archivos.
     */
    @Transactional
    public SequenceReservation reserveSequenceRange(String currentKey,
                                                   String initialKey,
                                                   int increment,
                                                   int requestedCount) {
        if (currentKey == null || currentKey.isBlank() || requestedCount <= 0) {
            return SequenceReservation.disabled();
        }

        int safeIncrement = increment <= 0 ? 1 : increment;
        Object lock = sequenceLocks.computeIfAbsent(currentKey, ignored -> new Object());

        synchronized (lock) {
            long initialValue = (initialKey == null || initialKey.isBlank())
                    ? 0L
                    : getLong(initialKey, 0L);

            SiproParametroUnico parametro = repository.findByClaveForUpdate(currentKey)
                    .orElseGet(() -> {
                        SiproParametroUnico nuevo = new SiproParametroUnico();
                        nuevo.setClave(currentKey);
                        nuevo.setValor(String.valueOf(initialValue));
                        nuevo.setTipo("INTEGER");
                        return repository.saveAndFlush(nuevo);
                    });

            long currentValue = parseLongOrDefault(parametro.getValor(), initialValue, currentKey);
            long nextValue = currentValue + safeIncrement;
            long lastReservedValue = currentValue + ((long) safeIncrement * requestedCount);

            parametro.setValor(String.valueOf(lastReservedValue));
            repository.save(parametro);
            cache.put(currentKey, new CachedValue(String.valueOf(lastReservedValue), System.currentTimeMillis()));

            return new SequenceReservation(true, nextValue, lastReservedValue, safeIncrement);
        }
    }

    private long parseLongOrDefault(String rawValue, long defaultValue, String clave) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            logger.warn("Parámetro '{}' tiene valor no numérico '{}'. Se usará {} para reservar secuencia.",
                    clave, rawValue, defaultValue);
            return defaultValue;
        }
    }

    public record SequenceReservation(boolean enabled,
                                      long nextValue,
                                      long lastReservedValue,
                                      int increment) {
        public static SequenceReservation disabled() {
            return new SequenceReservation(false, 0L, 0L, 0);
        }
    }
}
