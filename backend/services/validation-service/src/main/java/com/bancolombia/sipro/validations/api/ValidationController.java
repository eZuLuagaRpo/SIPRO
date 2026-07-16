package com.bancolombia.sipro.validations.api;

import com.bancolombia.sipro.validations.application.dto.ValidationJobStartResponse;
import com.bancolombia.sipro.validations.application.dto.ValidationJobStatusResponse;
import com.bancolombia.sipro.validations.model.ValidationResult;
import com.bancolombia.sipro.validations.service.ValidationAsyncService;
import com.bancolombia.sipro.validations.service.FileValidationService;
import com.bancolombia.sipro.validations.service.LoteMemoryStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Expone los endpoints de validación de archivos en modo síncrono y asíncrono.
 */
@Controller
public class ValidationController {
    
    private final FileValidationService validationService;
    private final ValidationAsyncService validationAsyncService;
    private final LoteMemoryStore store;
    
    public ValidationController(
        FileValidationService validationService,
        ValidationAsyncService validationAsyncService,
        LoteMemoryStore store
    ) {
        this.validationService = validationService;
        this.validationAsyncService = validationAsyncService;
        this.store = store;
    }
    
    /**
     * Redirige la raíz hacia la pantalla principal de login.
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/login.html";
    }
    
    /**
     * Valida un archivo de inmediato y devuelve el resultado en la misma respuesta.
     *
     * @param archivoControl archivo control .txt (requerido para Full IFRS, omitir en Colgaap)
     */
    @PostMapping("/api/validar")
    @ResponseBody
    public ValidationResult validar(
        @RequestParam(required = false) Long idProducto,
        @RequestParam(required = false) Long idSegmento,
        @RequestParam String producto, // Se mantiene por compatibilidad o para mostrar en mensajes
        @RequestParam String fechaCorte,
        @RequestParam(required = false, defaultValue = "") String descripcion,
        @RequestParam(required = false, defaultValue = "") String usuarioAdmin,
        @RequestPart MultipartFile archivo,
        @RequestPart(value = "archivoControl", required = false) MultipartFile archivoControl
    ) {
        try {
            return validationService.validarArchivo(idProducto, idSegmento, producto, fechaCorte, descripcion, archivo, archivoControl, usuarioAdmin);
        } catch (Exception e) {
            ValidationResult error = new ValidationResult();
            error.setStatus("ERROR");
            error.getErrores().add("Error procesando archivo: " + e.getMessage());
            return error;
        }
    }

    /**
     * Inicia una validación en segundo plano y retorna el identificador del trabajo.
     *
     * @param archivoControl archivo control .txt (requerido para Full IFRS, omitir en Colgaap)
     */
    @PostMapping("/api/validar/async")
    @ResponseBody
    public ResponseEntity<ValidationJobStartResponse> iniciarValidacionAsync(
        @RequestParam(required = false) Long idProducto,
        @RequestParam(required = false) Long idSegmento,
        @RequestParam String producto,
        @RequestParam String fechaCorte,
        @RequestParam(required = false, defaultValue = "") String descripcion,
        @RequestParam(required = false, defaultValue = "") String usuarioAdmin,
        @RequestPart MultipartFile archivo,
        @RequestPart(value = "archivoControl", required = false) MultipartFile archivoControl
    ) throws Exception {
        ValidationJobStartResponse response = validationAsyncService.startValidationJob(
            idProducto,
            idSegmento,
            producto,
            fechaCorte,
            descripcion,
            usuarioAdmin,
            archivo,
            archivoControl
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Consulta el estado actual de una validación asíncrona.
     */
    @GetMapping("/api/validar/jobs/{jobId}")
    @ResponseBody
    public ValidationJobStatusResponse consultarValidacionAsync(@PathVariable String jobId) {
        return validationAsyncService.getValidationJobStatus(jobId);
    }
    
    /**
     * Descarga el archivo de errores generado para un lote validado.
     */
    @GetMapping("/api/lotes/{id}/errores")
    public ResponseEntity<byte[]> descargarErrores(@PathVariable String id) {
        byte[] content = store.getErroresFile(id);
        
        if (content == null) {
            content = "REPORTE DE ERRORES DE VALIDACION SIPRO\n====================================\n\nNo hay errores registrados para este lote.\n".getBytes();
        }
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"errores_" + id + ".txt\"")
            .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
            .body(content);
    }
}
