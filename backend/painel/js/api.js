// Conversa com a API do condo.mini, que fica na pasta acima do painel (mesmo domínio: sem CORS).

const API_BASE = new URL('../', window.location.href).href.replace(/\/$/, '');
const TOKEN_KEY = 'condomini.painel.token';
const CONDO_KEY = 'condomini.painel.condo';

export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

function store(remember) {
    return remember ? window.localStorage : window.sessionStorage;
}

function readToken() {
    try {
        return window.sessionStorage.getItem(TOKEN_KEY) || window.localStorage.getItem(TOKEN_KEY);
    } catch {
        return null;
    }
}

let token = readToken();
let onUnauthorized = () => {};

/** Chamado quando a sessão cai (401): o painel volta ao login. */
export function setUnauthorizedHandler(handler) {
    onUnauthorized = handler;
}

export function hasToken() {
    return Boolean(token);
}

export function saveToken(value, remember) {
    token = value;
    try {
        window.localStorage.removeItem(TOKEN_KEY);
        window.sessionStorage.removeItem(TOKEN_KEY);
        if (value) store(remember).setItem(TOKEN_KEY, value);
    } catch {
        // Navegador sem armazenamento: o token fica só nesta aba enquanto ela estiver aberta.
    }
}

export function savedCondoId() {
    try {
        return window.localStorage.getItem(CONDO_KEY) || '';
    } catch {
        return '';
    }
}

export function saveCondoId(id) {
    try {
        window.localStorage.setItem(CONDO_KEY, id);
    } catch {
        // Sem armazenamento: vale até recarregar a página.
    }
}

async function request(method, path, { query, body, form, raw } = {}) {
    const url = new URL(API_BASE + '/' + path.replace(/^\//, ''));
    Object.entries(query || {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    let payload;
    if (form) {
        payload = form;
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(url, { method, headers, body: payload, credentials: 'omit', cache: 'no-store' });
    } catch {
        throw new ApiError('Sem conexão com o servidor. Confira a internet e tente de novo.', 0);
    }
    if (response.status === 401 && path !== 'auth/login') {
        saveToken(null);
        onUnauthorized();
        throw new ApiError('Sua sessão terminou. Entre de novo.', 401);
    }
    if (!response.ok) {
        let message = 'Não foi possível concluir. Tente de novo.';
        try {
            const data = await response.json();
            if (data && typeof data.error === 'string') message = data.error;
        } catch {
            // Resposta sem JSON (erro do servidor): fica a mensagem genérica.
        }
        throw new ApiError(message, response.status);
    }
    if (raw) return response;
    if (response.status === 204) return null;
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

export const api = {
    get: (path, query) => request('GET', path, { query }),
    post: (path, body, query) => request('POST', path, { body: body ?? {}, query }),
    put: (path, body, query) => request('PUT', path, { body: body ?? {}, query }),
    patch: (path, body, query) => request('PATCH', path, { body: body ?? {}, query }),
    del: (path, query) => request('DELETE', path, { query }),
    upload: (path, form) => request('POST', path, { form }),

    /**
     * Arquivos protegidos (PDFs): um link comum não leva o token, então baixa com ele
     * e abre a cópia local numa aba nova.
     */
    async openFile(fileUrl, fileName = 'documento.pdf') {
        const path = fileUrl.startsWith(API_BASE) ? fileUrl.slice(API_BASE.length) : new URL(fileUrl).pathname.replace(/^.*?\/files\//, 'files/');
        // Abre a aba já no clique (senão o navegador bloqueia como pop-up) e preenche quando o arquivo chegar.
        const tab = window.open('', '_blank');
        try {
            const response = await request('GET', path, { raw: true });
            const blob = await response.blob();
            const local = URL.createObjectURL(blob);
            if (tab && !tab.closed) {
                tab.location.href = local;
            } else {
                // Pop-up bloqueado: baixa o arquivo em vez de sair do painel.
                const link = document.createElement('a');
                link.href = local;
                link.download = fileName;
                document.body.append(link);
                link.click();
                link.remove();
            }
            setTimeout(() => URL.revokeObjectURL(local), 60_000);
        } catch (error) {
            if (tab) tab.close();
            throw error;
        }
    }
};
