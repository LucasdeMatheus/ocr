package com.myproject.ocr;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.javacpp.indexer.FloatRawIndexer;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Service
public class OcrService {

    // method principal, extrai texto e converte em pdf
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


    // metodo basico, para extrair texto de imagem e arquivo
    public String ocr(BufferedImage image) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("por");
        tesseract.setOcrEngineMode(1);

        try {
            String result = tesseract.doOCR(image);

            return "Texto detectado:\n" + result;
        } catch (TesseractException e) {
            return "Erro ao fazer OCR: " + e.getMessage();
        }
    }


    // deixa a imagem mais nitida
    public BufferedImage sharp(BufferedImage image) {
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

    // limpa o texto (linhas e simbolos especiais) a partir de alguns casos
    public Map<String, Object> cleanText(String textOCR) throws IOException {
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

            // caso de muitas palavras pequenas e seguidas (incluindo números)
            String[] words = clean.split("\\s+");
            int maxConsecutive = 0;
            int currentConsecutive = 0;

            for (String w : words) {
                // considera palavra pequena se for tamanho 1 ou for só números
                if (w.length() <= 2 || w.matches("\\d+")) {
                    currentConsecutive++;
                    if (currentConsecutive > maxConsecutive) {
                        maxConsecutive = currentConsecutive;
                    }
                } else {
                    currentConsecutive = 0;
                }
            }




            // ignora rejeição se houver pelo menos uma palavra grande (6+ caracteres)
            long countBigWords = Arrays.stream(words)
                    .filter(w -> w.length() >= 6)
                    .count();

            if (countBigWords < 2 && maxConsecutive > 2) {
                System.out.println("⛔ Rejeitada (mais de 2 palavras pequenas consecutivas e menos de 2 grandes)");
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

        return textCorrector(sb.toString());
    }

    // salva o texto em um arquivo pdf
    public void toPdf(String text, File fileDestination) throws IOException {
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

    // tranforma pdf em String
    public String toStringOf(MultipartFile file) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("por");

        StringBuilder result = new StringBuilder();

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String text = tesseract.doOCR(image);
                result.append(text).append("\n\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "Erro ao processar PDF com OCR: " + e.getMessage();
        }
    }

    // identifica e chama meetodo para sugerir correções
    public Map<String, Object> textCorrector(String textOCR) throws IOException {
        Map<String, Integer> count = new HashMap<>();
        List<String> words = new ArrayList<>();
        Set<String> dictionary = loadDictionary("src/main/resources/static/ocr/br-utf8.txt");

        // pecorree palavras separadas por espaço
        for (String word : textOCR.split("\\s+")) {
            String normalized = Normalizer.normalize(word, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase();

            // ignora palavras acompanhadas de numeros
            if (normalized.matches("^[^0-9]+$")) {
                words.add(normalized);
            } else {
                System.out.println("🔕 Ignorada da contagem: " + word);
            }
        }


        // ignora letras isoladas
        for (String word : words) {
            if (word.length() <= 1) continue;
            count.put(word, count.getOrDefault(word, 0) + 1);
        }

        Set<String> reliableWords = new HashSet<>();
        Set<String> suspiciousWords = new HashSet<>();

        // considera palavras muito repetidas(que possa estar certa, mas não aparece no dicionario) e palavras que estão no dicionário
        for (Map.Entry<String, Integer> p : count.entrySet()) {
            String word = p.getKey();
            int frequency = p.getValue();

            String normalizedWord = Normalizer.normalize(word, Normalizer.Form.NFD)
                    .replaceAll("[\\p{M}\\p{P}\\p{S}]", "");

            if (normalizedWord.isEmpty()) continue;

            boolean isInDictionary = dictionary.stream()
                    .map(d -> Normalizer.normalize(d, Normalizer.Form.NFD).replaceAll("\\p{M}", ""))
                    .anyMatch(dNorm -> dNorm.equalsIgnoreCase(normalizedWord));

            if (frequency > 1 || isInDictionary) {
                reliableWords.add(normalizedWord);
                System.out.println("✅ Palavra confiável: " + normalizedWord + " (" + frequency + " vezes)");
            } else {
                System.out.println("🟡 Palavra suspeita: " + normalizedWord);
                suspiciousWords.add(normalizedWord);
            }
        }

        Map<String, List<String>> corrections = suggestMultipleCorrections(suspiciousWords, reliableWords, dictionary);

        // retorna um mapa com o texto original e as sugestões
        Map<String, Object> result = new HashMap<>();
        result.put("original", textOCR);
        result.put("corrections", corrections);

        return result;
    }




    // sugere até 5 palavras, 5 encontradas no texto(context) e 5 do dicionario
    public Map<String, List<String>> suggestMultipleCorrections(Set<String> suspiciousWords, Set<String> reliableWords, Set<String> dictionary) {
        Map<String, List<String>> suggestions = new HashMap<>();
        LevenshteinDistance lv = new LevenshteinDistance();

        for (String word : suspiciousWords) {
            String wordWithoutAccent = Normalizer.normalize(word, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase();

            // Primeiro tenta no conjunto de confiaveis
            Map<Integer, List<String>> reliablesMap = new TreeMap<>();
            int totalSuggested = 0;

            boolean encontrouDistanciaBoa = false;

            for (int currentDistance = 0; currentDistance <= 10 && totalSuggested < 5; currentDistance++) {
                for (String term : reliableWords) {
                    if (totalSuggested >= 5) break;

                    String termWithoutAccent = Normalizer.normalize(term, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "")
                            .toLowerCase();

                    int distance = lv.apply(wordWithoutAccent, termWithoutAccent);

                    // Se encontrou uma sugestão com distância até 2, libera qualquer diff
                    if (distance <= 2) encontrouDistanciaBoa = true;

                    int diff = termWithoutAccent.length() - wordWithoutAccent.length();

                    // Só bloqueia diferenças grandes se ainda não achou nenhuma boa
                    if (!encontrouDistanciaBoa && (diff < -2 || diff > 4)) continue;

                    if (distance != currentDistance) continue;

                    List<String> list = reliablesMap.computeIfAbsent(distance, k -> new ArrayList<>());
                    list.add(term);
                    totalSuggested++;
                }
            }


            // Agora no dicionário
            Map<Integer, List<String>> dictionaryMap = new TreeMap<>();
            totalSuggested = 0;

            for (int currentDistance = 0; currentDistance <= 10 && totalSuggested < 5; currentDistance++) {
                for (String term : dictionary) {
                    if (totalSuggested >= 5) break; // garante o limite global

                    String termWithoutAccent = Normalizer.normalize(term, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "")
                            .toLowerCase();
                    int distance = lv.apply(wordWithoutAccent, termWithoutAccent);
                    if (distance != currentDistance) continue;
                    if (distance <= 2) encontrouDistanciaBoa = true;

                    int diff = termWithoutAccent.length() - wordWithoutAccent.length();

                    if (!encontrouDistanciaBoa && (diff < -2 || diff > 4)) continue;

                    List<String> list = dictionaryMap.computeIfAbsent(distance, k -> new ArrayList<>());

                    list.add(term);
                    totalSuggested++; // incrementa corretamente
                }
            }



            List<String> reliablesList = new ArrayList<>();
            outerReliables:
            for (List<String> l : reliablesMap.values()) {
                for (String s : l) {
                    if (reliablesList.size() >= 5) break outerReliables;
                    reliablesList.add(s);
                }
            }

            List<String> dictionaryList = new ArrayList<>();
            outerDictionary:
            for (List<String> l : dictionaryMap.values()) {
                for (String i : l) {
                    if (dictionaryList.size() >= 5) break outerDictionary;
                    dictionaryList.add(i);
                }
            }

            // Log das sugestões
            System.out.println("🔎 Sugestões para palavra suspeita '" + word + "':");
            System.out.println("  Confiáveis: " + reliablesList);
            System.out.println("  Dicionário: " + dictionaryList);

            // Junta tudo e adiciona no mapa final
            List<String> combined = new ArrayList<>();
            combined.addAll(reliablesList);
            combined.addAll(dictionaryList);

            suggestions.put(word, combined);
        }

        return suggestions;
    }




    // dicionario
    public static Set<String> loadDictionary(String caminho) throws IOException {
        Set<String> dictionary = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new FileReader(caminho))) {
            String l;
            while ((l = r.readLine()) != null) {
                String p = l.trim().toLowerCase();
                if (!p.isEmpty()) dictionary.add(p);
            }
        }
        System.out.println("📘 Dicionário carregado: " + dictionary.size() + " palavras");
        return dictionary;
    }



}
