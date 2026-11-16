# Bibliotecas de terceiros do painel

Copiadas aqui porque a política de segurança do painel (CSP) só aceita scripts do próprio site.

| Arquivo | Pacote | Licença |
|---|---|---|
| `pdf.min.js`, `pdf.worker.min.js` | pdfjs-dist 4.10.38 (Mozilla), `build/*.mjs` renomeados para `.js` | Apache-2.0 (`LICENSE-pdfjs.txt`) |
| `pdf-lib.esm.min.js` | pdf-lib 1.17.1 | MIT (`LICENSE-pdf-lib.txt`) |

Usadas só no envio de boletos em lote (`js/boletos.js`): o pdf.js lê o texto das páginas e o pdf-lib separa um PDF por apartamento.
