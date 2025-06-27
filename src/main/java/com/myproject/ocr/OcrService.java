package com.myproject.ocr;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.javacpp.indexer.FloatRawIndexer;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Service
public class OcrService {

    public ResponseEntity<List<String>> getOcr(PDDocument document, ArrayList<Integer> pages) throws IOException {
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        List<String> links = new ArrayList<>();

        // navega pelas :paginas do :document
        for (int pageNum : pages) {
            // valida se não é um numero negativo
            if (pageNum < 1 || pageNum > document.getNumberOfPages()) {
                System.out.println("Página " + pageNum + " inválida, ignorando.");
                continue;
            }

            // extraindo pagina :pageNum
            BufferedImage bim = pdfRenderer.renderImageWithDPI(pageNum - 1, 300);
            BufferedImage sharpImage = sharp(bim);

            String text = ocr(sharpImage);

            // cria um PDF individual com o texto
            String fileName = "ocr_pagina_" + pageNum + "_" + System.currentTimeMillis() + ".pdf";
            File paste = new File("src/main/resources/static/ocr/");
            if (!paste.exists()) paste.mkdirs();

            File pdf = new File(paste, fileName);
            toPdf(text, pdf);

            // gera link para efetuar download do pdf
            String url = "http://localhost:8080/ocr/" + fileName;
            links.add(url);
        }

        document.close();
        return ResponseEntity.ok(links);
    }


    // metodo basico, para extrair texto
    public static String ocr(BufferedImage image) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("por");
        tesseract.setOcrEngineMode(1);

        try {
            String result = tesseract.doOCR(image);
            return "Texto detectado:\n" + cleanText(result);
        } catch (TesseractException e) {
            return "Erro ao fazer OCR: " + e.getMessage();
        }
    }

    // deixa a imagem mais nitida
    public static BufferedImage sharp(BufferedImage image) {
        Mat mat = bufferedImageToMat(image);

        // Converte para escala de cinza
        Mat gray = new Mat();
        cvtColor(mat, gray, COLOR_BGR2GRAY);

        // Kernel de nitidez
        Mat kernel = new Mat(3, 3, CV_32F);
        FloatRawIndexer indexer = kernel.createIndexer();
        indexer.put(0, 0,  0f);  indexer.put(0, 1, -1f);  indexer.put(0, 2,  0f);
        indexer.put(1, 0, -1f);  indexer.put(1, 1,  9f);  indexer.put(1, 2, -1f);
        indexer.put(2, 0,  0f);  indexer.put(2, 1, -1f);  indexer.put(2, 2,  0f);
        indexer.release();

        Mat result = new Mat();
        filter2D(gray, result, gray.depth(), kernel);

        return matToBufferedImage(result);
    }

    // converte a imagem para Mat, para que possa ser usada o methodo nitidez
    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage convertedImg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        convertedImg.getGraphics().drawImage(bi, 0, 0, null);

        byte[] pixels = ((DataBufferByte) convertedImg.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CV_8UC3);
        mat.data().put(pixels);
        return mat;
    }

    // retorna a imagem nitida para o method getOcr
    private static BufferedImage matToBufferedImage(Mat mat) {
        int type = (mat.channels() == 1) ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = new byte[mat.cols() * mat.rows() * mat.channels()];
        mat.data().get(data);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
        return image;
    }

    // limpa o texto a partir de alguns casos
    public static String cleanText(String textOCR) {
        StringBuilder sb = new StringBuilder();
        String[] lines = textOCR.split("\\r?\\n");

        int numLine = 1; // contador de linha


        for (String line : lines) {
            String clean = line.trim();

            // todos os sout são log para debug
            System.out.println("📝 Linha " + numLine + ": \"" + clean + "\"");

            // limpa tudo que está entre os colchetes []
            clean = clean.replaceAll("[=>'\"—!]", "");

            // caso de poucos caracteres
            if (clean.length() < 6) {
                System.out.println("⛔ Rejeitada (muito curta: " + clean.length() + " caracteres)");
                numLine++;
                continue;
            }



            // caso de letras muito repetidas
            String lineWhitoutNumbers = clean.replaceAll("[0-9]", "");

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(.)\\1{2,}");
            java.util.regex.Matcher m = p.matcher(lineWhitoutNumbers);

            boolean reject = false;

            while (m.find()) {
                String repeated = m.group(1);
                int repeatedQuantity = m.group(0).length();

                // Ignorar repetições de espaço e ponto
                if (repeated.equals(" ") || repeated.equals(".")) {
                    System.out.println("ℹ Ignorado: repetição de '" + repeated + "' (" + repeatedQuantity + " vezes)");
                    continue;
                }

                System.out.println("⛔ Rejeitada (repetição exagerada: caractere '" + repeated + "' repetido " + repeatedQuantity + " vezes seguidas)");
                reject = true;
            }

            // caso de muitas palavras pequenas e seguidas
            // caso de muitas palavras pequenas e seguidas (incluindo números)
            String[] words = clean.split("\\s+");
            int maxConsecutive = 0;
            int currentConsecutive = 0;

            for (String w : words) {
                // considera palavra pequena se for tamanho 1 ou for só números
                if (w.length() <= 1 || w.matches("\\d+")) {
                    currentConsecutive++;
                    if (currentConsecutive > maxConsecutive) {
                        maxConsecutive = currentConsecutive;
                    }
                } else {
                    currentConsecutive = 0;
                }
            }

            // ignora rejeição se houver pelo menos uma palavra grande (6+ caracteres)
            boolean hasBigWord = Arrays.stream(words)
                    .anyMatch(p1 -> p1.length() >= 6);

            if (!hasBigWord && maxConsecutive > 2) {
                System.out.println("⛔ Rejeitada (mais de 2 palavras pequenas consecutivas: " + maxConsecutive + ")");
                numLine++;
                continue;
            }



            if (reject) {
                numLine++;
                continue;
            }



            System.out.println("✅ Mantida");
            sb.append(clean).append("\n");
            numLine++;
        }

        return sb.toString();
    }

    // salva o texto em um arquivo pdf
    private void toPdf(String text, File fileDestination) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // add pagina
            PDPage page = new PDPage();
            doc.addPage(page);

            // formatações
            PDPageContentStream content = new PDPageContentStream(doc, page);
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 12);
            content.setLeading(14.5f);
            content.newLineAtOffset(50, 700);

            String[] linhas = text.split("\\r?\\n");
            int linhasPorPagina = 45;
            int cont = 0;

            // preenche linha por linha
            for (String linha : linhas) {
                if (cont >= linhasPorPagina) {
                    content.endText();
                    content.close();

                    page = new PDPage();
                    doc.addPage(page);
                    content = new PDPageContentStream(doc, page);
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 12);
                    content.setLeading(14.5f);
                    content.newLineAtOffset(50, 700);
                    cont = 0;
                }

                content.showText(linha);
                content.newLine();
                cont++;
            }

            content.endText();
            content.close();

            doc.save(fileDestination);
        }
    }



}
