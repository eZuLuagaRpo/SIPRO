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
 */
@Service
public class ParametroUnicoService {

    private static final Logger logger = LoggerFactory.getLogger(ParametroUnicoService.class);
    private static final String ENV_PREFIX = "sipro.param.";

    private final SiproParametroUnicoRepository repository;
    private final Environment environment;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> sequenceLocks = new ConcurrentHashMap<>();

    public ParametroUnicoService(SiproParametroUnicoRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @PostConstruct
    public void cargarParametros() {
        try {
            repository.findAll().forEach(p -> cache.put(p.getClave(), p.getValor()));
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
        String val = cache.get(clave);
        if (val != null && !val.isBlank()) return Optional.of(val);
        String fromEnv = resolveFromEnvironment(clave);
        return fromEnv != null ? Optional.of(fromEnv) : Optional.empty();
    }

    public String getString(String clave, String defaultValue) {
        String val = cache.get(clave);
        if (val != null && !val.isBlank()) return val;
        String fromEnv = resolveFromEnvironment(clave);
        return fromEnv != null ? fromEnv : defaultValue;
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
        String val = cache.get(clave);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Parámetro '{}' tiene valor no numérico '{}'. Usando default: {}", clave, val, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String clave, long defaultValue) {
        String val = cache.get(clave);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Parámetro '{}' tiene valor no numérico '{}'. Usando default: {}", clave, val, defaultValue);
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
            cache.put(clave, valor);
        }, () -> {
            SiproParametroUnico nuevoParametro = new SiproParametroUnico();
            nuevoParametro.setClave(clave);
            nuevoParametro.setValor(valor);
            nuevoParametro.setTipo("STRING");
            repository.save(nuevoParametro);
            cache.put(clave, valor);
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
            cache.put(currentKey, String.valueOf(lastReservedValue));

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
