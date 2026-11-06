// Peças de interface do painel. Todo texto entra por textContent: nada vindo da API vira HTML.

const SVG_NS = 'http://www.w3.org/2000/svg';

/**
 * h('button', { class: 'btn', onclick: fn }, 'Salvar')
 * Atributos começando com "on" viram eventos; value/checked/disabled/selected viram propriedades.
 */
export function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
        if (value === undefined || value === null || value === false) continue;
        if (key.startsWith('on') && typeof value === 'function') {
            el.addEventListener(key.slice(2), value);
        } else if (key === 'class') {
            el.className = value;
        } else if (key === 'style' && typeof value === 'object') {
            // Pelo CSSOM: a política de segurança do painel bloqueia o atributo style="..." em texto.
            Object.assign(el.style, value);
        } else if (key === 'dataset') {
            Object.assign(el.dataset, value);
        } else if (['value', 'checked', 'disabled', 'selected', 'multiple'].includes(key)) {
            el[key] = value;
        } else {
            el.setAttribute(key, value === true ? '' : String(value));
        }
    }
    append(el, children);
    return el;
}

function append(parent, children) {
    for (const child of children.flat(Infinity)) {
        if (child === null || child === undefined || child === false) continue;
        parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
}

/** Troca todo o conteúdo de um elemento. */
export function mount(parent, ...children) {
    parent.replaceChildren();
    append(parent, children);
    return parent;
}

// Ícones de linha (24×24), no estilo dos ícones "outlined" do app.
const ICONS = {
    building: 'M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16 M15 9h4a1 1 0 0 1 1 1v11 M3 21h18 M8 8h3 M8 12h3 M8 16h3',
    users: 'M16 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 17.5V19 M10 10.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M20 19v-1.5a3.5 3.5 0 0 0-2.5-3.35 M15.5 4.6a3 3 0 0 1 0 5.8',
    sofa: 'M5 11V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v3 M3 13a2 2 0 0 1 4 0v2h10v-2a2 2 0 0 1 4 0v5H3z M6 18v2 M18 18v2',
    user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M5 20a7 7 0 0 1 14 0',
    logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3 M10 17l5-5-5-5 M15 12H4',
    plus: 'M12 5v14 M5 12h14',
    edit: 'M4 20h4L19 9l-4-4L4 16z M14 6l4 4',
    trash: 'M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3',
    close: 'M6 6l12 12 M18 6L6 18',
    search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14 M20 20l-4-4',
    file: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z M14 3v5h5 M9 13h6 M9 17h4',
    upload: 'M12 16V4 M7 9l5-5 5 5 M5 20h14',
    open: 'M14 4h6v6 M20 4l-9 9 M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
    lock: 'M7 11V8a5 5 0 0 1 10 0v3 M5 11h14v10H5z',
    unlock: 'M7 11V8a5 5 0 0 1 9.6-2 M5 11h14v10H5z',
    check: 'M5 12l5 5L20 7',
    chevronDown: 'M6 9l6 6 6-6',
    alert: 'M12 9v4 M12 17h.01 M10.3 4.2L2.6 18a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z',
    userMinus: 'M15 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 3 17.5V19 M9 10.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M16 11h6',
    userPlus: 'M15 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 3 17.5V19 M9 10.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M19 8v6 M16 11h6',
    contrast: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 3v18'
};

export function icon(name, size = 20) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', size);
    svg.setAttribute('height', size);
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.6');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');
    svg.classList.add('icon');
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', ICONS[name] || ICONS.file);
    svg.append(path);
    return svg;
}

/** A marca do condo.mini ("C" de andares), a mesma do ícone do app. */
export function brandMark(size = 40) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 52 52');
    svg.setAttribute('width', size);
    svg.setAttribute('height', size);
    svg.setAttribute('aria-hidden', 'true');
    const group = document.createElementNS(SVG_NS, 'g');
    group.setAttribute('transform', 'translate(-25.75 -28)');
    const bars = document.createElementNS(SVG_NS, 'path');
    bars.setAttribute('fill', 'currentColor');
    bars.setAttribute('d', 'M44.65,30.75h18.7a2.25,2.25 0 0,1 0,4.5h-18.7a2.25,2.25 0 0,1 0,-4.5z M36.75,37.75h34.5a2.25,2.25 0 0,1 0,4.5h-34.5a2.25,2.25 0 0,1 0,-4.5z M33.25,44.75h8.7a2.25,2.25 0 0,1 0,4.5h-8.7a2.25,2.25 0 0,1 0,-4.5z M33.25,58.75h8.7a2.25,2.25 0 0,1 0,4.5h-8.7a2.25,2.25 0 0,1 0,-4.5z M36.75,65.75h34.5a2.25,2.25 0 0,1 0,4.5h-34.5a2.25,2.25 0 0,1 0,-4.5z M44.65,72.75h18.7a2.25,2.25 0 0,1 0,4.5h-18.7a2.25,2.25 0 0,1 0,-4.5z');
    const accent = document.createElementNS(SVG_NS, 'path');
    accent.setAttribute('class', 'brand-accent');
    accent.setAttribute('d', 'M32.25,51.75h7.5a2.25,2.25 0 0,1 0,4.5h-7.5a2.25,2.25 0 0,1 0,-4.5z');
    group.append(bars, accent);
    svg.append(group);
    return svg;
}

export function button(label, { variant = 'primary', iconName, onclick, type = 'button', disabled, title, small } = {}) {
    return h(
        'button',
        { type, class: `btn btn-${variant}${small ? ' btn-small' : ''}`, onclick, disabled, title, 'aria-label': title },
        iconName ? icon(iconName, small ? 16 : 18) : null,
        label ? h('span', {}, label) : null
    );
}

/** Mostra "Salvando..." no botão enquanto a ação roda; devolve o resultado da ação. */
export async function busy(btn, action) {
    if (!btn) return action();
    const previous = btn.disabled;
    btn.disabled = true;
    btn.classList.add('is-busy');
    try {
        return await action();
    } finally {
        btn.disabled = previous;
        btn.classList.remove('is-busy');
    }
}

export function field(label, control, { hint, error } = {}) {
    const id = control.id || `f-${Math.random().toString(36).slice(2, 9)}`;
    control.id = id;
    return h(
        'div',
        { class: 'field' },
        h('label', { for: id }, label),
        control,
        hint ? h('p', { class: 'field-hint' }, hint) : null,
        error ? h('p', { class: 'field-error' }, error) : null
    );
}

export function input(attrs = {}) {
    return h('input', { class: 'input', type: 'text', autocomplete: 'off', ...attrs });
}

export function textarea(attrs = {}) {
    return h('textarea', { class: 'input textarea', rows: 3, ...attrs });
}

export function select(options, value, attrs = {}) {
    return h(
        'select',
        { class: 'input select', ...attrs },
        options.map(([optionValue, label]) => h('option', { value: optionValue, selected: optionValue === value }, label))
    );
}

export function statusChip(text, tone = 'neutral') {
    return h('span', { class: `chip chip-${tone}` }, text);
}

export function emptyState(iconName, title, message) {
    return h(
        'div',
        { class: 'empty' },
        h('div', { class: 'empty-icon' }, icon(iconName, 28)),
        h('h3', {}, title),
        h('p', {}, message)
    );
}

export function loading() {
    return h('div', { class: 'loading', role: 'status' }, h('span', { class: 'spinner' }), h('span', {}, 'Carregando...'));
}

export function formError(message) {
    return h('p', { class: 'form-error', role: 'alert' }, h('span', {}, icon('alert', 16)), message);
}

// ------------------------------------------------------------------ avisos

export function toast(message, tone = 'neutral') {
    let host = document.getElementById('toasts');
    if (!host) {
        host = h('div', { id: 'toasts', class: 'toasts', 'aria-live': 'polite' });
        document.body.append(host);
    }
    const item = h('div', { class: `toast toast-${tone}` }, message);
    host.append(item);
    setTimeout(() => item.classList.add('leaving'), 4200);
    setTimeout(() => item.remove(), 4600);
}

// ------------------------------------------------------------------ painel lateral e diálogos

let openDrawer = null;

/**
 * Painel que desliza da direita, para detalhes e formulários.
 * Devolve { body, footer, close, setTitle }.
 */
export function drawer(title, { eyebrow, wide, onClose } = {}) {
    closeDrawer();
    const titleEl = h('h2', { class: 'drawer-title' }, title);
    const body = h('div', { class: 'drawer-body' });
    const footer = h('div', { class: 'drawer-footer' });
    const panel = h(
        'aside',
        { class: `drawer${wide ? ' drawer-wide' : ''}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
        h(
            'header',
            { class: 'drawer-header' },
            h('div', {}, eyebrow ? h('p', { class: 'eyebrow' }, eyebrow) : null, titleEl),
            h('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Fechar', onclick: () => close() }, icon('close'))
        ),
        body,
        footer
    );
    const scrim = h('div', { class: 'scrim', onclick: () => close() });
    const onKey = (event) => {
        if (event.key === 'Escape' && !document.querySelector('dialog[open]')) close();
    };
    function close() {
        document.removeEventListener('keydown', onKey);
        panel.classList.add('leaving');
        scrim.classList.add('leaving');
        setTimeout(() => {
            panel.remove();
            scrim.remove();
        }, 180);
        if (openDrawer === api) openDrawer = null;
        onClose?.();
    }
    document.addEventListener('keydown', onKey);
    document.body.append(scrim, panel);
    requestAnimationFrame(() => panel.querySelector('input, select, textarea, button:not(.icon-btn)')?.focus());
    const api = { body, footer, close, setTitle: (text) => (titleEl.textContent = text) };
    openDrawer = api;
    return api;
}

export function closeDrawer() {
    openDrawer?.close();
    openDrawer = null;
}

/**
 * Pergunta antes de algo que não dá para desfazer. Com `reason`, pede um texto obrigatório.
 * Resolve com true (ou o texto) ao confirmar, e null ao desistir.
 */
export function confirmDialog({ title, message, confirmLabel = 'Confirmar', danger = false, reason }) {
    return new Promise((resolve) => {
        const reasonInput = reason ? textarea({ maxlength: reason.maxLength || 255, placeholder: reason.placeholder || '' }) : null;
        const confirmBtn = button(confirmLabel, { variant: danger ? 'danger' : 'primary', type: 'submit', disabled: Boolean(reason) });
        reasonInput?.addEventListener('input', () => (confirmBtn.disabled = !reasonInput.value.trim()));
        const dialog = h(
            'dialog',
            { class: 'dialog' },
            h(
                'form',
                { method: 'dialog' },
                h('h2', {}, title),
                h('p', { class: 'dialog-message' }, message),
                reasonInput ? field(reason.label, reasonInput) : null,
                h(
                    'div',
                    { class: 'dialog-actions' },
                    button('Voltar', { variant: 'ghost', onclick: () => dialog.close('cancel') }),
                    confirmBtn
                )
            )
        );
        dialog.addEventListener('close', () => {
            const confirmed = dialog.returnValue !== 'cancel' && dialog.returnValue !== '';
            dialog.remove();
            if (!confirmed) return resolve(null);
            resolve(reasonInput ? reasonInput.value.trim() : true);
        });
        confirmBtn.addEventListener('click', (event) => {
            event.preventDefault();
            dialog.close('ok');
        });
        document.body.append(dialog);
        dialog.showModal();
        (reasonInput || confirmBtn).focus();
    });
}

// ------------------------------------------------------------------ formatação

/** Busca sem acento e sem maiúsculas: "joao" acha "João". */
export function normalize(text) {
    return String(text ?? '')
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
        .toLowerCase()
        .trim();
}

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
export const formatBrl = (value) => brl.format(Number(value) || 0);

const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' });
/** "2026-11" → "Novembro de 2026" */
export function monthLabel(reference) {
    const [year, month] = reference.split('-').map(Number);
    const text = monthFormat.format(new Date(year, month - 1, 1));
    return text.charAt(0).toUpperCase() + text.slice(1);
}

const shortMonth = new Intl.DateTimeFormat('pt-BR', { month: 'short' });
/** "2026-11" → "nov" */
export function shortMonthLabel(reference) {
    const [year, month] = reference.split('-').map(Number);
    return shortMonth.format(new Date(year, month - 1, 1)).replace('.', '');
}

const dateTime = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
export function formatDateTime(iso) {
    if (!iso) return '';
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : dateTime.format(date);
}

export function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

/** "YYYY-MM" do mês atual somado a `offset` meses. */
export function monthRef(offset = 0) {
    const now = new Date();
    const date = new Date(now.getFullYear(), now.getMonth() + offset, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function initials(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '?';
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}
