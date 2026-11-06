// Entrada do painel. Só a administradora passa; os outros perfis são mandados para o app.

import { api } from '../api.js';
import { brandMark, busy, button, field, formError, h, icon, input, mount } from '../ui.js';

export function renderLogin(root, { message, error, onLoggedIn }) {
    const login = input({ name: 'login', autocomplete: 'username', autocapitalize: 'none', spellcheck: 'false', required: true });
    const password = input({ name: 'password', type: 'password', autocomplete: 'current-password' });
    const remember = h('input', { type: 'checkbox', checked: true });
    const errorBox = h('div', {});
    const submit = button('Entrar', { type: 'submit' });

    function showError(text) {
        mount(errorBox, text ? formError(text) : null);
    }
    showError(error);

    const form = h(
        'form',
        {
            novalidate: true,
            onsubmit: async (event) => {
                event.preventDefault();
                if (!login.value.trim()) return showError('Informe o usuário.');
                showError(null);
                await busy(submit, async () => {
                    try {
                        const session = await api.post('auth/login', { login: login.value.trim(), password: password.value });
                        await onLoggedIn(session, remember.checked);
                    } catch (err) {
                        showError(err.message);
                        password.value = '';
                        password.focus();
                    }
                });
            }
        },
        message ? h('p', { class: 'login-message', role: 'status' }, message) : null,
        errorBox,
        field('Usuário', login),
        field('Senha', password),
        h('label', { class: 'check' }, remember, 'Manter conectado neste computador'),
        submit
    );

    mount(
        root,
        h(
            'div',
            { class: 'login' },
            h(
                'section',
                { class: 'login-hero' },
                h('div', { class: 'sidebar-brand' },
                    h('span', { class: 'brand-tile' }, brandMark(26)),
                    h('span', { class: 'brand-name' }, 'condo', h('span', { class: 'brand-dot' }, '.'), 'mini'),
                    h('span', { class: 'brand-tag on-hero-muted' }, 'Administradora')
                ),
                h(
                    'div',
                    {},
                    h('p', { class: 'eyebrow' }, 'Painel da administradora'),
                    h('h1', {}, 'Seus condomínios, organizados em uma tela.'),
                    h('p', { class: 'lead' }, 'Unidades, boletos em PDF, cadastros de moradores e espaços comuns. Tudo o que o app faz, no conforto do computador.'),
                    h('ul', {},
                        h('li', {}, icon('building', 18), 'Mapa de cada bloco com os boletos do mês'),
                        h('li', {}, icon('users', 18), 'Cadastros com busca e alocação de apartamentos'),
                        h('li', {}, icon('sofa', 18), 'Espaços, regras e interdições')
                    )
                ),
                h('p', { class: 'small on-hero-muted' }, 'condo.mini')
            ),
            h(
                'section',
                { class: 'login-panel' },
                h('div', { class: 'login-card' },
                    h('h2', {}, 'Entrar'),
                    h('p', {}, 'Use o usuário e a senha de administrador.'),
                    form,
                    h('p', { class: 'login-note' }, 'Moradores, síndicos e portaria usam o app condo.mini no celular.')
                )
            )
        )
    );
    login.focus();
}
