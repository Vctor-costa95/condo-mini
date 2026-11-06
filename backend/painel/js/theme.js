// Tema Sistema / Claro / Escuro, como no "Meu perfil" do app. Fica salvo neste navegador.

const KEY = 'condomini.painel.tema';

export const THEMES = [
    ['system', 'Sistema'],
    ['light', 'Claro'],
    ['dark', 'Escuro']
];

export function savedTheme() {
    try {
        const value = window.localStorage.getItem(KEY);
        return THEMES.some(([key]) => key === value) ? value : 'system';
    } catch {
        return 'system';
    }
}

export function applyTheme(theme = savedTheme()) {
    if (theme === 'system') {
        document.documentElement.removeAttribute('data-theme');
    } else {
        document.documentElement.setAttribute('data-theme', theme);
    }
}

export function setTheme(theme) {
    try {
        window.localStorage.setItem(KEY, theme);
    } catch {
        // Sem armazenamento: vale até fechar a aba.
    }
    applyTheme(theme);
}
