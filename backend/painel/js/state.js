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

export function setUser(user) {
    state.user = user;
    notify();
}
