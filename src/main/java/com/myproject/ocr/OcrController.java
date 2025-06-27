package com.myproject.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ocr")
public class OcrController {

    @Autowired
    private OcrService ocrService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> getOcr(
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


}

