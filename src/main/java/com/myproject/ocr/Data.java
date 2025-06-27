package com.myproject.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.ArrayList;

public record Data(PDDocument document, ArrayList<Integer> paginas) {
}
