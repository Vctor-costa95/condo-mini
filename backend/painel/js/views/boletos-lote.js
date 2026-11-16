// Envio de boletos em lote: a administradora solta o PDF do mês (ou vários PDFs) e o painel
// descobre o apartamento, o valor e o vencimento de cada boleto. Só as exceções pedem atenção.

import { api } from '../api.js';
import { analyzeFiles, itemPdf, parseBoletoLine, renderPages } from '../boletos.js';
import { condoUnits } from '../units.js';
import {
    busy, button, confirmDialog, drawer, formatBrl, formError, h, icon, input, monthLabel, monthRef, mount, select, toast
} from '../ui.js';

const MAX_PDF_BYTES = 15 * 1024 * 1024;
const PARALLEL = 3;
const brDate = (iso) => (iso ? iso.split('-').reverse().join('/') : '');

export function openBulkUpload({ condo, month, documents, onDone }) {
    const units = condoUnits(condo);
    const panel = drawer('Enviar boletos do mês', { eyebrow: condo.name, wide: true, onClose: () => sent && onDone?.() });
    const state = { month, items: [], phase: 'pick' };
    let sent = false;

    const monthPicker = select(
        [monthRef(1), monthRef(0), monthRef(-1)].map((ref) => [ref, monthLabel(ref)]),
        state.month,
        { 'aria-label': 'Mês dos boletos' }
    );
    monthPicker.addEventListener('change', () => { state.month = monthPicker.value; if (state.phase === 'review') drawReview(); });

    // ---------------------------------------------------------------- 1. escolher os arquivos

    function drawPick(error) {
        state.phase = 'pick';
        const fileInput = h('input', { type: 'file', accept: 'application/pdf,.pdf', multiple: true, hidden: true });
        fileInput.addEventListener('change', () => fileInput.files.length && start([...fileInput.files]));
        const zone = h('div', {
            class: 'dropzone dropzone-big', role: 'button', tabindex: '0', 'aria-label': 'Escolher os PDFs dos boletos',
            onclick: () => fileInput.click(),
            onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); fileInput.click(); } },
            ondragover: (event) => { event.preventDefault(); zone.classList.add('over'); },
            ondragleave: () => zone.classList.remove('over'),
            ondrop: (event) => { event.preventDefault(); zone.classList.remove('over'); const files = [...(event.dataTransfer?.files || [])]; if (files.length) start(files); }
        },
        icon('upload', 30),
        h('strong', {}, 'Arraste aqui os boletos do mês'),
        h('span', { class: 'muted small' }, 'O PDF com todos os boletos (um por página) ou vários PDFs, um por apartamento. O painel lê a linha digitável, o valor e o vencimento e descobre o apartamento de cada um.'),
        fileInput
        );
        mount(panel.body,
            error ? formError(error) : null,
            h('div', { class: 'form-grid' }, h('div', { class: 'span-2' }, fieldRow('Mês dos boletos', monthPicker))),
            zone,
            h('div', { class: 'bulk-how' },
                howStep('1', 'Solte os arquivos', 'Do jeito que o sistema de cobrança gera.'),
                howStep('2', 'Confira as exceções', 'Só o que o painel não teve certeza pede atenção.'),
                howStep('3', 'Envie', 'Cada morador recebe o boleto no app, com aviso no celular.')
            )
        );
        mount(panel.footer, button('Cancelar', { variant: 'ghost', onclick: () => panel.close() }));
    }

    function howStep(n, title, text) {
        return h('div', { class: 'bulk-step' }, h('span', { class: 'bulk-step-n' }, n), h('div', {}, h('strong', {}, title), h('small', {}, text)));
    }

    function fieldRow(label, control) {
        return h('div', { class: 'field' }, h('label', {}, label), control);
    }

    async function start(files) {
        const pdfs = files.filter((file) => file.type === 'application/pdf' || /\.pdf$/i.test(file.name));
        if (!pdfs.length) return drawPick('Escolha arquivos PDF.');
        const status = h('p', { class: 'muted' }, 'Abrindo os arquivos...');
        const bar = h('span', {});
        mount(panel.body, h('div', { class: 'bulk-reading' },
            h('span', { class: 'spinner' }),
            h('strong', {}, `Lendo ${pdfs.length === 1 ? pdfs[0].name : `${pdfs.length} arquivos`}`),
            status,
            h('div', { class: 'progress' }, bar)
        ));
        mount(panel.footer);
        try {
            state.items = await analyzeFiles(pdfs, units, ({ file, page, total, fileIndex, files: count }) => {
                status.textContent = count > 1 ? `${file}: página ${page} de ${total} (arquivo ${fileIndex + 1} de ${count})` : `Página ${page} de ${total}`;
                const share = count > 1 ? (fileIndex + page / total) / count : page / total;
                bar.style.width = `${Math.round(share * 100)}%`;
            });
        } catch (error) {
            return drawPick(`Não deu para ler os arquivos: ${error.message}`);
        }
        if (!state.items.length) return drawPick('Nenhuma página encontrada nesses arquivos.');
        drawReview();
    }

    // ---------------------------------------------------------------- 2. conferir

    function problemsOf(item) {
        if (item.skip) return [];
        const list = [];
        if (item.problem) list.push(item.problem);
        if (!item.unit) list.push(item.candidates?.length > 1 ? 'Mais de um apartamento possível: escolha.' : 'Apartamento não identificado: escolha.');
        if (!item.line) list.push('Sem linha digitável: digite.');
        else {
            if (!(item.line.amount > 0) && !(item.amount > 0)) list.push('O boleto não traz o valor: informe.');
            if (!item.line.dueDate && !item.dueDate) list.push('O boleto não traz o vencimento: informe.');
        }
        const same = state.items.filter((other) => !other.skip && other.unit && item.unit && other.unit.label === item.unit.label);
        if (same.length > 1) list.push(`${item.unit.label} aparece em ${same.length} boletos.`);
        return list;
    }

    const existing = (unit) => unit && documents.find((doc) => doc.unit === unit.label && doc.reference === state.month);

    function drawReview() {
        state.phase = 'review';
        const active = state.items.filter((item) => !item.skip);
        const blocked = active.filter((item) => problemsOf(item).length);
        const doubt = active.filter((item) => !problemsOf(item).length && item.confidence === 'low');
        const replacing = active.filter((item) => existing(item.unit));
        const ready = active.length - blocked.length;
        const missing = units.filter((unit) => !active.some((item) => item.unit?.label === unit.label) && !existing(unit));

        const order = (item) => (item.skip ? 3 : problemsOf(item).length ? 0 : item.confidence === 'low' ? 1 : 2);
        const rows = [...state.items].sort((a, b) => order(a) - order(b));

        mount(panel.body,
            h('div', { class: 'form-grid' }, h('div', { class: 'span-2' }, fieldRow('Mês dos boletos', monthPicker))),
            h('div', { class: 'bulk-summary' },
                summary('check', ready, ready === 1 ? 'pronto' : 'prontos', 'success'),
                blocked.length ? summary('alert', blocked.length, 'para resolver', 'danger') : null,
                doubt.length ? summary('search', doubt.length, 'para conferir', 'warning') : null,
                replacing.length ? summary('file', replacing.length, `já ${replacing.length === 1 ? 'tem' : 'têm'} boleto (substitui)`, 'neutral') : null
            ),
            missing.length
                ? h('p', { class: 'muted small' }, `${missing.length} ${missing.length === 1 ? 'apartamento fica' : 'apartamentos ficam'} sem boleto de ${monthLabel(state.month).toLowerCase()}: `,
                    missing.slice(0, 8).map((unit) => unit.label.replace('Apto ', '')).join(', '), missing.length > 8 ? '...' : '')
                : null,
            h('div', { class: 'list bulk-list' }, rows.map(row)),
            h('div', {}, button('Trocar os arquivos', { variant: 'ghost', small: true, iconName: 'upload', onclick: () => drawPick() }))
        );
        const send = button(`Enviar ${ready} ${ready === 1 ? 'boleto' : 'boletos'}`, { iconName: 'upload', disabled: !ready || blocked.length > 0, onclick: () => sendAll() });
        mount(panel.footer,
            blocked.length ? h('span', { class: 'spacer muted small' }, `Resolva ou deixe de fora ${blocked.length === 1 ? 'o boleto marcado' : `os ${blocked.length} boletos marcados`}.`) : h('span', { class: 'spacer' }),
            button('Cancelar', { variant: 'ghost', onclick: () => panel.close() }),
            send
        );
    }

    function summary(iconName, value, label, tone) {
        return h('div', { class: `bulk-chip tone-${tone}` }, icon(iconName, 16), h('strong', {}, String(value)), ` ${label}`);
    }

    function row(item) {
        const problems = problemsOf(item);
        const unitSelect = select(
            [['', 'Escolha o apartamento'], ...units.map((unit) => [unit.label, unit.label.replace('Apto ', '')])],
            item.unit?.label || '',
            { 'aria-label': `Apartamento do boleto de ${item.fileName}`, class: 'input select select-compact' }
        );
        unitSelect.addEventListener('change', () => {
            item.unit = units.find((unit) => unit.label === unitSelect.value) || null;
            item.confidence = item.unit ? 'high' : null;
            drawReview();
        });
        const source = item.wholeFile ? item.fileName : `${item.fileName} · pág. ${item.pages.map((p) => p + 1).join(', ')}`;
        const tone = item.skip ? 'skipped' : problems.length ? 'danger' : item.confidence === 'low' ? 'warning' : 'ok';
        // O que precisou de atenção continua com o "Ver boleto" depois de resolvido, para conferir.
        if (tone === 'danger' || tone === 'warning') item.flagged = true;
        return h('div', { class: `list-row bulk-row ${tone}` },
            h('span', { class: 'bulk-state' }, icon(tone === 'ok' ? 'check' : tone === 'skipped' ? 'close' : 'alert', 16)),
            h('div', { class: 'grow' },
                h('div', { class: 'bulk-main' },
                    unitSelect,
                    item.flagged
                        ? button(item.previewOpen ? 'Fechar boleto' : 'Ver boleto', {
                            variant: 'secondary', small: true, iconName: item.previewOpen ? 'close' : 'file',
                            onclick: () => { item.previewOpen = !item.previewOpen; drawReview(); }
                        })
                        : null,
                    item.line
                        ? h('span', { class: 'bulk-money' },
                            item.line.amount > 0 ? formatBrl(item.line.amount) : amountInput(item),
                            ' · ',
                            item.line.dueDate ? `vence ${brDate(item.line.dueDate)}` : dueInput(item))
                        : lineInput(item)
                ),
                h('small', {}, source,
                    existing(item.unit) && !item.skip ? ' · substitui o PDF já enviado' : '',
                    item.confidence === 'low' && !problems.length && !item.skip ? ' · apartamento achado só por um número no texto: confira' : ''),
                problems.length ? h('p', { class: 'bulk-problem' }, problems.join(' ')) : null,
                item.previewOpen ? previewOf(item) : null
            ),
            h('button', {
                type: 'button', class: 'icon-btn', title: item.skip ? 'Incluir de novo' : 'Deixar de fora',
                'aria-label': item.skip ? `Incluir ${source}` : `Deixar de fora ${source}`,
                onclick: () => { item.skip = !item.skip; drawReview(); }
            }, icon(item.skip ? 'plus' : 'close', 16))
        );
    }

    /**
     * As páginas deste boleto desenhadas logo abaixo da linha, para ler o apartamento no
     * próprio PDF. Desenha uma vez e guarda: a lista é redesenhada a cada escolha.
     */
    function previewOf(item) {
        if (!item.previewEl) {
            item.previewEl = h('div', { class: 'bulk-preview' }, h('span', { class: 'spinner' }));
            renderPages(item.bytes, item.pages, 560)
                .then((canvases) => mount(item.previewEl, canvases))
                .catch((error) => mount(item.previewEl, formError(`Não deu para mostrar o boleto: ${error.message}`)));
        }
        return item.previewEl;
    }

    function lineInput(item) {
        const field = input({ placeholder: 'Digite a linha digitável (47 números)', inputmode: 'numeric', class: 'input input-compact line-input', 'aria-label': 'Linha digitável' });
        field.addEventListener('change', () => {
            const parsed = parseBoletoLine(field.value);
            if (!parsed) return toast('Linha digitável inválida: confira os 47 números.', 'danger');
            item.line = parsed;
            drawReview();
        });
        return field;
    }

    function amountInput(item) {
        const field = input({ placeholder: 'Valor', inputmode: 'decimal', value: item.amount ? String(item.amount).replace('.', ',') : '', class: 'input input-compact money-input', 'aria-label': 'Valor do boleto' });
        field.addEventListener('change', () => { item.amount = Number(field.value.replace(/\./g, '').replace(',', '.')) || 0; drawReview(); });
        return field;
    }

    function dueInput(item) {
        const field = input({ type: 'date', value: item.dueDate || '', class: 'input input-compact', 'aria-label': 'Vencimento' });
        field.addEventListener('change', () => { item.dueDate = field.value; drawReview(); });
        return field;
    }

    // ---------------------------------------------------------------- 3. enviar

    async function sendAll() {
        const queue = state.items.filter((item) => !item.skip && !item.sent);
        const replacing = queue.filter((item) => existing(item.unit)).length;
        if (replacing) {
            const ok = await confirmDialog({
                title: `Substituir ${replacing} ${replacing === 1 ? 'boleto' : 'boletos'}?`,
                message: `${replacing === 1 ? 'Um apartamento já tem' : `${replacing} apartamentos já têm`} PDF de ${monthLabel(state.month).toLowerCase()}. O novo entra no lugar.`,
                confirmLabel: 'Substituir e enviar'
            });
            if (!ok) return;
        }
        state.phase = 'sending';
        const status = new Map(queue.map((item) => [item.id, 'waiting']));
        const progressBar = h('span', {});
        const counter = h('strong', {});
        const list = h('div', { class: 'list bulk-list' });
        mount(panel.body, h('div', { class: 'bulk-reading' }, counter, h('div', { class: 'progress' }, progressBar)), list);
        mount(panel.footer);

        const draw = () => {
            const finished = [...status.values()].filter((s) => s === 'ok' || s === 'error').length;
            counter.textContent = `Enviando ${finished} de ${queue.length}...`;
            progressBar.style.width = `${Math.round((finished / queue.length) * 100)}%`;
            mount(list, queue.map((item) => {
                const s = status.get(item.id);
                return h('div', { class: `list-row bulk-row ${s === 'ok' ? 'ok' : s === 'error' ? 'danger' : ''}` },
                    h('span', { class: 'bulk-state' }, s === 'ok' ? icon('check', 16) : s === 'error' ? icon('alert', 16) : h('span', { class: 'spinner spinner-s' })),
                    h('div', { class: 'grow' }, h('strong', {}, item.unit.label), item.error ? h('p', { class: 'bulk-problem' }, item.error) : null)
                );
            }));
        };
        draw();

        const work = [...queue];
        async function worker() {
            while (work.length) {
                const item = work.shift();
                status.set(item.id, 'sending');
                try {
                    const pdf = await itemPdf(item);
                    if (pdf.size > MAX_PDF_BYTES) throw new Error('PDF passa de 15 MB.');
                    const form = new FormData();
                    form.append('condoId', condo.id);
                    form.append('unit', item.unit.label);
                    form.append('reference', state.month);
                    form.append('barcode', item.line.line);
                    if (item.amount) form.append('amount', String(item.amount));
                    if (item.dueDate) form.append('dueDate', item.dueDate);
                    form.append('file', pdf, pdf.name);
                    await api.upload('unit-documents', form);
                    item.sent = true;
                    item.error = null;
                    status.set(item.id, 'ok');
                    sent = true;
                } catch (error) {
                    item.error = error.message;
                    status.set(item.id, 'error');
                }
                draw();
            }
        }
        await Promise.all(Array.from({ length: Math.min(PARALLEL, queue.length) }, worker));

        const failed = queue.filter((item) => !item.sent);
        const ok = queue.length - failed.length;
        counter.textContent = failed.length
            ? `${ok} enviados, ${failed.length} com erro.`
            : `${ok} ${ok === 1 ? 'boleto enviado' : 'boletos enviados'}. Os moradores já foram avisados.`;
        const retry = button(`Tentar de novo os ${failed.length}`, { iconName: 'upload', onclick: (event) => busy(event.currentTarget, sendAll) });
        mount(panel.footer,
            h('span', { class: 'spacer' }),
            button(failed.length ? 'Fechar' : 'Concluir', { variant: failed.length ? 'ghost' : 'primary', onclick: () => panel.close() }),
            failed.length ? retry : null
        );
        if (!failed.length) toast(`${ok} ${ok === 1 ? 'boleto enviado' : 'boletos enviados'} para ${monthLabel(state.month).toLowerCase()}.`);
    }

    drawPick();
}
