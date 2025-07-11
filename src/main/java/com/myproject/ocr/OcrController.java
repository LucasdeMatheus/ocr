package com.myproject.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/ocr")
public class OcrController {

    @Autowired
    private OcrService ocrService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> getFileOcr(
            @RequestPart("file") MultipartFile file,
            @RequestParam("data") String dataJson) throws IOException {

        // Converte JSON string para OcrRequest
        ObjectMapper mapper = new ObjectMapper();
        OcrRequest data = mapper.readValue(dataJson, OcrRequest.class);

        PDDocument document = PDDocument.load(file.getInputStream());
        Data dados = new Data(document, new ArrayList<>(data.getPaginas()));

        return ocrService.getOcr(dados.document(), dados.paginas());
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String filename) throws IOException {
        Path path = Paths.get("src/main/resources/static/ocr").resolve(filename).normalize();

        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @PostMapping("/corrrect")
    public ResponseEntity<Map<String, Object>> getSuggestions(@RequestPart("file") MultipartFile file) {
        try {
            // 1. Extrair texto do PDF (ou da imagem dentro do PDF) - adapte conforme seu método
            String extractedText = ocrService.toStringOf(file); // ou método que você tiver para extrair texto do MultipartFile

            // 2. Processar o texto extraído para gerar sugestões/correções
            Map<String, Object> result = ocrService.cleanText(extractedText);

            // 3. Retorna o resultado como JSON
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // Retorna erro com mensagem no corpo
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Erro ao processar arquivo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

}

