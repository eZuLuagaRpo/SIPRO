package com.bancolombia.sipro.validations.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Publica copias protegidas contra edicion (CREFFSOS, planillas Full IFRS aprobadas y sus
 * controles en Word) en una carpeta de red separada de las rutas compartidas normales,
 * organizadas por periodo (AAAA-MM-DD) y comprimidas en un unico .zip al cierre del periodo.
 *
 * Totalmente independiente de las rutas compartidas existentes (CREFFSOS_RUTA_SALIDA,
 * IFRS_PLANILLAS_RUTA_SALIDA): esta clase no las toca ni depende de ellas.
 */
@Service
public class ArchivosBloqueadosService {

    private static final Logger logger = LoggerFactory.getLogger(ArchivosBloqueadosService.class);
    private static final String PARAM_RUTA_SALIDA = "ARCHIVOS_BLOQUEADOS_RUTA_SALIDA";
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ParametroUnicoService parametroUnicoService;

    public ArchivosBloqueadosService(ParametroUnicoService parametroUnicoService) {
        this.parametroUnicoService = parametroUnicoService;
    }

    private String resolveRutaRaiz() {
        return parametroUnicoService.getString(PARAM_RUTA_SALIDA, "").trim();
    }

    /**
     * Escribe (o sobreescribe) un archivo ya protegido dentro de la subcarpeta del periodo.
     * Si el parametro de ruta no esta configurado, no hace nada (mismo comportamiento de
     * "omitir en silencio" que ya usan CreffosConsolidationService/PlanillaUseCase).
     */
    public void publicarArchivo(LocalDate fechaCorte, String nombreArchivo, byte[] contenido) {
        if (fechaCorte == null || nombreArchivo == null || nombreArchivo.isBlank() || contenido == null) {
            return;
        }
        String rutaRaiz = resolveRutaRaiz();
        if (rutaRaiz.isBlank()) {
            logger.info("[ArchivosBloqueados] Parametro {} no configurado. Se omite la publicacion de {}.",
                    PARAM_RUTA_SALIDA, nombreArchivo);
            return;
        }

        try {
            Path periodoDir = Path.of(rutaRaiz).resolve(fechaCorte.format(FECHA_FMT));
            Files.createDirectories(periodoDir);
            Files.write(periodoDir.resolve(nombreArchivo), contenido,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            logger.info("[ArchivosBloqueados] Archivo publicado: {}/{}", periodoDir, nombreArchivo);
        } catch (Exception ex) {
            logger.warn("[ArchivosBloqueados] No se pudo publicar '{}' en '{}': {}",
                    nombreArchivo, rutaRaiz, ex.getMessage());
        }
    }

    /**
     * Comprime todo lo que haya en la subcarpeta del periodo en un unico .zip (a la altura de
     * la carpeta del periodo, no dentro de ella), y borra los archivos sueltos ya empaquetados
     * junto con la carpeta del periodo. Se llama una sola vez, al cerrar el periodo (cuando se
     * genera el CREFFSOS) — para ese momento ya deberian estar publicadas todas las
     * aprobaciones de Full IFRS del periodo.
     */
    public void comprimirYFinalizarPeriodo(LocalDate fechaCorte) {
        if (fechaCorte == null) {
            return;
        }
        String rutaRaiz = resolveRutaRaiz();
        if (rutaRaiz.isBlank()) {
            return;
        }

        Path periodoDir = Path.of(rutaRaiz).resolve(fechaCorte.format(FECHA_FMT));
        if (!Files.isDirectory(periodoDir)) {
            logger.info("[ArchivosBloqueados] No hay carpeta de periodo para comprimir: {}", periodoDir);
            return;
        }

        try {
            List<Path> archivos;
            try (Stream<Path> stream = Files.list(periodoDir)) {
                archivos = stream.filter(Files::isRegularFile).toList();
            }
            if (archivos.isEmpty()) {
                logger.info("[ArchivosBloqueados] Carpeta de periodo vacia, no se comprime: {}", periodoDir);
                return;
            }

            Path zipFile = Path.of(rutaRaiz).resolve(fechaCorte.format(FECHA_FMT) + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                for (Path archivo : archivos) {
                    zos.putNextEntry(new ZipEntry(archivo.getFileName().toString()));
                    Files.copy(archivo, zos);
                    zos.closeEntry();
                }
            }

            eliminarDirectorio(periodoDir);
            logger.info("[ArchivosBloqueados] Periodo {} comprimido en {} ({} archivos empaquetados).",
                    fechaCorte, zipFile, archivos.size());
        } catch (Exception ex) {
            logger.warn("[ArchivosBloqueados] No se pudo comprimir el periodo '{}': {}", fechaCorte, ex.getMessage());
        }
    }

    /**
     * Elimina lo publicado para un periodo (carpeta suelta si aun no se comprimio, y/o el
     * .zip ya generado). Se usa cuando se elimina la consolidacion completa del periodo.
     */
    public void eliminarPeriodo(LocalDate fechaCorte) {
        if (fechaCorte == null) {
            return;
        }
        String rutaRaiz = resolveRutaRaiz();
        if (rutaRaiz.isBlank()) {
            return;
        }

        String nombrePeriodo = fechaCorte.format(FECHA_FMT);
        Path periodoDir = Path.of(rutaRaiz).resolve(nombrePeriodo);
        Path zipFile = Path.of(rutaRaiz).resolve(nombrePeriodo + ".zip");

        try {
            if (Files.isDirectory(periodoDir)) {
                eliminarDirectorio(periodoDir);
                logger.info("[ArchivosBloqueados] Carpeta de periodo eliminada: {}", periodoDir);
            }
            if (Files.deleteIfExists(zipFile)) {
                logger.info("[ArchivosBloqueados] Zip de periodo eliminado: {}", zipFile);
            }
        } catch (Exception ex) {
            logger.warn("[ArchivosBloqueados] No se pudo eliminar lo publicado del periodo '{}': {}",
                    fechaCorte, ex.getMessage());
        }
    }

    private void eliminarDirectorio(Path directorio) throws IOException {
        try (Stream<Path> stream = Files.walk(directorio)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    logger.debug("[ArchivosBloqueados] No se pudo borrar '{}': {}", path, ex.getMessage());
                }
            });
        }
    }
}