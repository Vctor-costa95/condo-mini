// Meu perfil: contato, tema e sair. Igual ao perfil do app para o administrador.

import { api } from '../api.js';
import { setUser, state } from '../state.js';
import { savedTheme, setTheme, THEMES } from '../theme.js';
import { busy, button, field, formError, h, initials, input, mount, toast } from '../ui.js';

export function renderProfile(container, { onLogout }) {
    const user = state.user;
    const email = input({ type: 'email', value: user.email || '', maxlength: 120, autocomplete: 'email' });
    const phone = input({ type: 'tel', value: user.phone || '', maxlength: 30, autocomplete: 'tel' });
    const errorBox = h('div', {});
    const save = button('Salvar contato', { type: 'submit' });

    const themeButtons = h('div', { class: 'segmented', role: 'radiogroup', 'aria-label': 'Tema' });
    function renderThemes() {
        const current = savedTheme();
        mount(
            themeButtons,
            THEMES.map(([key, label]) =>
                h('button', {
                    type: 'button', role: 'radio', 'aria-checked': String(key === current),
                    class: key === current ? 'selected' : '',
                    onclick: () => { setTheme(key); renderThemes(); }
                }, label)
            )
        );
    }
    renderThemes();

    mount(
        container,
        h('div', { class: 'page' },
            h('header', { class: 'page-header' },
                h('div', {}, h('p', { class: 'eyebrow' }, 'Conta'), h('h1', {}, 'Meu perfil'))
            ),
            h('div', { class: 'profile-grid' },
                h('section', { class: 'card card-pad form-stack' },
                    h('div', { class: 'profile-head' },
                        h('span', { class: 'avatar' }, initials(user.name)),
                        h('div', {}, h('h2', {}, user.name), h('p', { class: 'muted' }, `Administrador · usuário ${user.login}`))
                    ),
                    h('form', {
                        class: 'form-stack',
                        onsubmit: async (event) => {
                            event.preventDefault();
                            mount(errorBox);
                            if (email.value.trim() && !email.value.includes('@')) return mount(errorBox, formError('E-mail inválido.'));
                            await busy(save, async () => {
                                try {
                                    const updated = await api.patch('me', { email: email.value.trim(), phone: phone.value.trim() });
                                    setUser({ ...state.user, ...updated });
                                    toast('Contato atualizado.');
                                } catch (error) {
                                    mount(errorBox, formError(error.message));
                                }
                            });
                        }
                    },
                    errorBox,
                    field('E-mail', email),
                    field('Telefone', phone),
                    h('div', {}, save)
                    )
                ),
                h('div', { class: 'form-stack' },
                    h('section', { class: 'card card-pad form-stack' },
                        h('div', {}, h('h3', { class: 'card-title' }, 'Tema'), h('p', { class: 'muted small' }, 'Vale só para este navegador. "Sistema" acompanha o modo claro ou escuro do computador.')),
                        themeButtons
                    ),
                    h('section', { class: 'card card-pad form-stack' },
                        h('div', {}, h('h3', { class: 'card-title' }, 'Sair do painel'), h('p', { class: 'muted small' }, 'Encerra a sessão neste computador. Use em computadores compartilhados.')),
                        h('div', {}, button('Sair da conta', { variant: 'danger-outline', iconName: 'logout', onclick: (event) => busy(event.currentTarget, onLogout) }))
                    )
                )
            )
        )
    );
}
