package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Publica en carpeta bloqueada las planillas Full IFRS aprobadas de un periodo,
 * junto con su archivo control convertido a Word protegido.
 */
@Service
public class FullIfrsBloqueadosConsolidacionService {

    private static final Logger logger = LoggerFactory.getLogger(FullIfrsBloqueadosConsolidacionService.class);
    private static final Long SEGMENTO_FULL_IFRS_ID = 2L;
    private static final String LOCKED_FILE_PASSWORD = "sipro-readonly";

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final FileStorageService fileStorageService;
    private final ArchivosBloqueadosService archivosBloqueadosService;

    public FullIfrsBloqueadosConsolidacionService(SiproDetalleCargaPlanillasRepository planillaRepository,
                                                  FileStorageService fileStorageService,
                                                  ArchivosBloqueadosService archivosBloqueadosService) {
        this.planillaRepository = planillaRepository;
        this.fileStorageService = fileStorageService;
        this.archivosBloqueadosService = archivosBloqueadosService;
    }

    /**
     * Toma las planillas Full IFRS aprobadas del periodo desde storage (aprobados/) y las
     * publica en la carpeta bloqueada del mismo periodo.
     */
    public void publicarPeriodo(LocalDate fechaCorte) {
        if (fechaCorte == null) {
            return;
        }

        List<SiproDetalleCargaPlanillas> planillas = planillaRepository
                .findPlanillasAprobadasByFechaCorteAndSegmentoId(fechaCorte, SEGMENTO_FULL_IFRS_ID);

        if (planillas.isEmpty()) {
            logger.info("[Full IFRS Bloqueados] No hay planillas aprobadas para publicar en periodo {}.", fechaCorte);
            return;
        }

        for (SiproDetalleCargaPlanillas planilla : planillas) {
            try {
                publicarXlsxBloqueado(fechaCorte, planilla);
                publicarControlWordBloqueado(fechaCorte, planilla);
            } catch (Exception ex) {
                logger.warn("[Full IFRS Bloqueados] No se pudo publicar planilla id={} del periodo {}: {}",
                        planilla.getId(), fechaCorte, ex.getMessage());
            }
        }
    }

    private void publicarXlsxBloqueado(LocalDate fechaCorte, SiproDetalleCargaPlanillas planilla) throws Exception {
        String rutaXlsx = planilla.getRutaArchivoAlmacenamiento();
        if (rutaXlsx == null || rutaXlsx.isBlank()) {
            return;
        }

        byte[] xlsxBytes = fileStorageService.getFileAsBytes(rutaXlsx);
        byte[] xlsxProtegido = protegerXlsxContraEdicion(xlsxBytes);
        String nombreDest = (planilla.getNombreArchivoFuente() != null && !planilla.getNombreArchivoFuente().isBlank())
                ? planilla.getNombreArchivoFuente()
                : extraerNombreLimpio(rutaXlsx);

        archivosBloqueadosService.publicarArchivo(fechaCorte, nombreDest, xlsxProtegido);
    }

    private void publicarControlWordBloqueado(LocalDate fechaCorte, SiproDetalleCargaPlanillas planilla) throws Exception {
        String rutaControl = planilla.getRutaArchivoControl();
        if (rutaControl == null || rutaControl.isBlank()) {
            return;
        }

        byte[] ctrlBytes = fileStorageService.getFileAsBytes(rutaControl);
        byte[] wordProtegido = convertirControlAWordProtegido(ctrlBytes);
        String nombreCtrlBase = extraerNombreLimpio(rutaControl);
        String nombreCtrlDest = nombreCtrlBase.toLowerCase(Locale.ROOT).endsWith(".txt")
                ? nombreCtrlBase.substring(0, nombreCtrlBase.length() - 4) + ".docx"
                : nombreCtrlBase + ".docx";

        archivosBloqueadosService.publicarArchivo(fechaCorte, nombreCtrlDest, wordProtegido);
    }

    private byte[] protegerXlsxContraEdicion(byte[] xlsxBytes) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                workbook.getSheetAt(i).protectSheet(LOCKED_FILE_PASSWORD);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] convertirControlAWordProtegido(byte[] txtBytes) throws Exception {
        String contenido = new String(txtBytes, StandardCharsets.UTF_8);
        try (XWPFDocument documento = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String linea : contenido.split("\\r?\\n", -1)) {
                XWPFParagraph parrafo = documento.createParagraph();
                XWPFRun run = parrafo.createRun();
                run.setText(linea);
            }
            documento.enforceReadonlyProtection(LOCKED_FILE_PASSWORD, HashAlgorithm.sha256);
            documento.write(out);
            return out.toByteArray();
        }
    }

    private static String extraerNombreLimpio(String ruta) {
        String nombre = ruta.contains("/") ? ruta.substring(ruta.lastIndexOf("/") + 1) : ruta;
        return nombre.contains("__") ? nombre.substring(nombre.indexOf("__") + 2) : nombre;
    }
}
