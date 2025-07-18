﻿
# 🧾 OCR - Leitura e Correção de Texto em PDFs

Este serviço REST permite realizar OCR (Reconhecimento Óptico de Caracteres) em arquivos PDF com ou sem imagens escaneadas. Além de extrair o texto, ele identifica palavras suspeitas e sugere correções baseadas em dicionário e palavras confiáveis.

---

## 📌 Endpoints Disponíveis

---

### 📤 `/ocr` – Extração de Texto de Páginas PDF

**Método:** `POST`

Realiza OCR nas páginas indicadas de um arquivo PDF e retorna links para download de arquivos gerados com o texto extraído.

#### 🧾 URL

```
POST http://localhost:8080/ocr
```

#### 📥 Parâmetros

| Nome  | Tipo           | Descrição                                                       |
|-------|----------------|-------------------------------------------------------------------|
| file  | MultipartFile  | Arquivo PDF a ser processado                                     |
| data  | String (JSON)  | JSON com a lista de páginas para extrair. Exemplo: `{"paginas":[1,3,5]}` |

#### 🧪 Exemplo com `curl`

```bash
curl -X POST http://localhost:8080/ocr \
  -F "file=@/caminho/para/documento.pdf" \
  -F "data={\"paginas\":[1,3,5]}"
```

#### ✅ Resposta

```json
[
  "http://localhost:8080/ocr/ocr_pagina_1_1750994871069.pdf",
  "http://localhost:8080/ocr/ocr_pagina_3_1750994878806.pdf",
  "http://localhost:8080/ocr/ocr_pagina_5_1750994879801.pdf"
]
```

#### 📁 Localização dos PDFs

Os arquivos são gerados no diretório configurado pelo backend e expostos publicamente na rota:

```
http://localhost:8080/ocr/{nome_do_arquivo_gerado.pdf}
```
diretório:
```
src\main\resources\static\ocr
```

---

### 🛠️ `/corrrect` – Análise e Sugestão de Correção de Texto

**Método:** `POST`

Recebe um arquivo PDF, extrai o texto via OCR, identifica palavras suspeitas e retorna sugestões para correção.

#### 🧾 URL

```
POST http://localhost:8080/corrrect
```

#### 📥 Parâmetros

| Nome  | Tipo           | Descrição                                         |
|-------|----------------|---------------------------------------------------|
| file  | MultipartFile  | Arquivo PDF contendo texto a ser analisado       |

#### 🧪 Exemplo com `curl`

```bash
curl -X POST http://localhost:8080/corrrect \
  -F "file=@/caminho/para/arquivo.pdf"
```

#### ✅ Resposta

```json
{
  "original": "Texto extraído do PDF...",
  "corrections": {
    "fic": ["ficha", "física", "fica"],
    "inttiituicao": ["instituição", "instrução"]
  }
}
```

---

## 🔧 Tecnologias Utilizadas

- **Tesseract OCR** (`tess4j`) – para reconhecimento de texto em imagens
- **Apache PDFBox** – para leitura de páginas de PDFs
- **OpenCV** (opcional) – para aplicar nitidez às imagens antes do OCR
- **Apache Commons Text** – cálculo da distância de Levenshtein
- **Dicionário UTF-8 personalizado** – palavras válidas para sugestão
- **Spring Boot (Java)** – backend REST estruturado

---

## 🔍 Lógica de Correção

- O texto é separado em palavras.
- Palavras com alta frequência ou no dicionário são consideradas confiáveis.
- As demais são consideradas suspeitas.
- Para cada palavra suspeita:
    - Sugestões são buscadas primeiro nas palavras confiáveis.
    - Depois, no dicionário.
    - A distância de Levenshtein é usada para ordenar as sugestões.
    - Até 5 sugestões por palavra são retornadas.
- O texto original **não é modificado** — a decisão de corrigir é do front-end.

---

## ℹ️ Observações

- Apenas letras são consideradas nas análises (números e símbolos são descartados).
- O endpoint `/ocr` permite escolher páginas específicas para extração.
- O endpoint `/corrrect` retorna o texto original + sugestões para cada palavra.
