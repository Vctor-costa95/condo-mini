// Administradoras (só a plataforma): cadastro, marca (logo e cor), suspensão e os administradores de cada uma.

import { api } from '../api.js';
import {
    brandTile, busy, button, confirmDialog, drawer, emptyState, field, formError, h, icon, initials, input, loading, mount,
    normalize, statusChip, toast
} from '../ui.js';

const LOGO_TYPES = ['image/png', 'image/webp', 'image/jpeg'];
const HEX = /^#[0-9A-Fa-f]{6}$/;

export function renderCompanies(container) {
    let alive = true;
    const view = { companies: [], query: '' };
    const page = h('div', { class: 'page' });
    const results = h('div', {});
    const search = input({ type: 'search', placeholder: 'Buscar por nome ou CNPJ', 'aria-label': 'Buscar administradoras' });
    search.addEventListener('input', () => {
        view.query = search.value;
        drawList();
    });
    mount(container, page);
    mount(page, header(), loading());

    function header() {
        return h('header', { class: 'page-header' },
            h('div', {},
                h('p', { class: 'eyebrow' }, 'Plataforma'),
                h('h1', {}, 'Administradoras'),
                h('p', {}, 'Cada administradora vê só os condomínios dela, com a logo dela no app e no painel. Suspender tira o acesso de todos os cadastros dela.')
            ),
            h('div', { class: 'page-actions' }, button('Nova administradora', { iconName: 'plus', onclick: () => openEditor(null) }))
        );
    }

    async function load() {
        view.companies = await api.get('companies');
    }

    function drawList() {
        const query = normalize(view.query);
        const list = view.companies.filter((c) => !query || normalize(`${c.name} ${c.cnpj}`).includes(query));
        if (!view.companies.length) {
            return mount(results, emptyState('briefcase', 'Nenhuma administradora ainda', 'Cadastre a primeira e depois aloque os condomínios dela em Condomínios.'));
        }
        mount(results,
            list.length
                ? h('div', { class: 'company-grid' }, list.map(card))
                : emptyState('search', 'Nada encontrado', 'Confira o nome ou o CNPJ.')
        );
    }

    function card(company) {
        return h('button', { type: 'button', class: `card company-card${company.active ? '' : ' suspended'}`, onclick: () => openEditor(company) },
            h('div', { class: 'company-card-head' },
                brandTile(brandOf(company), 52),
                h('div', { class: 'company-card-title' },
                    h('strong', {}, company.name),
                    h('small', {}, company.cnpj || 'CNPJ não informado')
                ),
                company.active ? statusChip('Ativa', 'success') : statusChip('Suspensa', 'danger')
            ),
            h('div', { class: 'company-card-stats' },
                stat(company.condoCount, company.condoCount === 1 ? 'condomínio' : 'condomínios'),
                stat(company.adminCount, company.adminCount === 1 ? 'administrador' : 'administradores'),
                company.logoUrl ? null : h('span', { class: 'warn-tag' }, icon('image', 14), 'Sem logo')
            )
        );
    }

    function stat(value, label) {
        return h('span', { class: 'company-stat' }, h('strong', {}, String(value)), ` ${label}`);
    }

    async function refresh() {
        await load();
        if (alive) drawList();
    }

    // ---------------------------------------------------------------- editor

    function openEditor(initial) {
        let company = initial;
        const panel = drawer(company ? company.name : 'Nova administradora', { eyebrow: company ? 'Administradora' : 'Plataforma', wide: true });
        const errorBox = h('div', {});
        const form = {
            name: input({ value: company?.name || '', maxlength: 120, placeholder: 'Ex.: Prime Administradora' }),
            cnpj: input({ value: company?.cnpj || '', maxlength: 20, placeholder: '00.000.000/0000-00', inputmode: 'numeric' }),
            colorText: input({ value: company?.brandColor || '', maxlength: 7, placeholder: '#1F4E79', class: 'input color-text' }),
            colorPick: h('input', { type: 'color', class: 'color-pick', value: HEX.test(company?.brandColor || '') ? company.brandColor : '#B08D57', 'aria-label': 'Escolher a cor' }),
            active: h('input', { type: 'checkbox', checked: company ? company.active : true })
        };
        form.colorPick.addEventListener('input', () => { form.colorText.value = form.colorPick.value.toUpperCase(); drawPreview(); });
        form.colorText.addEventListener('input', () => { if (HEX.test(form.colorText.value)) form.colorPick.value = form.colorText.value; drawPreview(); });
        form.name.addEventListener('input', () => drawPreview());

        const preview = h('div', { class: 'logo-previews' });
        const logoBox = h('section', { class: 'form-section' });
        const adminsBox = h('section', { class: 'form-section' });
        const save = button(company ? 'Salvar' : 'Criar administradora', { type: 'submit' });

        function draftBrand() {
            const color = HEX.test(form.colorText.value) ? form.colorText.value : null;
            return { companyId: company?.id || 'novo', name: form.name.value.trim() || 'Nova administradora', logoUrl: company?.logoUrl || null, color };
        }

        /** A marca como aparece na barra do painel, no tema claro e no escuro. */
        function drawPreview() {
            const brand = draftBrand();
            mount(preview, ['light', 'dark'].map((theme) =>
                h('div', { class: `logo-preview ${theme}` },
                    brandTile(brand, 44),
                    h('span', { class: 'logo-preview-text' },
                        h('strong', {}, brand.name),
                        h('small', {}, theme === 'light' ? 'Tema claro' : 'Tema escuro')
                    )
                )
            ));
        }

        function drawLogo() {
            if (!company) {
                return mount(logoBox,
                    h('h3', {}, 'Logo'),
                    h('p', { class: 'muted small' }, 'Crie a administradora primeiro; depois você envia a logo e cadastra os administradores dela.')
                );
            }
            const fileInput = h('input', { type: 'file', accept: LOGO_TYPES.join(','), hidden: true });
            fileInput.addEventListener('change', () => fileInput.files[0] && upload(fileInput.files[0]));
            const zone = h('div', {
                class: 'dropzone', role: 'button', tabindex: '0',
                'aria-label': company.logoUrl ? 'Trocar a logo' : 'Enviar a logo',
                onclick: () => fileInput.click(),
                onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); fileInput.click(); } },
                ondragover: (event) => { event.preventDefault(); zone.classList.add('over'); },
                ondragleave: () => zone.classList.remove('over'),
                ondrop: (event) => { event.preventDefault(); zone.classList.remove('over'); const file = event.dataTransfer.files[0]; if (file) upload(file); }
            },
            icon('upload', 22),
            h('strong', {}, company.logoUrl ? 'Trocar a logo' : 'Enviar a logo'),
            h('span', { class: 'muted small' }, 'PNG, WebP ou JPEG, até 1 MB e pelo menos 128 px. De preferência quadrada e com fundo transparente.'),
            fileInput
            );
            async function upload(file) {
                if (!LOGO_TYPES.includes(file.type)) return toast('Envie a logo em PNG, WebP ou JPEG.', 'danger');
                if (file.size > 1024 * 1024) return toast('Logo grande demais (até 1 MB).', 'danger');
                const data = new FormData();
                data.append('file', file);
                zone.classList.add('is-busy');
                try {
                    company = await api.upload(`companies/${company.id}/logo`, data);
                    toast('Logo atualizada.');
                    afterChange();
                } catch (err) {
                    toast(err.message, 'danger');
                } finally {
                    zone.classList.remove('is-busy');
                }
            }
            mount(logoBox,
                h('div', { class: 'section-title' },
                    h('h3', {}, 'Logo'),
                    company.logoUrl
                        ? button('Remover logo', {
                            variant: 'ghost', small: true, iconName: 'trash',
                            onclick: async (event) => {
                                const ok = await confirmDialog({ title: 'Remover a logo?', message: 'O app e o painel passam a mostrar as iniciais da administradora.', confirmLabel: 'Remover', danger: true });
                                if (!ok) return;
                                await busy(event.currentTarget, async () => {
                                    try {
                                        company = await api.del(`companies/${company.id}/logo`);
                                        toast('Logo removida.');
                                        afterChange();
                                    } catch (err) {
                                        toast(err.message, 'danger');
                                    }
                                });
                            }
                        })
                        : null
                ),
                zone
            );
        }

        async function drawAdmins() {
            if (!company) return mount(adminsBox);
            mount(adminsBox, h('h3', {}, 'Administradores'), loading());
            let admins;
            try {
                admins = await api.get('users', { companyId: company.id });
            } catch (err) {
                return mount(adminsBox, h('h3', {}, 'Administradores'), formError(err.message));
            }
            const addBox = h('div', {});
            mount(adminsBox,
                h('div', { class: 'section-title' },
                    h('h3', {}, 'Administradores'),
                    button('Adicionar', { variant: 'secondary', small: true, iconName: 'userPlus', onclick: () => drawAddForm(addBox) })
                ),
                h('p', { class: 'muted small' }, 'Entram no painel e no app como administradora e veem todos os condomínios dela.'),
                admins.length
                    ? h('div', { class: 'list' }, admins.map((admin) =>
                        h('div', { class: 'list-row' },
                            h('span', { class: 'avatar avatar-s' }, initials(admin.name)),
                            h('span', { class: 'grow' },
                                h('strong', {}, admin.name),
                                h('small', {}, [admin.login, admin.email].filter(Boolean).join(' · '))
                            ),
                            h('button', {
                                type: 'button', class: 'icon-btn danger', title: `Excluir ${admin.name}`, 'aria-label': `Excluir ${admin.name}`,
                                onclick: async () => {
                                    const ok = await confirmDialog({ title: `Excluir ${admin.name}?`, message: 'A pessoa deixa de entrar no painel e no app. Não dá para desfazer.', confirmLabel: 'Excluir', danger: true });
                                    if (!ok) return;
                                    try {
                                        await api.del(`users/${admin.id}`);
                                        toast(`${admin.name} excluído.`);
                                        afterChange();
                                    } catch (err) {
                                        toast(err.message, 'danger');
                                    }
                                }
                            }, icon('trash', 16))
                        )
                    ))
                    : h('p', { class: 'list-empty' }, 'Ninguém ainda. Adicione quem vai usar o painel.'),
                addBox
            );
        }

        function drawAddForm(box) {
            const fields = {
                name: input({ maxlength: 120, placeholder: 'Nome completo' }),
                email: input({ type: 'email', maxlength: 160, placeholder: 'email@administradora.com.br' }),
                phone: input({ type: 'tel', maxlength: 40, placeholder: '(11) 90000-0000' }),
                login: input({ maxlength: 80, placeholder: 'nome.sobrenome', autocapitalize: 'none' }),
                password: input({ type: 'password', maxlength: 120, autocomplete: 'new-password', placeholder: 'Pelo menos 6 caracteres' })
            };
            const err = h('div', {});
            const add = button('Criar administrador', { small: true });
            add.addEventListener('click', () => busy(add, async () => {
                mount(err);
                const values = {
                    name: fields.name.value.trim(), email: fields.email.value.trim(), phone: fields.phone.value.trim(),
                    login: fields.login.value.trim().toLowerCase(), password: fields.password.value
                };
                const problem = !values.name ? 'Informe o nome.'
                    : values.email && !values.email.includes('@') ? 'E-mail inválido.'
                        : !values.login || /\s/.test(values.login) ? 'Crie um usuário sem espaços.'
                            : values.password.length < 6 ? 'A senha precisa ter pelo menos 6 caracteres.'
                                : null;
                if (problem) return mount(err, formError(problem));
                try {
                    const available = await api.get('logins/available', { login: values.login });
                    if (available && available.available === false) return mount(err, formError('Esse usuário já é de outro cadastro.'));
                    const saved = await api.post('users', { ...values, role: 'ADMIN', companyId: company.id });
                    toast(`${saved.name} pode entrar no painel.`);
                    afterChange();
                } catch (error) {
                    mount(err, formError(error.message));
                }
            }));
            mount(box,
                h('div', { class: 'card card-pad add-admin' },
                    err,
                    h('div', { class: 'form-grid' },
                        h('div', { class: 'span-2' }, field('Nome', fields.name)),
                        field('E-mail', fields.email),
                        field('Telefone', fields.phone),
                        field('Usuário', fields.login),
                        field('Senha', fields.password)
                    ),
                    h('div', { class: 'dialog-actions' }, button('Cancelar', { variant: 'ghost', small: true, onclick: () => mount(box) }), add)
                )
            );
            fields.name.focus();
        }

        /** Depois de salvar algo: lista, barra do drawer e (se mudou a marca) o seletor de condomínios. */
        function afterChange() {
            panel.setTitle(company.name);
            drawPreview();
            drawLogo();
            drawAdmins();
            drawFooter();
            refresh();
        }

        function drawFooter() {
            mount(panel.footer,
                company && company.condoCount === 0
                    ? button('Excluir', {
                        variant: 'danger-outline', iconName: 'trash',
                        onclick: async () => {
                            const ok = await confirmDialog({
                                title: `Excluir ${company.name}?`,
                                message: company.adminCount ? `Os ${company.adminCount} administrador(es) dela também são excluídos. Não dá para desfazer.` : 'Não dá para desfazer.',
                                confirmLabel: 'Excluir', danger: true
                            });
                            if (!ok) return;
                            try {
                                await api.del(`companies/${company.id}`);
                                toast(`${company.name} excluída.`);
                                panel.close();
                                refresh();
                            } catch (err) {
                                toast(err.message, 'danger');
                            }
                        }
                    })
                    : null,
                h('span', { class: 'spacer' }),
                button('Fechar', { variant: 'ghost', onclick: () => panel.close() }),
                save
            );
            save.setAttribute('form', 'company-form');
            save.querySelector('span').textContent = company ? 'Salvar' : 'Criar administradora';
        }

        const formEl = h('form', {
            class: 'form-stack', id: 'company-form', novalidate: true,
            onsubmit: async (event) => {
                event.preventDefault();
                mount(errorBox);
                const color = form.colorText.value.trim().toUpperCase();
                const problem = !form.name.value.trim() ? 'Informe o nome da administradora.'
                    : color && !HEX.test(color) ? 'Cor no formato #RRGGBB, ou deixe em branco.'
                        : null;
                if (problem) return mount(errorBox, formError(problem));
                const wasActive = company?.active;
                if (company && wasActive && !form.active.checked) {
                    const ok = await confirmDialog({
                        title: `Suspender ${company.name}?`,
                        message: 'Ninguém dela entra mais: nem os administradores, nem síndicos, portaria e moradores dos condomínios dela. Dá para reativar depois.',
                        confirmLabel: 'Suspender', danger: true
                    });
                    if (!ok) return;
                }
                await busy(save, async () => {
                    try {
                        const isNew = !company;
                        company = await api.post('companies', {
                            id: company?.id || '', name: form.name.value.trim(), cnpj: form.cnpj.value.trim(),
                            brandColor: color, active: form.active.checked
                        });
                        toast(isNew ? `${company.name} criada. Agora envie a logo e cadastre os administradores.` : `${company.name} salva.`);
                        afterChange();
                    } catch (err) {
                        mount(errorBox, formError(err.message));
                    }
                });
            }
        },
        errorBox,
        h('div', { class: 'form-grid' },
            h('div', { class: 'span-2' }, field('Nome', form.name)),
            field('CNPJ', form.cnpj),
            field('Cor da marca', h('span', { class: 'color-field' }, form.colorPick, form.colorText), { hint: 'Entra só nos detalhes. Em branco, fica a cor do condo.mini.' }),
            h('label', { class: 'check span-2' }, form.active, h('span', {}, h('strong', {}, 'Ativa'), h('small', { class: 'muted' }, ' · desmarque para suspender o acesso de todos os cadastros dela')))
        ),
        h('section', { class: 'form-section' }, h('h3', {}, 'Como aparece'), preview)
        );

        mount(panel.body, formEl, logoBox, adminsBox);
        drawPreview();
        drawLogo();
        drawAdmins();
        drawFooter();
    }

    load()
        .then(() => {
            if (!alive) return;
            mount(page, header(), h('div', { class: 'toolbar' }, h('div', { class: 'search', style: { flex: '1 1 320px', maxWidth: '440px' } }, icon('search', 18), search)), results);
            drawList();
        })
        .catch((err) => alive && mount(page, header(), formError(err.message)));

    return () => { alive = false; };
}

function brandOf(company) {
    return { companyId: company.id, name: company.name, logoUrl: company.logoUrl, color: company.brandColor || null };
}
