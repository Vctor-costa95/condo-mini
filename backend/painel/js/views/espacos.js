// Espaços comuns: lista com reservas futuras, interditar/liberar e o formulário do espaço.

import { api } from '../api.js';
import {
    busy, button, confirmDialog, drawer, emptyState, field, formatBrl, formError, h, icon, input, loading,
    mount, textarea, toast
} from '../ui.js';

// Mesmos ícones e tons da capa do app (ui/components/AreaIcons.kt).
const AREA_ICONS = [
    ['party', 'Festas', '#3B3226', '#1C1813'],
    ['grill', 'Churrasco', '#4A2F24', '#1F1511'],
    ['gourmet', 'Gourmet', '#2C3A33', '#131A16'],
    ['court', 'Quadra', '#243247', '#111824'],
    ['meeting', 'Reuniões', '#34303F', '#16141C'],
    ['pool', 'Piscina', '#1F3A44', '#0F1B20'],
    ['gym', 'Academia', '#33363B', '#15171A'],
    ['spa', 'Spa e sauna', '#3A2E3A', '#1A141A'],
    ['tennis', 'Tênis', '#2F3D2A', '#151C12'],
    ['kids', 'Infantil', '#44352A', '#1E1712'],
    ['games', 'Jogos', '#2A2F45', '#12141F'],
    ['cinema', 'Cinema', '#3D2328', '#1B1012'],
    ['cowork', 'Coworking', '#263038', '#11161A'],
    ['wellness', 'Bem-estar', '#37322A', '#191612'],
    ['garden', 'Jardim', '#293A2C', '#121A13'],
    ['pets', 'Pet place', '#3F3527', '#1C1711'],
    ['laundry', 'Lavanderia', '#2B3440', '#131820'],
    ['bikes', 'Bicicletário', '#33302B', '#171512']
];

const MODES = [
    ['PERIOD', 'Por turno', 'Manhã, tarde ou noite'],
    ['HOURLY', 'Por hora', 'Início e duração, no horário de funcionamento'],
    ['DAILY', 'Dia inteiro', 'Uma reserva por dia']
];

function iconFor(key) {
    return AREA_ICONS.find(([k]) => k === key) || AREA_ICONS[4];
}

/** "120,50" ou "120.50" → 120.5; vazio → 0; texto inválido → null. */
function parseFee(text) {
    const clean = String(text).trim().replace(/\s|R\$/g, '');
    if (!clean) return 0;
    const normalized = clean.includes(',') ? clean.replace(/\./g, '').replace(',', '.') : clean;
    const value = Number(normalized);
    return Number.isFinite(value) && value >= 0 ? Math.round(value * 100) / 100 : null;
}

function feeText(value) {
    return value ? String(value.toFixed(2)).replace('.', ',') : '';
}

function scheduleText(area) {
    if (area.bookingMode === 'HOURLY') return `${area.opensAt}h às ${area.closesAt}h · até ${area.maxHours}h por reserva`;
    if (area.bookingMode === 'DAILY') return 'Dia inteiro';
    return 'Por turno';
}

export function renderAreas(container, { condo }) {
    let alive = true;
    const view = { areas: [], upcoming: new Map() };
    const page = h('div', { class: 'page' });
    mount(container, page);
    mount(page, header(), loading());

    function header() {
        return h('header', { class: 'page-header' },
            h('div', {},
                h('p', { class: 'eyebrow' }, condo.name),
                h('h1', {}, 'Espaços'),
                h('p', {}, 'Salão, churrasqueira, quadra... Defina regras, taxa e como cada espaço é reservado. Interdite quando precisar de manutenção.')
            ),
            h('div', { class: 'page-actions' }, button('Novo espaço', { iconName: 'plus', onclick: () => openForm(null) }))
        );
    }

    async function load() {
        const [areas, reservations] = await Promise.all([
            api.get('areas', { condoId: condo.id }),
            api.get('reservations', { condoId: condo.id })
        ]);
        const today = new Date();
        const todayText = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
        view.areas = areas;
        view.upcoming = new Map();
        reservations
            .filter((r) => (r.status === 'CONFIRMED' || r.status === 'PENDING') && r.date >= todayText)
            .forEach((r) => view.upcoming.set(r.area.id, (view.upcoming.get(r.area.id) || 0) + 1));
    }

    async function reload(message) {
        await load();
        render();
        if (message) toast(message);
    }

    function render() {
        if (!alive) return;
        mount(page, header(),
            view.areas.length
                ? h('div', { class: 'area-grid' }, view.areas.map(card))
                : h('div', { class: 'card' }, emptyState('sofa', 'Nenhum espaço ainda', 'Cadastre o salão de festas, a churrasqueira e os outros espaços que os moradores podem reservar.'))
        );
    }

    function card(area) {
        const [, label, start, end] = iconFor(area.iconKey);
        const upcoming = view.upcoming.get(area.id) || 0;
        const closed = Boolean(area.closedReason);
        return h('article', { class: 'card area-card' },
            h('div', { class: 'area-cover', style: { background: `linear-gradient(135deg, ${start}, ${end})` } },
                closed ? h('span', { class: 'chip chip-danger area-closed' }, 'Interditado') : null,
                h('span', { class: 'area-glyph', 'aria-hidden': 'true' }, label.charAt(0)),
                h('p', { class: 'eyebrow' }, `Até ${area.capacity} pessoas · ${area.fee > 0 ? formatBrl(area.fee) : 'sem taxa'}`),
                h('h3', {}, area.name)
            ),
            h('div', { class: 'area-body' },
                h('div', { class: 'area-meta' },
                    h('span', {}, scheduleText(area)),
                    h('span', {}, upcoming ? `${upcoming} ${upcoming === 1 ? 'reserva futura' : 'reservas futuras'}` : 'Sem reservas futuras')
                ),
                area.description ? h('p', { class: 'area-desc' }, area.description) : null,
                closed ? h('p', { class: 'area-reason' }, `Motivo: ${area.closedReason}`) : null
            ),
            h('div', { class: 'area-actions' },
                button(closed ? 'Liberar' : 'Interditar', {
                    variant: closed ? 'secondary' : 'danger-outline', small: true, iconName: closed ? 'unlock' : 'lock',
                    onclick: (event) => toggleClosed(area, event.currentTarget)
                }),
                h('span', { class: 'grow' }),
                h('button', { type: 'button', class: 'icon-btn', title: 'Editar', 'aria-label': `Editar ${area.name}`, onclick: () => openForm(area) }, icon('edit', 18)),
                h('button', { type: 'button', class: 'icon-btn danger', title: 'Excluir', 'aria-label': `Excluir ${area.name}`, onclick: () => remove(area) }, icon('trash', 18))
            )
        );
    }

    async function toggleClosed(area, btn) {
        if (area.closedReason) {
            const ok = await confirmDialog({ title: `Liberar ${area.name}?`, message: 'Os moradores voltam a poder reservar o espaço.', confirmLabel: 'Liberar' });
            if (!ok) return;
            await busy(btn, () => api.put(`areas/${area.id}/closed`, { reason: null }).then(() => reload(`${area.name} liberado.`)).catch((e) => toast(e.message, 'danger')));
            return;
        }
        const reason = await confirmDialog({
            title: `Interditar ${area.name}?`,
            message: 'Ninguém consegue reservar enquanto estiver interditado. O motivo aparece para os moradores.',
            confirmLabel: 'Interditar', danger: true,
            reason: { label: 'Motivo', placeholder: 'Ex.: pintura até sexta-feira', maxLength: 255 }
        });
        if (!reason) return;
        await busy(btn, () => api.put(`areas/${area.id}/closed`, { reason }).then(() => reload(`${area.name} interditado.`)).catch((e) => toast(e.message, 'danger')));
    }

    async function remove(area) {
        const upcoming = view.upcoming.get(area.id) || 0;
        const ok = await confirmDialog({
            title: `Excluir ${area.name}?`,
            message: upcoming
                ? `O espaço some do app e as ${upcoming} reservas futuras dele são canceladas. Não dá para desfazer.`
                : 'O espaço some do app. Não dá para desfazer.',
            confirmLabel: 'Excluir', danger: true
        });
        if (!ok) return;
        try {
            await api.del(`areas/${area.id}`);
            await reload(`${area.name} excluído.`);
        } catch (error) {
            toast(error.message, 'danger');
        }
    }

    // -------------------------------------------------------------- formulário

    function openForm(area) {
        const isNew = !area;
        const panel = drawer(isNew ? 'Novo espaço' : area.name, { eyebrow: isNew ? condo.name : 'Editar espaço', wide: true });
        const state = {
            iconKey: area?.iconKey || 'party',
            bookingMode: area?.bookingMode || 'PERIOD',
            opensAt: area?.opensAt ?? 8,
            closesAt: area?.closesAt ?? 22,
            maxHours: area?.maxHours ?? 2
        };
        const name = input({ value: area?.name || '', maxlength: 120 });
        const description = input({ value: area?.description || '', maxlength: 200, placeholder: 'Ex.: Salão com cozinha de apoio e banheiros' });
        const capacity = input({ type: 'number', min: 1, max: 5000, value: area?.capacity || '', inputmode: 'numeric' });
        const fee = input({ value: feeText(area?.fee || 0), inputmode: 'decimal', placeholder: '0,00' });
        const rules = textarea({ maxlength: 2000, rows: 4, placeholder: 'Ex.: Som até 22h. Limpeza por conta de quem reservou.' });
        rules.value = area?.rules || '';
        const errorBox = h('div', {});
        const iconsBox = h('div', { class: 'icon-choices', role: 'radiogroup', 'aria-label': 'Ícone' });
        const modeBox = h('div', {});

        function drawIcons() {
            mount(iconsBox, AREA_ICONS.map(([key, label, start, end]) =>
                h('button', {
                    type: 'button', role: 'radio', 'aria-checked': String(key === state.iconKey),
                    class: `icon-choice${key === state.iconKey ? ' selected' : ''}`,
                    onclick: () => { state.iconKey = key; drawIcons(); }
                }, h('i', { style: { background: `linear-gradient(135deg, ${start}, ${end})` } }), label)
            ));
        }

        function hourSelect(value, from, to, onchange, label) {
            const options = [];
            for (let hour = from; hour <= to; hour += 1) options.push(h('option', { value: hour, selected: hour === value }, `${hour}h`));
            return h('select', { class: 'input select', 'aria-label': label, onchange: (event) => onchange(Number(event.target.value)) }, options);
        }

        function drawMode() {
            // Mesmo ajuste do app: abre antes de fechar e o máximo cabe no horário.
            state.opensAt = Math.min(Math.max(state.opensAt, 0), 23);
            state.closesAt = Math.min(Math.max(state.closesAt, state.opensAt + 1), 24);
            state.maxHours = Math.min(Math.max(state.maxHours, 1), state.closesAt - state.opensAt);
            mount(modeBox,
                h('div', { class: 'mode-cards', role: 'radiogroup', 'aria-label': 'Como reservar' }, MODES.map(([key, label, hint]) =>
                    h('button', {
                        type: 'button', role: 'radio', 'aria-checked': String(key === state.bookingMode),
                        class: `mode-card${key === state.bookingMode ? ' selected' : ''}`,
                        onclick: () => { state.bookingMode = key; drawMode(); }
                    }, h('strong', {}, label), h('small', {}, hint))
                )),
                state.bookingMode === 'HOURLY'
                    ? h('div', { class: 'form-grid', style: { gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', marginTop: '14px' } },
                        field('Abre às', hourSelect(state.opensAt, 0, 23, (v) => { state.opensAt = v; drawMode(); }, 'Abre às')),
                        field('Fecha às', hourSelect(state.closesAt, state.opensAt + 1, 24, (v) => { state.closesAt = v; drawMode(); }, 'Fecha às')),
                        field('Máximo por reserva', hourSelect(state.maxHours, 1, state.closesAt - state.opensAt, (v) => { state.maxHours = v; drawMode(); }, 'Máximo de horas'))
                    )
                    : null
            );
        }
        drawIcons();
        drawMode();

        const save = button(isNew ? 'Criar espaço' : 'Salvar', { type: 'submit' });
        const formEl = h('form', {
            class: 'form-stack', id: 'area-form', novalidate: true,
            onsubmit: async (event) => {
                event.preventDefault();
                mount(errorBox);
                const feeValue = parseFee(fee.value);
                const capacityValue = parseInt(capacity.value, 10) || 0;
                const error = !name.value.trim() ? 'Dê um nome ao espaço.'
                    : capacityValue <= 0 ? 'Informe quantas pessoas cabem no espaço.'
                        : feeValue === null ? 'Taxa inválida. Use só números, como 120,50.'
                            : null;
                if (error) return mount(errorBox, formError(error));
                await busy(save, async () => {
                    try {
                        await api.post('areas', {
                            id: area?.id || '',
                            condoId: condo.id,
                            name: name.value.trim(),
                            description: description.value.trim(),
                            capacity: capacityValue,
                            fee: feeValue,
                            rules: rules.value.trim(),
                            iconKey: state.iconKey,
                            closedReason: area?.closedReason || null,
                            bookingMode: state.bookingMode,
                            opensAt: state.opensAt,
                            closesAt: state.closesAt,
                            maxHours: state.maxHours
                        });
                        panel.close();
                        await reload(isNew ? `${name.value.trim()} criado.` : `${name.value.trim()} atualizado.`);
                    } catch (err) {
                        mount(errorBox, formError(err.message));
                    }
                });
            }
        },
        errorBox,
        h('div', { class: 'form-grid' },
            h('div', { class: 'span-2' }, field('Nome', name)),
            h('div', { class: 'span-2' }, field('Descrição curta', description)),
            field('Capacidade (pessoas)', capacity),
            field('Taxa (R$)', fee, { hint: 'Deixe em branco se for gratuito. Com taxa, a reserva espera a aprovação do síndico.' })
        ),
        h('section', { class: 'form-section' }, h('h3', {}, 'Como reservar'), modeBox),
        h('section', { class: 'form-section' }, h('h3', {}, 'Regras'), rules),
        h('section', { class: 'form-section' }, h('h3', {}, 'Ícone da capa'), iconsBox)
        );

        mount(panel.body, formEl);
        mount(panel.footer, button('Cancelar', { variant: 'ghost', onclick: () => panel.close() }), save);
        save.setAttribute('form', 'area-form');
    }

    load()
        .then(render)
        .catch((error) => alive && mount(page, header(), formError(error.message)));

    return () => {
        alive = false;
    };
}
