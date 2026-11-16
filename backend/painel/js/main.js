// Entrada do painel: login, barra lateral, seletor de condomínio e navegação por hash.
// Entram a administradora (ADMIN), que vê só os condomínios dela com a marca dela, e
// a plataforma (PLATFORM, a equipe condo.mini), que cadastra administradoras e aloca condomínios.

import { api, hasToken, saveToken, setUnauthorizedHandler } from './api.js';
import { applyTheme } from './theme.js';
import { brandMark, brandTile, closeDrawer, h, icon, initials, mount, toast } from './ui.js';
import { currentCondo, isPlatform, loadCondos, selectCondo, setUser, state, subscribe } from './state.js';
import { renderLogin } from './views/login.js';
import { renderUnits } from './views/unidades.js';
import { renderUsers } from './views/cadastros.js';
import { renderAreas } from './views/espacos.js';
import { renderCondoEditor } from './views/condominio.js';
import { renderProfile } from './views/perfil.js';
import { renderCompanies } from './views/administradoras.js';
import { renderCondoList } from './views/condominios.js';

const root = document.getElementById('app');
const PANEL_ROLES = ['ADMIN', 'PLATFORM'];
const BRAND_KEY = 'condomini.painel.brand';

/** Telas do condomínio aberto: as mesmas para a administradora e para a plataforma (suporte). */
const CONDO_NAV = [
    { route: 'unidades', label: 'Unidades', icon: 'building', render: renderUnits },
    { route: 'cadastros', label: 'Cadastros', icon: 'users', render: renderUsers },
    { route: 'espacos', label: 'Espaços', icon: 'sofa', render: renderAreas }
];

const PLATFORM_NAV = [
    { route: 'administradoras', label: 'Administradoras', icon: 'briefcase', render: renderCompanies },
    { route: 'condominios', label: 'Condomínios', icon: 'building', render: renderCondoList }
];

let content = null;
let sidebar = null;
let cleanup = null;
let unsubscribe = null;
let unassignedCount = 0;
let pendingRequests = 0;

applyTheme();
setUnauthorizedHandler(() => showLogin('Sua sessão terminou. Entre de novo.'));
window.addEventListener('hashchange', () => content && route());
boot();

/** Última marca vista neste navegador: a tela de carregamento já abre com a logo certa. */
function savedBrand() {
    try {
        return JSON.parse(window.localStorage.getItem(BRAND_KEY) || 'null');
    } catch {
        return null;
    }
}

function rememberBrand(brand) {
    try {
        if (brand) window.localStorage.setItem(BRAND_KEY, JSON.stringify(brand));
        else window.localStorage.removeItem(BRAND_KEY);
    } catch {
        // Sem armazenamento: a tela de carregamento mostra a marca do condo.mini.
    }
}

async function boot() {
    if (!hasToken()) return showLogin();
    const cached = savedBrand();
    mount(root, h('div', { class: 'boot' }, cached?.companyId ? brandTile(cached, 64) : brandMark(48)));
    try {
        const me = await api.get('me');
        if (!PANEL_ROLES.includes(me.role)) {
            await logout();
            return showLogin(null, 'Este painel é da administradora. Use o app condo.mini.');
        }
        await enter(me);
    } catch (error) {
        if (error.status !== 401) showLogin(null, error.message);
    }
}

async function enter(user) {
    setUser(user);
    rememberBrand(user.brand?.companyId ? user.brand : null);
    await loadCondos();
    showShell();
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
            if (!PANEL_ROLES.includes(user.role)) {
                // Entrou com outro perfil: encerra a sessão criada e não guarda nada.
                saveToken(token, false);
                await api.post('auth/logout').catch(() => {});
                saveToken(null);
                throw new Error('Este painel é da administradora. Moradores, síndicos e portaria usam o app condo.mini.');
            }
            saveToken(token, remember);
            await enter(user);
        }
    });
}

export async function logout() {
    await api.post('auth/logout').catch(() => {});
    saveToken(null);
    rememberBrand(null);
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

/** O que espera a administradora em Cadastros: pedidos feitos pelo app e moradores sem apartamento. */
async function refreshBadge() {
    if (!state.condoId) return;
    try {
        const [users, requests] = await Promise.all([
            api.get('users', { condoId: state.condoId }),
            api.get('signup-requests', { condoId: state.condoId })
        ]);
        pendingRequests = requests.length;
        unassignedCount = users.filter((user) => user.role === 'RESIDENT' && !(user.units || []).length).length + pendingRequests;
        renderSidebar();
    } catch {
        // O número é só um lembrete; sem ele o painel continua funcionando.
    }
}

export function refreshSidebarBadge() {
    refreshBadge();
}

function currentRoute() {
    // "#/condominio/novo?administradora=x": o que vem depois do "?" é só para a tela ler.
    const hash = window.location.hash.replace(/^#\/?/, '').split('?')[0];
    const [name, ...rest] = hash.split('/');
    return { name: name || (isPlatform() ? 'administradoras' : 'unidades'), param: rest.join('/') };
}

function route() {
    if (!content) return;
    const { name, param } = currentRoute();
    cleanup?.();
    cleanup = null;
    closeDrawer();
    renderSidebar();
    const ctx = { condo: currentCondo(), condoId: state.condoId, user: state.user, navigate };
    const platformItem = isPlatform() ? PLATFORM_NAV.find((entry) => entry.route === name) : null;
    if (platformItem) {
        cleanup = platformItem.render(content, ctx);
    } else if (name === 'condominio' && (param !== 'novo' || isPlatform())) {
        cleanup = renderCondoEditor(content, { ...ctx, editId: param === 'novo' ? '' : param });
    } else if (name === 'perfil') {
        cleanup = renderProfile(content, { ...ctx, onLogout: async () => { await logout(); showLogin('Você saiu do painel.'); } });
    } else if (!state.condos.length) {
        mount(content, h('div', { class: 'page' }, noCondos()));
    } else {
        const item = CONDO_NAV.find((entry) => entry.route === name) || CONDO_NAV[0];
        cleanup = item.render(content, ctx);
    }
    content.focus({ preventScroll: true });
    window.scrollTo(0, 0);
}

function noCondos() {
    if (isPlatform()) {
        return h('div', { class: 'empty' },
            h('div', { class: 'empty-icon' }, icon('building', 28)),
            h('h3', {}, 'Nenhum condomínio ainda'),
            h('p', {}, 'Cadastre o primeiro condomínio e escolha a administradora que cuida dele.'),
            h('a', { class: 'btn btn-primary', href: '#/condominio/novo' }, icon('plus', 18), h('span', {}, 'Novo condomínio'))
        );
    }
    return h('div', { class: 'empty' },
        h('div', { class: 'empty-icon' }, icon('building', 28)),
        h('h3', {}, 'Nenhum condomínio liberado ainda'),
        h('p', {}, 'Os condomínios da sua administradora aparecem aqui assim que a equipe condo.mini liberar. Fale com a gente para cadastrar um novo.')
    );
}

export function navigate(path) {
    window.location.hash = `#/${path}`;
}

function navList(items, name, extraClass = '') {
    return h('ul', { class: `nav ${extraClass}`.trim() },
        items.map((item) =>
            h('li', {},
                h('a', { href: `#/${item.route}`, class: `nav-link${name === item.route ? ' active' : ''}`, 'aria-current': name === item.route ? 'page' : null },
                    icon(item.icon),
                    h('span', {}, item.label),
                    item.route === 'cadastros' && unassignedCount > 0
                        ? h('span', { class: 'nav-badge', title: pendingRequests ? `${pendingRequests} pedido(s) de cadastro e moradores sem apartamento` : 'Moradores sem apartamento' }, String(unassignedCount))
                        : null
                )
            )
        )
    );
}

function sidebarBrand() {
    const brand = state.user?.brand;
    if (isPlatform() || !brand?.companyId) {
        return h('div', { class: 'sidebar-brand' },
            h('span', { class: 'brand-tile' }, brandMark(26)),
            h('span', { class: 'brand-name' }, 'condo', h('span', { class: 'brand-dot' }, '.'), 'mini'),
            h('span', { class: 'brand-tag' }, isPlatform() ? 'Plataforma' : 'Administradora')
        );
    }
    return h('div', { class: 'sidebar-brand company' },
        brandTile(brand, 44),
        h('span', { class: 'brand-name company-name', title: brand.name }, brand.name),
        h('span', { class: 'brand-tag' }, 'Painel da administradora')
    );
}

function renderSidebar() {
    if (!sidebar) return;
    const { name } = currentRoute();
    const condo = currentCondo();
    const platform = isPlatform();
    mount(
        sidebar,
        sidebarBrand(),
        platform ? navList(PLATFORM_NAV, name, 'nav-platform') : null,
        platform ? h('p', { class: 'nav-section' }, 'Condomínio aberto') : null,
        condoPicker(condo),
        navList(CONDO_NAV, name),
        h('div', { class: 'sidebar-footer' },
            h('a', { href: '#/perfil', class: `profile-link${name === 'perfil' ? ' active' : ''}` },
                h('span', { class: 'avatar' }, initials(state.user?.name)),
                h('span', { class: 'profile-text' },
                    h('strong', {}, state.user?.name || ''),
                    h('small', {}, platform ? 'Equipe condo.mini' : 'Meu perfil')
                )
            ),
            !platform && state.user?.brand?.companyId
                ? h('p', { class: 'powered' }, 'Desenvolvido por ', h('span', {}, 'condo', h('span', { class: 'brand-dot' }, '.'), 'mini'))
                : null
        )
    );
}

/** Condomínio atual no topo da barra; abre a lista para trocar, editar ou (plataforma) criar outro. */
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
        isPlatform()
            ? h('a', { href: '#/condominio/novo', class: 'picker-new', onclick: () => setOpen(false) }, icon('plus', 16), h('span', {}, 'Novo condomínio'))
            : null
    );
    return h('div', { class: 'picker-wrap' }, toggle, list);
}
