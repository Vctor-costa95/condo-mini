// Arquivo de retorno do banco (CNAB 240 ou 400): mostra o que vai ter baixa e,
// com a confirmação da administradora, dá baixa em todos de uma vez.

import { api } from '../api.js';
import { button, closeDrawer, drawer, formError, formatBrl, h, icon, monthLabel, mount, toast } from '../ui.js';

const MAX_RETURN_BYTES = 5 * 1024 * 1024;

/** "2026-11-18" → "18/11/2026" */
function formatDay(day) {
    if (!day) return 'sem data';
    const [y, m, d] = day.split('-');
    return `${d}/${m}/${y}`;
}

export function openBankReturn({ condo, onDone }) {
    const panel = drawer('Retorno do banco', { eyebrow: condo.name, wide: true });
    const errorBox = h('div', {});
    let file = null;

    function pickStep() {
        const fileInput = h('input', { type: 'file', accept: '.ret,.txt,.RET,.TXT,text/plain', hidden: true });
        const zone = h('div', {
            class: 'dropzone', role: 'button', tabindex: '0', 'aria-label': 'Escolher o arquivo de retorno',
            onclick: () => fileInput.click(),
            onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); fileInput.click(); } },
            ondragover: (event) => { event.preventDefault(); zone.classList.add('over'); },
            ondragleave: () => zone.classList.remove('over'),
            ondrop: (event) => {
                event.preventDefault();
                zone.classList.remove('over');
                const dropped = event.dataTransfer?.files?.[0];
                if (dropped) preview(dropped);
            }
        },
        icon('upload', 26),
        h('strong', {}, 'Arraste o arquivo de retorno aqui'),
        h('small', {}, 'O arquivo que o banco gera com os boletos pagos (.ret ou .txt, CNAB 240 ou 400). Primeiro você vê o que vai ter baixa.'),
        fileInput
        );
        fileInput.addEventListener('change', () => fileInput.files[0] && preview(fileInput.files[0]));
        mount(panel.footer);
        mount(panel.body, errorBox,
            h('section', { class: 'form-stack' },
                h('p', { class: 'muted' }, 'Baixe o retorno no internet banking da conta do condomínio (Cobrança → Arquivo retorno) e envie aqui. Cada pagamento é ligado ao boleto pelo nosso número e pelo valor.'),
                zone
            )
        );
    }

    function send(preview) {
        const form = new FormData();
        form.append('condoId', condo.id);
        if (preview) form.append('preview', '1');
        form.append('file', file, file.name);
        return api.upload('bills/bank-return', form);
    }

    async function preview(chosen) {
        mount(errorBox);
        if (chosen.size > MAX_RETURN_BYTES) return mount(errorBox, formError('Este arquivo é grande demais para um retorno do banco.'));
        file = chosen;
        mount(panel.body, h('div', { class: 'loading' }, h('span', { class: 'spinner' }), h('span', {}, `Lendo ${chosen.name}...`)));
        try {
            showResult(await send(true));
        } catch (error) {
            pickStep();
            mount(errorBox, formError(error.message));
        }
    }

    function row(item, tone, note) {
        return h('div', { class: 'list-row' },
            h('span', { class: `chip chip-${tone}` }, note),
            h('div', { class: 'grow' },
                h('strong', {}, item.unit ? `${item.unit} · ${monthLabel(item.reference)}` : `Nosso número ${item.ourNumber || '—'}`),
                h('small', {}, [formatBrl(item.amount), `pago em ${formatDay(item.paidAt)}`, item.unit ? null : `linha ${item.line} do arquivo`].filter(Boolean).join(' · '))
            )
        );
    }

    function showResult(result) {
        const { paid, alreadyPaid, unmatched, otherEntries } = result;
        const confirm = button(paid.length === 1 ? 'Dar baixa em 1 boleto' : `Dar baixa em ${paid.length} boletos`, {
            iconName: 'check',
            disabled: !paid.length,
            onclick: async () => {
                confirm.disabled = true;
                try {
                    const done = await send(false);
                    closeDrawer();
                    toast(done.paid.length === 1 ? '1 boleto marcado como pago.' : `${done.paid.length} boletos marcados como pagos.`, 'success');
                    onDone?.();
                } catch (error) {
                    confirm.disabled = false;
                    mount(errorBox, formError(error.message));
                }
            }
        });
        mount(panel.body, errorBox,
            h('section', { class: 'stats' },
                h('div', { class: 'card stat' }, h('p', { class: 'eyebrow' }, 'Para dar baixa'), h('p', { class: 'stat-value' }, String(paid.length)),
                    h('p', { class: 'stat-sub' }, formatBrl(paid.reduce((sum, item) => sum + item.amount, 0)))),
                h('div', { class: 'card stat' }, h('p', { class: 'eyebrow' }, 'Já estavam pagos'), h('p', { class: 'stat-value' }, String(alreadyPaid.length)),
                    h('p', { class: 'stat-sub' }, 'Nada muda neles')),
                h('div', { class: 'card stat' }, h('p', { class: 'eyebrow' }, 'Sem boleto'), h('p', { class: 'stat-value' }, String(unmatched.length)),
                    h('p', { class: 'stat-sub' }, unmatched.length ? 'Confira e dê baixa no apartamento' : 'Todos encontrados'))
            ),
            paid.length
                ? h('section', { class: 'form-stack' }, h('div', { class: 'section-title' }, h('h3', {}, 'Vão ter baixa')),
                    h('div', { class: 'list' }, paid.map((item) => row(item, 'success', 'Pago'))))
                : h('p', { class: 'muted' }, 'Nenhum pagamento novo neste arquivo para os boletos deste condomínio.'),
            unmatched.length
                ? h('section', { class: 'form-stack' },
                    h('div', { class: 'section-title' }, h('h3', {}, 'Sem boleto no sistema')),
                    h('p', { class: 'muted small' }, 'Pagamentos que não batem com nenhum boleto em aberto (valor ou nosso número diferentes, ou de outro condomínio). Se for de um apartamento, abra o apartamento em Unidades e use "Marcar como pago".'),
                    h('div', { class: 'list' }, unmatched.map((item) => row(item, 'warning', 'Conferir'))))
                : null,
            alreadyPaid.length
                ? h('section', { class: 'form-stack' }, h('div', { class: 'section-title' }, h('h3', {}, 'Já estavam pagos')),
                    h('div', { class: 'list' }, alreadyPaid.map((item) => row(item, 'neutral', 'Já pago'))))
                : null,
            otherEntries ? h('p', { class: 'muted small' }, otherEntries === 1
                ? '1 linha do arquivo não é pagamento (entrada de título, baixa, tarifa) e fica de fora.'
                : `${otherEntries} linhas do arquivo não são pagamentos (entrada de título, baixa, tarifa) e ficam de fora.`) : null
        );
        mount(panel.footer,
            button('Escolher outro arquivo', { variant: 'ghost', onclick: () => { file = null; pickStep(); } }),
            confirm
        );
    }

    pickStep();
}
