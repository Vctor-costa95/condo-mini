// Entrada do painel: login, barra lateral, seletor de condomínio e navegação por hash.

import { api, hasToken, saveToken, setUnauthorizedHandler } from './api.js';
import { applyTheme } from './theme.js';
import { brandMark, closeDrawer, h, icon, initials, mount, toast } from './ui.js';
import { currentCondo, loadCondos, selectCondo, setUser, state, subscribe } from './state.js';
import { renderLogin } from './views/login.js';
import { renderUnits } from './views/unidades.js';
import { renderUsers } from './views/cadastros.js';
import { renderAreas } from './views/espacos.js';
import { renderCondoEditor } from './views/condominio.js';
import { renderProfile } from './views/perfil.js';

const root = document.getElementById('app');

const NAV = [
    { route: 'unidades', label: 'Unidades', icon: 'building', render: renderUnits },
    { route: 'cadastros', label: 'Cadastros', icon: 'users', render: renderUsers },
    { route: 'espacos', label: 'Espaços', icon: 'sofa', render: renderAreas }
];

let content = null;
let sidebar = null;
let cleanup = null;
let unsubscribe = null;
let unassignedCount = 0;

applyTheme();
setUnauthorizedHandler(() => showLogin('Sua sessão terminou. Entre de novo.'));
window.addEventListener('hashchange', () => content && route());
boot();

async function boot() {
    if (!hasToken()) return showLogin();
    mount(root, h('div', { class: 'boot' }, brandMark(48)));
    try {
        const me = await api.get('me');
        if (me.role !== 'ADMIN') {
            await logout();
            return showLogin(null, 'Este painel é da administradora. Use o app condo.mini.');
        }
        setUser(me);
        await loadCondos();
        showShell();
    } catch (error) {
        if (error.status !== 401) showLogin(null, error.message);
    }
}

function showLogin(message, error) {
    cleanup?.();
    cleanup = null;
    unsubscribe?.();
    unsubscribe = null;
    content = null;
    sidebar = null;
    renderLogin(root, {
        message,
        error,
        onLoggedIn: async ({ token, user }, remember) => {
            if (user.role !== 'ADMIN') {
                // Entrou com outro perfil: encerra a sessão criada e não guarda nada.
                saveToken(token, false);
                await api.post('auth/logout').catch(() => {});
                saveToken(null);
                throw new Error('Este painel é da administradora. Moradores, síndicos e portaria usam o app condo.mini.');
            }
            saveToken(token, remember);
            setUser(user);
            await loadCondos();
            showShell();
        }
    });
}

export async function logout() {
    await api.post('auth/logout').catch(() => {});
    saveToken(null);
}

function showShell() {
    sidebar = h('nav', { class: 'sidebar', 'aria-label': 'Menu' });
    content = h('main', { class: 'content', id: 'conteudo', tabindex: '-1' });
    mount(root, h('div', { class: 'shell' }, sidebar, content));
    unsubscribe?.();
    unsubscribe = subscribe(() => {
        renderSidebar();
        route();
        refreshBadge();
    });
    renderSidebar();
    route();
    refreshBadge();
}

/** Moradores sem apartamento: o mesmo aviso que o app mostra na aba Cadastros. */
async function refreshBadge() {
    if (!state.condoId) return;
    try {
        const users = await api.get('users', { condoId: state.condoId });
        unassignedCount = users.filter((user) => user.role === 'RESIDENT' && !(user.units || []).length).length;
        renderSidebar();
    } catch {
        // O número é só um lembrete; sem ele o painel continua funcionando.
    }
}

export function refreshSidebarBadge() {
    refreshBadge();
}

function currentRoute() {
    const hash = window.location.hash.replace(/^#\/?/, '');
    const [name, ...rest] = hash.split('/');
    return { name: name || 'unidades', param: rest.join('/') };
}

function route() {
    if (!content) return;
    const { name, param } = currentRoute();
    cleanup?.();
    cleanup = null;
    closeDrawer();
    renderSidebar();
    const ctx = { condo: currentCondo(), condoId: state.condoId, user: state.user, navigate };
    if (name === 'condominio') {
        cleanup = renderCondoEditor(content, { ...ctx, editId: param === 'novo' ? '' : param });
    } else if (name === 'perfil') {
        cleanup = renderProfile(content, { ...ctx, onLogout: async () => { await logout(); showLogin('Você saiu do painel.'); } });
    } else if (!state.condos.length) {
        mount(
            content,
            h(
                'div',
                { class: 'page' },
                h('div', { class: 'empty' },
                    h('div', { class: 'empty-icon' }, icon('building', 28)),
                    h('h3', {}, 'Nenhum condomínio ainda'),
                    h('p', {}, 'Cadastre o primeiro condomínio para começar.'),
                    h('a', { class: 'btn btn-primary', href: '#/condominio/novo' }, icon('plus', 18), h('span', {}, 'Novo condomínio'))
                )
            )
        );
    } else {
        const item = NAV.find((entry) => entry.route === name) || NAV[0];
        cleanup = item.render(content, ctx);
    }
    content.focus({ preventScroll: true });
    window.scrollTo(0, 0);
}

export function navigate(path) {
    window.location.hash = `#/${path}`;
}

function renderSidebar() {
    if (!sidebar) return;
    const { name } = currentRoute();
    const condo = currentCondo();
    mount(
        sidebar,
        h('div', { class: 'sidebar-brand' },
            h('span', { class: 'brand-tile' }, brandMark(26)),
            h('span', { class: 'brand-name' }, 'condo', h('span', { class: 'brand-dot' }, '.'), 'mini'),
            h('span', { class: 'brand-tag' }, 'Administradora')
        ),
        condoPicker(condo),
        h('ul', { class: 'nav' },
            NAV.map((item) =>
                h('li', {},
                    h('a', { href: `#/${item.route}`, class: `nav-link${name === item.route ? ' active' : ''}`, 'aria-current': name === item.route ? 'page' : null },
                        icon(item.icon),
                        h('span', {}, item.label),
                        item.route === 'cadastros' && unassignedCount > 0
                            ? h('span', { class: 'nav-badge', title: 'Moradores sem apartamento' }, String(unassignedCount))
                            : null
                    )
                )
            )
        ),
        h('div', { class: 'sidebar-footer' },
            h('a', { href: '#/perfil', class: `profile-link${name === 'perfil' ? ' active' : ''}` },
                h('span', { class: 'avatar' }, initials(state.user?.name)),
                h('span', { class: 'profile-text' },
                    h('strong', {}, state.user?.name || ''),
                    h('small', {}, 'Meu perfil')
                )
            )
        )
    );
}

/** Condomínio atual no topo da barra; abre a lista para trocar, editar ou criar outro. */
function condoPicker(condo) {
    const list = h('div', { class: 'picker-menu', role: 'menu', hidden: true });
    const toggle = h(
        'button',
        { type: 'button', class: 'picker', 'aria-haspopup': 'menu', 'aria-expanded': 'false', onclick: () => setOpen(list.hidden) },
        h('span', { class: 'picker-text' },
            h('small', {}, 'Condomínio'),
            h('strong', {}, condo?.name || 'Nenhum condomínio'),
            condo?.address ? h('span', { class: 'picker-address' }, condo.address) : null
        ),
        icon('chevronDown', 18)
    );
    function setOpen(open) {
        list.hidden = !open;
        toggle.setAttribute('aria-expanded', String(open));
        if (open) {
            setTimeout(() => document.addEventListener('click', outside), 0);
        } else {
            document.removeEventListener('click', outside);
        }
    }
    function outside(event) {
        if (!list.contains(event.target) && !toggle.contains(event.target)) setOpen(false);
    }
    mount(
        list,
        state.condos.map((item) =>
            h('div', { class: `picker-item${item.id === state.condoId ? ' selected' : ''}`, role: 'none' },
                h('button', {
                    type: 'button', role: 'menuitemradio', 'aria-checked': String(item.id === state.condoId), class: 'picker-choose',
                    onclick: () => {
                        setOpen(false);
                        selectCondo(item.id);
                        toast(`Abrindo ${item.name}.`);
                    }
                },
                item.id === state.condoId ? icon('check', 16) : h('span', { class: 'icon-spacer' }),
                h('span', {}, item.name)
                ),
                h('a', { href: `#/condominio/${item.id}`, class: 'icon-btn', title: `Editar ${item.name}`, 'aria-label': `Editar ${item.name}`, onclick: () => setOpen(false) }, icon('edit', 16))
            )
        ),
        h('a', { href: '#/condominio/novo', class: 'picker-new', onclick: () => setOpen(false) }, icon('plus', 16), h('span', {}, 'Novo condomínio'))
    );
    return h('div', { class: 'picker-wrap' }, toggle, list);
}
