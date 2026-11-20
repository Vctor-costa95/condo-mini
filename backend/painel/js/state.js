// Estado compartilhado do painel: quem entrou, os condomínios e qual está aberto.

import { api, saveCondoId, savedCondoId } from './api.js';

const listeners = new Set();

export const state = {
    user: null,
    condos: [],
    condoId: ''
};

export function subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

function notify() {
    listeners.forEach((listener) => listener(state));
}

/** A equipe Mini Condo: cadastra administradoras e vê todos os condomínios. */
export function isPlatform() {
    return state.user?.role === 'PLATFORM';
}

export function currentCondo() {
    return state.condos.find((condo) => condo.id === state.condoId) || null;
}

/** Recarrega os condomínios; mantém o escolhido se ele ainda existir, senão abre o primeiro. */
export async function loadCondos(preferredId) {
    state.condos = await api.get('condos');
    const wanted = preferredId || state.condoId || savedCondoId();
    state.condoId = state.condos.some((condo) => condo.id === wanted) ? wanted : state.condos[0]?.id || '';
    saveCondoId(state.condoId);
    notify();
}

export function selectCondo(id) {
    if (id === state.condoId) return;
    state.condoId = id;
    saveCondoId(id);
    notify();
}

/** Maior arquivo que a API aceita (vem do /me); 15 MB se o servidor ainda não informa. */
export function maxUploadBytes() {
    return state.user?.maxUploadBytes || 15 * 1024 * 1024;
}

/** O limite em texto para as mensagens: "30 MB", "12,5 MB". */
export function maxUploadLabel() {
    const mb = maxUploadBytes() / (1024 * 1024);
    return `${Number.isInteger(mb) ? mb : mb.toFixed(1).replace('.', ',')} MB`;
}

export function setUser(user) {
    state.user = user;
    notify();
}
