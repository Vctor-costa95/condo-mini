// Leitura de boletos em PDF, tudo no navegador: a linha digitável (com valor e vencimento),
// o apartamento de cada boleto e a separação de um PDF com vários boletos em um por apartamento.
// A API confere a linha de novo; aqui é para mostrar a conferência antes de enviar.

let pdfjsPromise = null;
let pdfLibPromise = null;

function pdfjs() {
    pdfjsPromise ??= import('./vendor/pdf.min.js').then((lib) => {
        lib.GlobalWorkerOptions.workerSrc = new URL('./vendor/pdf.worker.min.js', import.meta.url).href;
        return lib;
    });
    return pdfjsPromise;
}

function pdfLib() {
    pdfLibPromise ??= import('./vendor/pdf-lib.esm.min.js');
    return pdfLibPromise;
}

// ------------------------------------------------------------------ linha digitável

function mod10(digits) {
    let sum = 0;
    let weight = 2;
    for (let i = digits.length - 1; i >= 0; i--) {
        const product = Number(digits[i]) * weight;
        sum += Math.floor(product / 10) + (product % 10);
        weight = weight === 2 ? 1 : 2;
    }
    return (10 - (sum % 10)) % 10;
}

function mod11(digits) {
    let sum = 0;
    let weight = 2;
    for (let i = digits.length - 1; i >= 0; i--) {
        sum += Number(digits[i]) * weight;
        weight = weight === 9 ? 2 : weight + 1;
    }
    const dv = 11 - (sum % 11);
    return dv === 0 || dv === 10 || dv === 11 ? 1 : dv;
}

const DAY = 24 * 60 * 60 * 1000;
const isoDate = (date) => date.toISOString().slice(0, 10);

/**
 * 47 dígitos de boleto bancário: confere os três campos (módulo 10) e o dígito geral
 * (módulo 11) e tira o valor e o vencimento. null quando não é uma linha válida.
 */
export function parseBoletoLine(text) {
    const d = String(text).replace(/\D/g, '');
    if (d.length !== 47) return null;
    if (mod10(d.slice(0, 9)) !== Number(d[9]) || mod10(d.slice(10, 20)) !== Number(d[20]) || mod10(d.slice(21, 31)) !== Number(d[31])) return null;
    const barcode = d.slice(0, 4) + d.slice(33, 47) + d.slice(4, 9) + d.slice(10, 20) + d.slice(21, 31);
    if (mod11(barcode) !== Number(d[32])) return null;
    const factor = Number(d.slice(33, 37));
    let dueDate = null;
    if (factor > 0) {
        // O fator recomeçou em 1000 no dia 22/02/2025: vale o ciclo mais perto de hoje.
        const candidates = [Date.UTC(1997, 9, 7) + factor * DAY];
        if (factor >= 1000) candidates.push(Date.UTC(2025, 1, 22) + (factor - 1000) * DAY);
        const now = Date.now();
        candidates.sort((a, b) => Math.abs(a - now) - Math.abs(b - now));
        dueDate = isoDate(new Date(candidates[0]));
    }
    return {
        digits: d,
        line: `${d.slice(0, 5)}.${d.slice(5, 10)} ${d.slice(10, 15)}.${d.slice(15, 21)} ${d.slice(21, 26)}.${d.slice(26, 32)} ${d[32]} ${d.slice(33)}`,
        amount: Number(d.slice(37)) / 100,
        dueDate
    };
}

/** Linhas digitáveis válidas no texto de uma página (sem repetir). */
export function findBoletoLines(text) {
    const found = new Map();
    // Formatada ("34191.79001 01043.510047 ..."), com espaços ou pontos variando.
    const formatted = /\d{5}[.\s]?\d{5}\s*\d{5}[.\s]?\d{6}\s*\d{5}[.\s]?\d{6}\s*\d\s*\d{14}/g;
    for (const match of text.matchAll(formatted)) {
        const parsed = parseBoletoLine(match[0]);
        if (parsed) found.set(parsed.digits, parsed);
    }
    if (!found.size) {
        // Sem formatação: qualquer sequência de 47 dígitos seguidos que feche os dígitos verificadores.
        for (const run of text.replace(/[.\s]/g, '').match(/\d{47,}/g) || []) {
            for (let i = 0; i + 47 <= run.length; i++) {
                const parsed = parseBoletoLine(run.slice(i, i + 47));
                if (parsed) found.set(parsed.digits, parsed);
            }
        }
    }
    return [...found.values()];
}

// ------------------------------------------------------------------ apartamento

const plain = (text) => String(text || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** "Bloco A" → "a"; "Torre 2" → "2"; "Edifício Sol" → "sol". */
function towerToken(towerName) {
    const words = plain(towerName).split(/[\s-]+/).filter(Boolean);
    return words[words.length - 1] || '';
}

/** O bloco aparece no texto perto do número? ("Bloco A", "Bl. A", "Torre A", "101-A", "A-101", "101A") */
function mentionsTower(text, number, tower) {
    const token = towerToken(tower);
    const full = plain(tower);
    if (!token) return false;
    const t = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const n = number.toLowerCase();
    const patterns = [
        new RegExp(`\\b(bloco|bl|torre|tr|ed|edificio|predio)\\.?\\s*${t}\\b`),
        new RegExp(`\\b${n}\\s*[-/]?\\s*${t}\\b`),
        new RegExp(`\\b${t}\\s*[-/]?\\s*${n}\\b`)
    ];
    return text.includes(full) || patterns.some((re) => re.test(text));
}

/**
 * Qual apartamento é este boleto? Procura primeiro "Apto 101", "Unidade 101" e afins
 * (alta confiança); se não houver, um número solto que seja de um único apartamento
 * (baixa confiança: pede conferência). Com o mesmo número em vários blocos, decide pelo bloco.
 */
export function matchUnit(rawText, units, { trustBareNumbers = false } = {}) {
    let text = plain(rawText)
        .replace(/\d{5}[.\s]?\d{5}\s*\d{5}[.\s]?\d{6}\s*\d{5}[.\s]?\d{6}\s*\d\s*\d{14}/g, ' ')
        .replace(/\b\d{1,2}\/\d{1,2}\/\d{2,4}\b/g, ' ')
        .replace(/\b\d{1,3}(\.\d{3})*,\d{2}\b/g, ' ')
        .replace(/\b\d{5}-?\d{3}\b/g, ' ');
    const byNumber = new Map();
    for (const unit of units) {
        const key = unit.number.toLowerCase();
        if (!byNumber.has(key)) byNumber.set(key, []);
        byNumber.get(key).push(unit);
    }
    const pick = (numbers, confidence) => {
        const hits = [...new Set(numbers)].flatMap((n) => byNumber.get(n.replace(/^0+(?=\d)/, '').toLowerCase()) || []);
        if (!hits.length) return null;
        if (hits.length === 1) return { unit: hits[0], confidence, candidates: hits };
        const withTower = hits.filter((unit) => mentionsTower(text, unit.number, unit.tower));
        if (withTower.length === 1) return { unit: withTower[0], confidence, candidates: hits };
        return { unit: null, confidence: 'low', candidates: hits };
    };
    const keyword = /\b(?:apto|apt|apartamento|ap|unidade|unid|und|uni|sala|casa|lote)\b\.?\s*(?:n[o°º.]*\s*)?:?\s*(t?\d{1,4})\b/g;
    const strong = [...text.matchAll(keyword)].map((m) => m[1]);
    // "101-A", "A-101", "101/B": número com o bloco grudado também conta como forte.
    const tokens = new Set(units.map((unit) => towerToken(unit.tower)).filter(Boolean));
    for (const token of tokens) {
        const t = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        for (const m of text.matchAll(new RegExp(`\\b(t?\\d{2,4})\\s*[-/]\\s*${t}\\b|\\b${t}\\s*[-/]\\s*(t?\\d{2,4})\\b`, 'g'))) strong.push(m[1] || m[2]);
    }
    const fromKeyword = pick(strong, 'high');
    if (fromKeyword) return fromKeyword;
    const bare = [...text.matchAll(/\b(t?\d{2,4})\b/g)].map((m) => m[1]);
    const fromBare = pick(bare, trustBareNumbers ? 'high' : 'low');
    return fromBare || { unit: null, confidence: null, candidates: [] };
}

// ------------------------------------------------------------------ PDFs

/** Texto de cada página do PDF. */
export async function readPdfText(bytes, onPage) {
    const lib = await pdfjs();
    const doc = await lib.getDocument({ data: bytes.slice(), isEvalSupported: false, disableFontFace: true, useSystemFonts: false }).promise;
    const pages = [];
    try {
        for (let n = 1; n <= doc.numPages; n++) {
            const page = await doc.getPage(n);
            const content = await page.getTextContent();
            pages.push(content.items.map((item) => item.str + (item.hasEOL ? '\n' : ' ')).join(''));
            page.cleanup();
            onPage?.(n, doc.numPages);
        }
    } finally {
        await doc.destroy();
    }
    return pages;
}

/**
 * Desenha as páginas pedidas em imagens (canvas), para ver o boleto dentro do painel,
 * sem depender de aba nova nem do leitor de PDF do navegador.
 */
export async function renderPages(bytes, pageIndexes, width) {
    const lib = await pdfjs();
    const doc = await lib.getDocument({ data: bytes.slice(), isEvalSupported: false }).promise;
    const ratio = window.devicePixelRatio || 1;
    const canvases = [];
    try {
        for (const index of pageIndexes) {
            const page = await doc.getPage(index + 1);
            const base = page.getViewport({ scale: 1 });
            const viewport = page.getViewport({ scale: (width / base.width) * ratio });
            const canvas = document.createElement('canvas');
            canvas.width = Math.floor(viewport.width);
            canvas.height = Math.floor(viewport.height);
            canvas.style.width = `${width}px`;
            canvas.setAttribute('role', 'img');
            canvas.setAttribute('aria-label', `Página ${index + 1} do boleto`);
            await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
            canvases.push(canvas);
            page.cleanup();
        }
    } finally {
        await doc.destroy();
    }
    return canvases;
}

/** Um PDF novo só com as páginas pedidas (índices a partir de 0). */
export async function extractPages(bytes, pageIndexes) {
    const { PDFDocument } = await pdfLib();
    const source = await PDFDocument.load(bytes, { ignoreEncryption: true });
    const out = await PDFDocument.create();
    const copied = await out.copyPages(source, pageIndexes);
    copied.forEach((page) => out.addPage(page));
    return new Blob([await out.save()], { type: 'application/pdf' });
}

/**
 * Página sem linha digitável: é o verso do boleto anterior, a não ser que diga com todas
 * as letras outro apartamento ("Unidade 906"). Aí é um boleto à parte, que veio sem linha,
 * e não pode ir de carona para o apartamento do anterior.
 */
function startsOwnBoleto(text, previous, units) {
    const own = matchUnit(text, units);
    if (own.confidence !== 'high' || !own.unit) return false;
    const before = matchUnit(previous.text, units);
    return !before.unit || before.unit.label !== own.unit.label;
}

/**
 * Lê os arquivos e devolve um item por boleto:
 * { id, file, fileName, pages, line, unit, confidence, candidates, problem }.
 * Num PDF com vários boletos, cada página com linha digitável começa um boleto novo e as
 * páginas seguintes sem linha (verso, instruções) vão junto com ele.
 */
export async function analyzeFiles(files, units, onProgress) {
    const items = [];
    let done = 0;
    for (const file of files) {
        const bytes = new Uint8Array(await file.arrayBuffer());
        let pages;
        try {
            pages = await readPdfText(bytes, (page, total) => onProgress?.({ file: file.name, page, total, fileIndex: done, files: files.length }));
        } catch {
            items.push({ id: crypto.randomUUID(), file, bytes, fileName: file.name, pages: [], line: null, unit: null, confidence: null, candidates: [], problem: 'Não deu para ler este PDF (protegido por senha ou corrompido).' });
            done++;
            continue;
        }
        const groups = [];
        pages.forEach((text, index) => {
            const lines = findBoletoLines(text);
            if (lines.length > 1) {
                groups.push({ pages: [index], text, lines, problem: `A página ${index + 1} tem ${lines.length} boletos. Gere o PDF com um boleto por página.` });
            } else if (lines.length === 1 || !groups.length || startsOwnBoleto(text, groups[groups.length - 1], units)) {
                groups.push({ pages: [index], text, lines });
            } else {
                const last = groups[groups.length - 1];
                last.pages.push(index);
                last.text += `\n${text}`;
            }
        });
        const single = groups.length === 1;
        for (const group of groups) {
            // Arquivo de um boleto só: o nome do arquivo também diz o apartamento ("101.pdf", "Bloco A - 1004.pdf").
            let match = matchUnit(group.text, units);
            if (single && (!match.unit || match.confidence !== 'high')) {
                const byName = matchUnit(file.name.replace(/\.pdf$/i, ''), units, { trustBareNumbers: true });
                if (byName.unit) match = byName;
            }
            items.push({
                id: crypto.randomUUID(),
                file,
                bytes,
                fileName: file.name,
                pages: group.pages,
                wholeFile: single,
                line: group.lines.length === 1 ? group.lines[0] : null,
                unit: match.unit,
                confidence: match.confidence,
                candidates: match.candidates,
                problem: group.problem || null
            });
        }
        done++;
    }
    return items;
}

/** O PDF que vai para o apartamento: o arquivo inteiro, ou só as páginas daquele boleto. */
export async function itemPdf(item) {
    if (item.wholeFile) return item.file;
    const blob = await extractPages(item.bytes, item.pages);
    const base = item.fileName.replace(/\.pdf$/i, '');
    return new File([blob], `${base} - p${item.pages[0] + 1}.pdf`, { type: 'application/pdf' });
}
