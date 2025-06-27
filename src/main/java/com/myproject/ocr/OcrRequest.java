package com.myproject.ocr;

import java.util.List;

public class OcrRequest {
    private List<Integer> paginas;

    // getter e setter obrigatórios
    public List<Integer> getPaginas() {
        return paginas;
    }

    public void setPaginas(List<Integer> paginas) {
        this.paginas = paginas;
    }
}
