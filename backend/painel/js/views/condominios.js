// Condomínios (só a plataforma): todos, com a administradora de cada um, filtro e troca de administradora.

import { api } from '../api.js';
import { loadCondos, selectCondo } from '../state.js';
import { totalUnits } from '../units.js';
import { button, confirmDialog, emptyState, formError, h, icon, input, loading, mount, normalize, select, statusChip, toast } from '../ui.js';

const NONE = '__none__';

export function renderCondoList(container, { navigate }) {
    let alive = true;
    const view = { condos: [], companies: [], filter: 'ALL', query: '' };
    const page = h('div', { class: 'page' });
    const results = h('div', {});
    const filters = h('div', { class: 'pills', role: 'group', 'aria-label': 'Filtrar por administradora' });
    const search = input({ type: 'search', placeholder: 'Buscar por nome ou endereço', 'aria-label': 'Buscar condomínios' });
    search.addEventListener('input', () => {
        view.query = search.value;
        drawTable();
    });
    mount(container, page);
    mount(page, header(), loading());

    function header() {
        return h('header', { class: 'page-header' },
            h('div', {},
                h('p', { class: 'eyebrow' }, 'Plataforma'),
                h('h1', {}, 'Condomínios'),
                h('p', {}, 'Todos os condomínios do Mini Condo. Os administradores de uma administradora veem só os condomínios alocados nela.')
            ),
            h('div', { class: 'page-actions' },
                button('Novo condomínio', {
                    iconName: 'plus',
                    onclick: () => navigate(`condominio/novo${view.filter !== 'ALL' && view.filter !== NONE ? `?administradora=${encodeURIComponent(view.filter)}` : ''}`)
                })
            )
        );
    }

    async function load() {
        [view.condos, view.companies] = await Promise.all([api.get('condos'), api.get('companies')]);
    }

    const companyName = (id) => view.companies.find((c) => c.id === id)?.name || '';

    function drawFilters() {
        const count = (id) => view.condos.filter((c) => (id === NONE ? !c.companyId : c.companyId === id)).length;
        const options = [
            ['ALL', 'Todas', view.condos.length],
            ...view.companies.map((c) => [c.id, c.name, count(c.id)]),
            ...(count(NONE) ? [[NONE, 'Sem administradora', count(NONE)]] : [])
        ];
        mount(filters, options.map(([id, label, n]) =>
            h('button', {
                type: 'button', class: `pill${view.filter === id ? ' selected' : ''}`, 'aria-pressed': String(view.filter === id),
                onclick: () => { view.filter = id; drawFilters(); drawTable(); }
            }, label, h('span', { class: 'count' }, String(n)))
        ));
    }

    function drawTable() {
        const query = normalize(view.query);
        const list = view.condos
            .filter((c) => view.filter === 'ALL' || (view.filter === NONE ? !c.companyId : c.companyId === view.filter))
            .filter((c) => !query || normalize(`${c.name} ${c.address}`).includes(query));
        if (!view.condos.length) {
            return mount(results, emptyState('building', 'Nenhum condomínio ainda', 'Cadastre o primeiro em "Novo condomínio".'));
        }
        if (!list.length) {
            return mount(results, emptyState('search', 'Nada encontrado', 'Troque o filtro ou a busca.'));
        }
        mount(results,
            h('div', { class: 'card table-card' },
                h('div', { class: 'table-scroll' },
                    h('table', { class: 'table' },
                        h('thead', {}, h('tr', {},
                            h('th', {}, 'Condomínio'),
                            h('th', {}, 'Apartamentos'),
                            h('th', {}, 'Administradora'),
                            h('th', { class: 'actions' }, h('span', { class: 'sr-only' }, 'Ações'))
                        )),
                        h('tbody', {}, list.map(row))
                    )
                )
            )
        );
    }

    function row(condo) {
        const units = condo.towers.reduce((sum, tower) => sum + totalUnits(tower), 0);
        const owner = select(
            [['', 'Sem administradora'], ...view.companies.map((c) => [c.id, c.active ? c.name : `${c.name} (suspensa)`])],
            condo.companyId || '',
            { 'aria-label': `Administradora de ${condo.name}`, class: 'input select select-compact' }
        );
        owner.addEventListener('click', (event) => event.stopPropagation());
        owner.addEventListener('change', () => move(condo, owner));
        return h('tr', { onclick: () => open(condo) },
            h('td', {},
                h('div', { class: 'person' },
                    h('span', { class: 'avatar avatar-s' }, icon('building', 15)),
                    h('span', {},
                        h('strong', {}, condo.name),
                        h('small', {}, condo.address || 'Sem endereço')
                    )
                )
            ),
            h('td', {}, `${units} em ${condo.towers.length} ${condo.towers.length === 1 ? 'bloco' : 'blocos'}`),
            h('td', {}, owner, condo.companyId ? null : h('span', { class: 'owner-warn' }, statusChip('Só a plataforma vê', 'warning'))),
            h('td', { class: 'actions' },
                h('a', {
                    href: `#/condominio/${condo.id}`, class: 'icon-btn', title: `Editar ${condo.name}`, 'aria-label': `Editar ${condo.name}`,
                    onclick: (event) => event.stopPropagation()
                }, icon('edit', 16)),
                h('button', { type: 'button', class: 'icon-btn', title: `Abrir ${condo.name}`, 'aria-label': `Abrir ${condo.name}` }, icon('open', 16))
            )
        );
    }

    /** Abre o condomínio nas telas de sempre (unidades, cadastros, espaços), para dar suporte. */
    function open(condo) {
        selectCondo(condo.id);
        navigate('unidades');
        toast(`Abrindo ${condo.name}.`);
    }

    async function move(condo, owner) {
        const from = companyName(condo.companyId);
        const to = companyName(owner.value);
        const ok = await confirmDialog({
            title: to ? `Mover ${condo.name} para ${to}?` : `Tirar ${condo.name} da administradora?`,
            message: [
                from ? `Os administradores de ${from} deixam de ver este condomínio na hora.` : null,
                to ? `Os de ${to} passam a ver, com a marca de ${to} para síndico, portaria e moradores.` : 'Ele fica sem administradora: só a plataforma vê.'
            ].filter(Boolean).join(' '),
            confirmLabel: to ? 'Mover' : 'Tirar'
        });
        if (!ok) {
            owner.value = condo.companyId || '';
            return;
        }
        owner.disabled = true;
        try {
            await api.put(`condos/${condo.id}/company`, { companyId: owner.value });
            toast(to ? `${condo.name} agora é de ${to}.` : `${condo.name} ficou sem administradora.`);
            await refresh();
            loadCondos().catch(() => {});
        } catch (err) {
            owner.value = condo.companyId || '';
            owner.disabled = false;
            toast(err.message, 'danger');
        }
    }

    async function refresh() {
        await load();
        if (!alive) return;
        drawFilters();
        drawTable();
    }

    load()
        .then(() => {
            if (!alive) return;
            mount(page, header(),
                h('div', { class: 'toolbar' },
                    h('div', { class: 'search', style: { flex: '1 1 320px', maxWidth: '440px' } }, icon('search', 18), search),
                    filters
                ),
                results
            );
            drawFilters();
            drawTable();
        })
        .catch((err) => alive && mount(page, header(), formError(err.message)));

    return () => { alive = false; };
}
