// Cadastros: pessoas do condomínio, com busca, filtro por perfil e o formulário de cadastro.

import { api } from '../api.js';
import { refreshSidebarBadge } from '../main.js';
import { hasUnits, ROLES, towerUnits } from '../units.js';
import {
    busy, button, confirmDialog, drawer, emptyState, field, formError, h, icon, initials, input, loading,
    mount, normalize, select, toast
} from '../ui.js';

export function renderUsers(container, { condo, user: me }) {
    let alive = true;
    const view = { users: [], role: 'ALL', query: '' };

    const page = h('div', { class: 'page' });
    const results = h('div', {});
    const search = input({ type: 'search', placeholder: 'Buscar por nome, apartamento, e-mail ou usuário', 'aria-label': 'Buscar cadastros' });
    search.addEventListener('input', () => {
        view.query = search.value;
        renderResults();
    });
    mount(container, page);
    mount(page, header(), loading());

    function header() {
        return h('header', { class: 'page-header' },
            h('div', {},
                h('p', { class: 'eyebrow' }, condo.name),
                h('h1', {}, 'Cadastros'),
                h('p', {}, 'Moradores, síndico, portaria e administradores. Cada pessoa entra no app com o usuário e a senha criados aqui.')
            ),
            h('div', { class: 'page-actions' }, button('Novo cadastro', { iconName: 'plus', onclick: () => openForm(null) }))
        );
    }

    async function load() {
        view.users = await api.get('users', { condoId: condo.id });
    }

    function unassigned() {
        return view.users.filter((user) => user.role === 'RESIDENT' && !(user.units || []).length);
    }

    function render() {
        if (!alive) return;
        const counts = { ALL: view.users.length };
        Object.keys(ROLES).forEach((role) => (counts[role] = view.users.filter((user) => user.role === role).length));
        const missing = unassigned();
        mount(
            page,
            header(),
            missing.length
                ? h('div', { class: 'notice', role: 'status' }, icon('alert', 18),
                    missing.length === 1
                        ? `${missing[0].name} ainda não tem apartamento. Edite o cadastro ou aloque pela tela de Unidades.`
                        : `${missing.length} moradores ainda não têm apartamento. Eles aparecem primeiro na lista.`)
                : null,
            h('div', { class: 'toolbar', style: { marginTop: missing.length ? '18px' : '0' } },
                h('div', { class: 'pills', role: 'group', 'aria-label': 'Filtrar por perfil' },
                    [['ALL', 'Todos'], ...Object.entries(ROLES)].map(([role, label]) =>
                        h('button', {
                            type: 'button', class: `pill${view.role === role ? ' selected' : ''}`, 'aria-pressed': String(view.role === role),
                            onclick: () => { view.role = role; render(); }
                        }, label, h('span', { class: 'count' }, String(counts[role] || 0)))
                    )
                ),
                h('div', { class: 'search', style: { flex: '1 1 320px', maxWidth: '440px' } }, icon('search', 18), search)
            ),
            results
        );
        renderResults();
    }

    function renderResults() {
        const needle = normalize(view.query);
        const list = view.users
            .filter((user) => view.role === 'ALL' || user.role === view.role)
            .filter((user) => !needle || [user.name, user.email, user.login, ...(user.units || [])].some((text) => normalize(text).includes(needle)))
            .sort((a, b) => {
                const aMissing = a.role === 'RESIDENT' && !(a.units || []).length;
                const bMissing = b.role === 'RESIDENT' && !(b.units || []).length;
                if (aMissing !== bMissing) return aMissing ? -1 : 1;
                return a.name.localeCompare(b.name, 'pt-BR');
            });

        if (!list.length) {
            mount(results, h('div', { class: 'card' },
                emptyState('search', view.users.length ? 'Ninguém encontrado' : 'Nenhum cadastro ainda',
                    view.users.length ? 'Tente outro nome ou limpe o filtro.' : 'Crie o primeiro cadastro para os moradores entrarem no app.')));
            return;
        }
        mount(results,
            h('div', { class: 'card table-card' },
                h('div', { class: 'table-scroll' },
                    h('table', { class: 'table' },
                        h('thead', {}, h('tr', {},
                            h('th', {}, 'Nome'), h('th', {}, 'Perfil'), h('th', {}, 'Apartamentos'), h('th', {}, 'Contato'), h('th', {}, 'Usuário'), h('th', {}, h('span', { class: 'sr-only' }, 'Ações'))
                        )),
                        h('tbody', {}, list.map(row))
                    )
                )
            ),
            h('p', { class: 'muted small', style: { marginTop: '10px' } }, `${list.length} de ${view.users.length} cadastros`)
        );
    }

    function row(user) {
        const units = user.units || [];
        const missing = user.role === 'RESIDENT' && !units.length;
        return h('tr', { onclick: () => openForm(user), tabindex: '0', onkeydown: (event) => event.key === 'Enter' && openForm(user) },
            h('td', {}, h('div', { class: 'person' },
                h('span', { class: 'avatar avatar-s' }, initials(user.name)),
                h('div', {}, h('strong', {}, user.name), user.id === me.id ? h('small', {}, 'Você') : null)
            )),
            h('td', {}, ROLES[user.role] || user.role),
            h('td', {}, missing
                ? h('span', { class: 'warn-tag' }, icon('alert', 15), 'Sem apartamento')
                : hasUnits(user.role)
                    ? h('div', { class: 'units-cell' }, units.map((unit, index) => h('span', { class: `unit-tag${index === 0 && units.length > 1 ? ' main' : ''}`, title: index === 0 && units.length > 1 ? 'Apartamento principal' : null }, unit)))
                    : h('span', { class: 'muted' }, '—')),
            h('td', {}, h('div', {}, user.email || h('span', { class: 'muted' }, 'Sem e-mail')), user.phone ? h('small', { class: 'muted' }, user.phone) : null),
            h('td', {}, h('code', {}, user.login || '')),
            h('td', { class: 'actions' },
                h('button', { type: 'button', class: 'icon-btn', title: 'Editar', 'aria-label': `Editar ${user.name}`, onclick: (event) => { event.stopPropagation(); openForm(user); } }, icon('edit', 18)),
                user.id !== me.id
                    ? h('button', { type: 'button', class: 'icon-btn danger', title: 'Excluir', 'aria-label': `Excluir ${user.name}`, onclick: (event) => { event.stopPropagation(); remove(user); } }, icon('trash', 18))
                    : null
            )
        );
    }

    async function remove(user) {
        const ok = await confirmDialog({
            title: 'Excluir cadastro?',
            message: `${user.name} perde o acesso ao app e sai de todos os apartamentos. Não dá para desfazer.`,
            confirmLabel: 'Excluir', danger: true
        });
        if (!ok) return;
        try {
            await api.del(`users/${user.id}`);
            await load();
            render();
            refreshSidebarBadge();
            toast(`Cadastro de ${user.name} excluído.`);
        } catch (error) {
            toast(error.message, 'danger');
        }
    }

    // -------------------------------------------------------------- formulário

    function openForm(user) {
        const isNew = !user;
        const panel = drawer(isNew ? 'Novo cadastro' : user.name, { eyebrow: isNew ? condo.name : 'Editar cadastro', wide: true });
        const form = {
            name: input({ value: user?.name || '', maxlength: 120, autocomplete: 'off' }),
            email: input({ type: 'email', value: user?.email || '', maxlength: 120 }),
            phone: input({ type: 'tel', value: user?.phone || '', maxlength: 30 }),
            login: input({ value: user?.login || '', maxlength: 40, autocapitalize: 'none', spellcheck: 'false' }),
            password: input({ type: 'password', maxlength: 72, autocomplete: 'new-password', placeholder: isNew ? '' : 'Deixe em branco para manter a atual' }),
            role: select(Object.entries(ROLES), user?.role || 'RESIDENT')
        };
        // O usuário de login não tem espaços nem maiúsculas, como no app.
        form.login.addEventListener('input', () => {
            const clean = form.login.value.replace(/\s+/g, '').toLowerCase();
            if (clean !== form.login.value) form.login.value = clean;
        });
        let units = [...(user?.units || [])];
        const errorBox = h('div', {});
        const unitsSection = h('section', { class: 'form-section' });
        form.role.addEventListener('change', drawUnits);

        function drawUnits() {
            if (!hasUnits(form.role.value)) {
                mount(unitsSection,
                    h('h3', {}, 'Apartamentos'),
                    h('p', { class: 'muted small' }, form.role.value === 'ADMIN'
                        ? 'Administradores não moram no condomínio e veem todos os condomínios.'
                        : 'A portaria não tem apartamento.'));
                return;
            }
            mount(unitsSection,
                h('h3', {}, 'Apartamentos'),
                h('p', { class: 'muted small' }, 'Escolha um ou mais. O primeiro é o principal: é o que o app abre primeiro.'),
                units.length
                    ? h('div', { class: 'selected-units' }, units.map((unit, index) =>
                        h('span', { class: `unit-tag${index === 0 ? ' main' : ''}` },
                            index === 0 ? 'Principal · ' : '', unit,
                            index > 0 ? h('button', { type: 'button', title: 'Tornar principal', 'aria-label': `Tornar ${unit} o principal`, onclick: () => { units = [unit, ...units.filter((u) => u !== unit)]; drawUnits(); } }, icon('check', 14)) : null,
                            h('button', { type: 'button', title: 'Remover', 'aria-label': `Remover ${unit}`, onclick: () => { units = units.filter((u) => u !== unit); drawUnits(); } }, icon('close', 14))
                        )))
                    : h('p', { class: 'warn-tag' }, icon('alert', 15), 'Nenhum apartamento escolhido'),
                h('div', { class: 'unit-picker' }, condo.towers.map((tower) => {
                    const towerList = towerUnits(tower);
                    const chosen = towerList.filter((unit) => units.includes(unit.label)).length;
                    return h('details', { class: 'unit-picker-tower', open: condo.towers.length === 1 || chosen > 0 },
                        h('summary', {}, tower.name, h('small', {}, chosen ? `${chosen} escolhido${chosen > 1 ? 's' : ''}` : `${towerList.length} apartamentos`)),
                        h('div', { class: 'unit-picker-grid' }, towerList.map((unit) => {
                            const index = units.indexOf(unit.label);
                            return h('button', {
                                type: 'button', title: unit.label,
                                class: `unit-option${index >= 0 ? ' selected' : ''}${index === 0 ? ' main' : ''}`,
                                'aria-pressed': String(index >= 0),
                                onclick: () => {
                                    units = index >= 0 ? units.filter((u) => u !== unit.label) : [...units, unit.label];
                                    drawUnits();
                                }
                            }, unit.number);
                        }))
                    );
                }))
            );
        }
        drawUnits();

        const save = button(isNew ? 'Criar cadastro' : 'Salvar', { type: 'submit' });
        const formEl = h('form', {
            class: 'form-stack', id: 'user-form', novalidate: true,
            onsubmit: async (event) => {
                event.preventDefault();
                mount(errorBox);
                const values = {
                    name: form.name.value.trim(),
                    email: form.email.value.trim(),
                    phone: form.phone.value.trim(),
                    login: form.login.value.trim().toLowerCase(),
                    password: form.password.value,
                    role: form.role.value
                };
                const error =
                    !values.name ? 'Informe o nome.'
                        : values.email && !values.email.includes('@') ? 'E-mail inválido.'
                            : !values.login ? 'Crie um usuário para a pessoa entrar no app.'
                                : /\s/.test(values.login) ? 'O usuário não pode ter espaços.'
                                    : isNew && values.password.length < 6 ? 'A senha precisa ter pelo menos 6 caracteres.'
                                        : !isNew && values.password && values.password.length < 6 ? 'A senha nova precisa ter pelo menos 6 caracteres.'
                                            : null;
                if (error) return mount(errorBox, formError(error));
                await busy(save, async () => {
                    try {
                        const available = await api.get('logins/available', { login: values.login, exceptUserId: user?.id || '' });
                        if (available && available.available === false) return mount(errorBox, formError('Esse usuário já é de outro cadastro.'));
                        const body = {
                            id: user?.id || '',
                            condoId: condo.id,
                            name: values.name,
                            role: values.role,
                            email: values.email,
                            phone: values.phone,
                            login: values.login,
                            units: hasUnits(values.role) ? units : []
                        };
                        if (values.password) body.password = values.password;
                        const saved = await api.post('users', body);
                        await load();
                        render();
                        refreshSidebarBadge();
                        panel.close();
                        toast(isNew ? `Cadastro criado: ${saved.name}.` : `Cadastro de ${saved.name} atualizado.`);
                    } catch (err) {
                        mount(errorBox, formError(err.message));
                    }
                });
            }
        },
        errorBox,
        h('div', { class: 'form-grid' },
            h('div', { class: 'span-2' }, field('Nome completo', form.name)),
            field('E-mail', form.email),
            field('Telefone', form.phone),
            field('Perfil', form.role)
        ),
        h('section', { class: 'form-section' },
            h('h3', {}, 'Acesso ao app'),
            h('div', { class: 'form-grid' },
                field('Usuário', form.login, { hint: 'Sem espaços. É o que a pessoa digita para entrar.' }),
                field(isNew ? 'Senha' : 'Nova senha', form.password, { hint: isNew ? 'Pelo menos 6 caracteres.' : 'Pelo menos 6 caracteres, ou em branco para manter.' })
            )
        ),
        unitsSection
        );

        mount(panel.body, formEl);
        mount(panel.footer,
            !isNew && user.id !== me.id
                ? h('span', { class: 'spacer' }, button('Excluir', { variant: 'danger-outline', iconName: 'trash', onclick: () => { panel.close(); remove(user); } }))
                : null,
            button('Cancelar', { variant: 'ghost', onclick: () => panel.close() }),
            save
        );
        save.setAttribute('form', 'user-form');
    }

    load()
        .then(render)
        .catch((error) => alive && mount(page, header(), formError(error.message)));

    return () => {
        alive = false;
    };
}
