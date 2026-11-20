// Unidades: boletos do mês, mapa de cada bloco e o detalhe do apartamento (PDF e moradores).

import { api } from '../api.js';
import { maxUploadBytes, maxUploadLabel } from '../state.js';
import { refreshSidebarBadge } from '../main.js';
import { allFloors, floorLabel, towerUnits, unitsOn } from '../units.js';
import { findBoletoLines, parseBoletoLine, readPdfText } from '../boletos.js';
import { openBulkUpload } from './boletos-lote.js';
import { openBankReturn } from './retorno-banco.js';
import {
    busy, button, confirmDialog, drawer, emptyState, formatBrl, formatBytes, formatDateTime, formError, h, icon,
    loading, monthLabel, monthRef, mount, select, shortMonthLabel, toast
} from '../ui.js';

const BILL_STATUS = { OPEN: 'Em aberto', OVERDUE: 'Vencido', PAID: 'Pago' };
const PAID_VIA = { MANUAL: 'pela administração', BANK_RETURN: 'pelo retorno do banco' };

/** "2026-11-18" → "18/11/2026" */
function formatDay(day) {
    const [y, m, d] = String(day || '').split('-');
    return d ? `${d}/${m}/${y}` : '';
}

export function renderUnits(container, { condo }) {
    let alive = true;
    const view = {
        month: monthRef(0),
        towerId: condo.towers[0]?.id || '',
        users: [],
        documents: [],
        // Boletos com valor e situação dos três meses da tela, por mês ("2026-11" → lista).
        bills: {},
        openUnit: null,
        refreshDrawer: null
    };
    const months = [
        [monthRef(1), 'Próximo mês'],
        [monthRef(0), 'Este mês'],
        [monthRef(-1), 'Mês passado']
    ];

    const page = h('div', { class: 'page' });
    mount(container, page);
    mount(page, header(), loading());

    function header() {
        return h('header', { class: 'page-header' },
            h('div', {},
                h('p', { class: 'eyebrow' }, condo.name),
                h('h1', {}, 'Unidades'),
                h('p', {}, 'Envie os boletos do mês de uma vez e cuide de quem mora em cada apartamento. Clique num apartamento para abrir o detalhe.')
            ),
            h('div', { class: 'page-actions' },
                button('Retorno do banco', {
                    variant: 'secondary',
                    iconName: 'check',
                    onclick: () => openBankReturn({
                        condo,
                        onDone: () => load().then(render).catch((error) => toast(error.message, 'danger'))
                    })
                }),
                button('Enviar boletos do mês', {
                    iconName: 'upload',
                    onclick: () => openBulkUpload({
                        condo, month: view.month, documents: view.documents,
                        onDone: () => load().then(render).catch((error) => toast(error.message, 'danger'))
                    })
                })
            )
        );
    }

    async function load() {
        const [users, documents, ...bills] = await Promise.all([
            api.get('users', { condoId: condo.id }),
            api.get('unit-documents', { condoId: condo.id }),
            ...months.map(([ref]) => api.get('bills', { condoId: condo.id, reference: ref }))
        ]);
        view.users = users;
        view.documents = documents;
        view.bills = Object.fromEntries(months.map(([ref], index) => [ref, bills[index]]));
    }

    function billFor(label, month) {
        return (view.bills[month] || []).find((bill) => bill.unit === label);
    }

    /** Quantos boletos do mês já foram pagos (pelo retorno do banco ou na mão). */
    function paidCard() {
        const bills = view.bills[view.month] || [];
        const paid = bills.filter((bill) => bill.status === 'PAID');
        const overdue = bills.filter((bill) => bill.status === 'OVERDUE').length;
        return h('div', { class: 'card stat' },
            h('p', { class: 'eyebrow' }, 'Pagos'),
            h('p', { class: 'stat-value' }, String(paid.length), h('small', {}, ` / ${bills.length}`)),
            h('p', { class: 'stat-sub' }, bills.length
                ? [`${formatBrl(paid.reduce((sum, bill) => sum + bill.amount, 0))} recebidos`, overdue ? `${overdue} vencidos` : null].filter(Boolean).join(' · ')
                : 'Nenhum boleto com valor neste mês')
        );
    }

    function occupantsOf(label) {
        return view.users.filter((user) => (user.role === 'RESIDENT' || user.role === 'MANAGER') && (user.units || []).includes(label));
    }

    function documentFor(label, month) {
        return view.documents.find((doc) => doc.unit === label && doc.reference === month);
    }

    function render() {
        if (!alive) return;
        const units = condo.towers.flatMap(towerUnits);
        const occupied = units.filter((unit) => occupantsOf(unit.label).length).length;
        const withPdf = units.filter((unit) => documentFor(unit.label, view.month)).length;
        const percent = units.length ? Math.round((withPdf / units.length) * 100) : 0;
        const tower = condo.towers.find((item) => item.id === view.towerId) || condo.towers[0];

        mount(
            page,
            header(),
            h('section', { class: 'stats' },
                h('div', { class: 'card stat stat-hero' },
                    h('p', { class: 'eyebrow' }, `Boletos de ${monthLabel(view.month).toLowerCase()}`),
                    h('p', { class: 'stat-value' }, String(withPdf), h('small', {}, ` / ${units.length}`)),
                    h('p', { class: 'stat-sub' }, withPdf === units.length && units.length ? 'Todos os apartamentos já têm o PDF do mês.' : `${units.length - withPdf} apartamentos ainda sem PDF.`),
                    h('div', { class: 'progress', role: 'progressbar', 'aria-valuenow': percent, 'aria-valuemin': 0, 'aria-valuemax': 100 },
                        h('span', { style: { width: `${percent}%` } }))
                ),
                paidCard(),
                h('div', { class: 'card stat' },
                    h('p', { class: 'eyebrow' }, 'Apartamentos'),
                    h('p', { class: 'stat-value' }, String(units.length)),
                    h('p', { class: 'stat-sub' }, `${condo.towers.length} ${condo.towers.length === 1 ? 'bloco' : 'blocos'}`)
                ),
                h('div', { class: 'card stat' },
                    h('p', { class: 'eyebrow' }, 'Ocupados'),
                    h('p', { class: 'stat-value' }, String(occupied)),
                    h('p', { class: 'stat-sub' }, `${units.length - occupied} sem morador cadastrado`)
                )
            ),
            h('div', { class: 'toolbar' },
                h('div', { class: 'toolbar-group' },
                    h('span', { class: 'toolbar-label' }, 'Mês'),
                    h('div', { class: 'pills' },
                        months.map(([ref, label]) =>
                            h('button', {
                                type: 'button', class: `pill${ref === view.month ? ' selected' : ''}`, 'aria-pressed': String(ref === view.month),
                                onclick: () => { view.month = ref; render(); }
                            }, label, h('span', { class: 'count' }, shortMonthLabel(ref)))
                        )
                    )
                ),
                condo.towers.length > 1
                    ? h('div', { class: 'toolbar-group' },
                        h('span', { class: 'toolbar-label' }, 'Bloco'),
                        h('div', { class: 'pills' },
                            condo.towers.map((item) =>
                                h('button', {
                                    type: 'button', class: `pill${item.id === tower.id ? ' selected' : ''}`, 'aria-pressed': String(item.id === tower.id),
                                    onclick: () => { view.towerId = item.id; render(); }
                                }, item.name)
                            )
                        )
                    )
                    : null
            ),
            tower ? towerMap(tower) : emptyState('building', 'Sem blocos', 'Edite o condomínio para cadastrar os blocos.')
        );
    }

    function towerMap(tower) {
        const floors = allFloors(tower)
            .filter((floor) => floor !== 0 || unitsOn(tower, 0) > 0)
            .reverse();
        const units = towerUnits(tower);
        return h('section', { class: 'card tower-map' },
            h('div', { class: 'tower-head' },
                h('h2', {}, tower.name),
                h('div', { class: 'legend' },
                    h('span', {}, h('i', { class: 'l-pdf' }), `PDF de ${shortMonthLabel(view.month)} enviado`),
                    h('span', {}, h('i', { class: 'l-nopdf' }), 'Sem PDF'),
                    h('span', {}, h('i', { class: 'l-empty' }), 'Sem morador')
                )
            ),
            floors.map((floor) => {
                const onFloor = units.filter((unit) => unit.floor === floor);
                return h('div', { class: 'floor-row' },
                    h('div', { class: 'floor-label' }, floorLabel(floor)),
                    onFloor.length
                        ? h('div', { class: 'floor-units' }, onFloor.map(unitCell))
                        : h('div', { class: 'floor-none' }, 'Sem apartamentos neste andar')
                );
            })
        );
    }

    function unitCell(unit) {
        const people = occupantsOf(unit.label);
        const pdf = documentFor(unit.label, view.month);
        const bill = billFor(unit.label, view.month);
        return h('button', {
            type: 'button',
            class: `unit-cell${people.length ? '' : ' vacant'}`,
            'aria-label': `${unit.label}. ${people.length ? people.map((p) => p.name).join(', ') : 'Sem morador'}. ${pdf ? 'Boleto enviado' : 'Sem boleto'}${bill ? `, ${BILL_STATUS[bill.status].toLowerCase()}` : ''}.`,
            onclick: () => openUnit(unit)
        },
        h('span', { class: 'unit-top' },
            h('span', { class: 'unit-number' }, unit.number),
            pdf ? h('span', { class: 'unit-pdf ok' }, icon('check', 12), 'PDF') : h('span', { class: 'unit-pdf missing' }, 'sem PDF')
        ),
        h('span', { class: 'unit-people' }, people.length ? people.map((p) => p.name.split(' ')[0]).join(', ') : 'Sem morador'),
        bill ? h('span', { class: `unit-pay ${bill.status.toLowerCase()}` }, BILL_STATUS[bill.status]) : null
        );
    }

    // -------------------------------------------------------------- detalhe do apartamento

    function openUnit(unit) {
        const panel = drawer(`Apto ${unit.number}`, {
            eyebrow: `${unit.tower} · ${floorLabel(unit.floor)} andar`,
            onClose: () => { view.openUnit = null; view.refreshDrawer = null; }
        });
        view.openUnit = unit;
        const errorBox = h('div', {});

        async function afterChange(message) {
            await load();
            render();
            draw();
            refreshSidebarBadge();
            toast(message);
        }

        function draw() {
            const people = occupantsOf(unit.label);
            const current = documentFor(unit.label, view.month);
            const history = view.documents
                .filter((doc) => doc.unit === unit.label)
                .sort((a, b) => b.reference.localeCompare(a.reference));
            const unassigned = view.users.filter((user) => user.role === 'RESIDENT' && !(user.units || []).length);

            mount(
                panel.body,
                errorBox,
                h('section', { class: 'form-stack' },
                    h('div', { class: 'section-title' },
                        h('h3', {}, `Boleto de ${monthLabel(view.month).toLowerCase()}`),
                        current ? h('span', { class: 'chip chip-success' }, 'PDF enviado') : h('span', { class: 'chip chip-neutral' }, 'Sem PDF')
                    ),
                    current
                        ? h('div', { class: 'list' },
                            documentRow(current, true))
                        : null,
                    paymentRow(billFor(unit.label, view.month)),
                    dropzone(current)
                ),
                h('section', { class: 'form-stack' },
                    h('div', { class: 'section-title' }, h('h3', {}, 'Moradores')),
                    h('div', { class: 'list' },
                        people.length
                            ? people.map((person) =>
                                h('div', { class: 'list-row' },
                                    h('span', { class: 'avatar avatar-s' }, person.name.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase()),
                                    h('div', { class: 'grow' },
                                        h('strong', {}, person.name),
                                        h('small', {}, [person.role === 'MANAGER' ? 'Síndico' : 'Morador', person.phone, person.email].filter(Boolean).join(' · '))
                                    ),
                                    button('Desalocar', {
                                        variant: 'ghost', small: true, iconName: 'userMinus',
                                        onclick: async (event) => {
                                            const btn = event.currentTarget;
                                            const ok = await confirmDialog({
                                                title: 'Desalocar morador?',
                                                message: `${person.name} deixa de estar ligado ao ${unit.label}. O cadastro continua existindo.`,
                                                confirmLabel: 'Desalocar', danger: true
                                            });
                                            if (!ok) return;
                                            await run(btn, async () => {
                                                await api.del(`users/${person.id}/units`, { unit: unit.label });
                                                await afterChange(`${person.name} desalocado do ${unit.label}.`);
                                            });
                                        }
                                    })
                                ))
                            : h('p', { class: 'list-empty' }, 'Nenhum morador neste apartamento.')
                    ),
                    allocateForm(unassigned)
                ),
                h('section', { class: 'form-stack' },
                    h('div', { class: 'section-title' }, h('h3', {}, 'Boletos em PDF'), h('span', { class: 'muted small' }, `${history.length}`)),
                    h('div', { class: 'list' },
                        history.length ? history.map((doc) => documentRow(doc, false)) : h('p', { class: 'list-empty' }, 'Nenhum boleto enviado para este apartamento.')
                    )
                )
            );
        }
        view.refreshDrawer = draw;

        async function run(btn, action) {
            mount(errorBox);
            await busy(btn, async () => {
                try {
                    await action();
                } catch (error) {
                    mount(errorBox, formError(error.message));
                }
            });
        }

        /**
         * Situação do boleto do mês: em aberto, "Marcar como pago" (pagou por Pix, em dinheiro);
         * pago, como foi a baixa e "Desfazer baixa" (volta a aparecer em aberto para o morador).
         */
        function paymentRow(bill) {
            if (!bill) return null;
            const paid = bill.status === 'PAID';
            const tone = paid ? 'success' : bill.status === 'OVERDUE' ? 'danger' : 'warning';
            return h('div', { class: 'list' },
                h('div', { class: 'list-row' },
                    h('span', { class: `chip chip-${tone}` }, BILL_STATUS[bill.status]),
                    h('div', { class: 'grow' },
                        h('strong', {}, formatBrl(bill.amount)),
                        h('small', {}, paid
                            ? [`pago em ${formatDay(bill.paidAt)}`, PAID_VIA[bill.paidVia]].filter(Boolean).join(' · ')
                            : `vence em ${formatDay(bill.dueDate)}`)
                    ),
                    paid
                        ? button('Desfazer baixa', {
                            variant: 'ghost', small: true,
                            onclick: async (event) => {
                                const btn = event.currentTarget;
                                const ok = await confirmDialog({
                                    title: 'Desfazer a baixa?',
                                    message: `O boleto de ${monthLabel(bill.reference).toLowerCase()} do ${unit.label} volta a aparecer em aberto para o morador.`,
                                    confirmLabel: 'Desfazer baixa', danger: true
                                });
                                if (!ok) return;
                                await run(btn, async () => {
                                    await api.post(`bills/${bill.id}/unpay`, { condoId: condo.id });
                                    await afterChange('Baixa desfeita.');
                                });
                            }
                        })
                        : button('Marcar como pago', {
                            variant: 'secondary', small: true, iconName: 'check',
                            onclick: async (event) => {
                                const btn = event.currentTarget;
                                const ok = await confirmDialog({
                                    title: 'Marcar como pago?',
                                    message: `O boleto de ${monthLabel(bill.reference).toLowerCase()} (${formatBrl(bill.amount)}) do ${unit.label} fica pago com a data de hoje, e os moradores são avisados. Use quando o pagamento não veio pelo retorno do banco.`,
                                    confirmLabel: 'Marcar como pago'
                                });
                                if (!ok) return;
                                await run(btn, async () => {
                                    await api.post(`bills/${bill.id}/pay`, { condoId: condo.id });
                                    await afterChange(`Boleto do ${unit.label} marcado como pago.`);
                                });
                            }
                        })
                )
            );
        }

        function documentRow(doc, compact) {
            return h('div', { class: 'list-row' },
                icon('file', 20),
                h('div', { class: 'grow' },
                    h('strong', {}, compact ? doc.fileName : `${monthLabel(doc.reference)}`),
                    h('small', {}, [compact ? null : doc.fileName, formatBytes(doc.sizeBytes), `enviado em ${formatDateTime(doc.uploadedAt)}`].filter(Boolean).join(' · '))
                ),
                h('button', {
                    type: 'button', class: 'icon-btn', title: 'Abrir PDF', 'aria-label': `Abrir PDF de ${monthLabel(doc.reference)}`,
                    onclick: () => api.openFile(doc.url, doc.fileName).catch((error) => toast(error.message, 'danger'))
                }, icon('open', 18)),
                h('button', {
                    type: 'button', class: 'icon-btn danger', title: 'Excluir PDF', 'aria-label': `Excluir PDF de ${monthLabel(doc.reference)}`,
                    onclick: async (event) => {
                        const btn = event.currentTarget;
                        const ok = await confirmDialog({
                            title: 'Excluir boleto?',
                            message: `O PDF de ${monthLabel(doc.reference).toLowerCase()} do ${unit.label} some para os moradores.`,
                            confirmLabel: 'Excluir', danger: true
                        });
                        if (!ok) return;
                        await run(btn, async () => {
                            await api.del(`unit-documents/${doc.id}`);
                            await afterChange('Boleto excluído.');
                        });
                    }
                }, icon('trash', 18))
            );
        }

        function dropzone(current) {
            const fileInput = h('input', { type: 'file', accept: 'application/pdf,.pdf', hidden: true });
            const zone = h('div', {
                class: 'dropzone', role: 'button', tabindex: '0',
                'aria-label': current ? 'Substituir o PDF do mês' : 'Enviar o PDF do mês',
                onclick: () => fileInput.click(),
                onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); fileInput.click(); } },
                ondragover: (event) => { event.preventDefault(); zone.classList.add('over'); },
                ondragleave: () => zone.classList.remove('over'),
                ondrop: (event) => {
                    event.preventDefault();
                    zone.classList.remove('over');
                    const file = event.dataTransfer?.files?.[0];
                    if (file) upload(file);
                }
            },
            icon('upload', 26),
            h('strong', {}, current ? 'Substituir o PDF deste mês' : 'Enviar o PDF deste mês'),
            h('small', {}, `Arraste o arquivo aqui ou clique para escolher. Só PDF, até ${maxUploadLabel()}.`),
            fileInput
            );
            fileInput.addEventListener('change', () => fileInput.files[0] && upload(fileInput.files[0]));

            async function upload(file) {
                mount(errorBox);
                const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
                if (!isPdf) return mount(errorBox, formError('Escolha um arquivo PDF.'));
                if (file.size > maxUploadBytes()) return mount(errorBox, formError(`O PDF passa de ${maxUploadLabel()}. Diminua o arquivo e tente de novo.`));
                if (current) {
                    const ok = await confirmDialog({
                        title: 'Substituir boleto?',
                        message: `O PDF atual de ${monthLabel(view.month).toLowerCase()} (${current.fileName}) será trocado por ${file.name}.`,
                        confirmLabel: 'Substituir'
                    });
                    if (!ok) return;
                }
                // O morador só vê o boleto com valor e vencimento: eles saem da linha digitável do PDF.
                let line = null;
                try {
                    const lines = findBoletoLines((await readPdfText(new Uint8Array(await file.arrayBuffer()))).join('\n'));
                    if (lines.length === 1) line = lines[0];
                } catch {
                    // PDF que não dá para ler aqui: pede a linha abaixo.
                }
                if (!line) {
                    const typed = await confirmDialog({
                        title: 'Linha digitável',
                        message: 'Não encontrei a linha digitável neste PDF. Digite os 47 números para o morador ver o valor e o vencimento no app.',
                        confirmLabel: 'Enviar',
                        reason: { label: 'Linha digitável', placeholder: '00000.00000 00000.000000 00000.000000 0 00000000000000', maxLength: 80 }
                    });
                    if (!typed) return;
                    line = parseBoletoLine(typed);
                    if (!line) return mount(errorBox, formError('Linha digitável inválida: confira os 47 números.'));
                }
                const form = new FormData();
                form.append('condoId', condo.id);
                form.append('unit', unit.label);
                form.append('reference', view.month);
                form.append('barcode', line.line);
                form.append('file', file, file.name);
                zone.classList.add('over');
                mount(zone, h('span', { class: 'spinner' }), h('strong', {}, `Enviando ${file.name}...`));
                try {
                    await api.upload('unit-documents', form);
                    await afterChange(`Boleto de ${monthLabel(view.month).toLowerCase()} enviado para o ${unit.label}.`);
                } catch (error) {
                    draw();
                    mount(errorBox, formError(error.message));
                }
            }
            return zone;
        }

        function allocateForm(unassigned) {
            if (!unassigned.length) {
                return h('p', { class: 'muted small' }, 'Para alocar alguém, crie o cadastro em Cadastros. Aqui aparecem os moradores que ainda não têm apartamento.');
            }
            const picker = select([['', 'Escolha um morador sem apartamento'], ...unassigned.map((user) => [user.id, user.name])], '', { 'aria-label': 'Morador sem apartamento' });
            const add = button('Alocar', { variant: 'secondary', iconName: 'userPlus', disabled: true });
            picker.addEventListener('change', () => (add.disabled = !picker.value));
            add.addEventListener('click', () =>
                run(add, async () => {
                    const person = unassigned.find((user) => user.id === picker.value);
                    await api.post(`users/${person.id}/units`, { unit: unit.label });
                    await afterChange(`${person.name} alocado no ${unit.label}.`);
                })
            );
            return h('div', { class: 'toolbar-group' }, h('div', { style: { flex: '1', minWidth: '220px' } }, picker), add);
        }

        draw();
    }

    load()
        .then(render)
        .catch((error) => alive && mount(page, header(), formError(error.message)));

    return () => {
        alive = false;
    };
}
