// Editor do condomínio: nome, endereço e blocos (andares, aptos por andar e exceções por andar).

import { api } from '../api.js';
import { loadCondos, state } from '../state.js';
import {
    allFloors, defaultUnitsOn, floorLabel, GROUND_FLOOR, MAX_FLOORS, MAX_UNITS_PER_FLOOR, totalUnits, towerUnits, unitsOn
} from '../units.js';
import { busy, button, confirmDialog, field, formError, h, icon, input, mount, toast } from '../ui.js';

function newTower(towers) {
    // Próxima letra livre: depois de remover o "Bloco B", o novo volta a ser B.
    const used = new Set(towers.map((tower) => tower.name));
    const index = Array.from({ length: 26 }, (_, i) => i).find((i) => !used.has(`Bloco ${String.fromCharCode(65 + i)}`)) ?? towers.length;
    return { id: crypto.randomUUID(), name: `Bloco ${String.fromCharCode(65 + (index % 26))}`, floors: 10, unitsPerFloor: 4, floorOverrides: {} };
}

function clamp(value, min, max) {
    return Math.min(Math.max(Number.isFinite(value) ? value : min, min), max);
}

/** Campo numérico com − e +, como no app. */
function stepper(value, min, max, onchange, label) {
    const field = h('input', { type: 'number', min, max, value, inputmode: 'numeric', 'aria-label': label });
    const minus = h('button', { type: 'button', 'aria-label': `Diminuir ${label}`, disabled: value <= min, onclick: () => onchange(clamp(value - 1, min, max)) }, '−');
    const plus = h('button', { type: 'button', 'aria-label': `Aumentar ${label}`, disabled: value >= max, onclick: () => onchange(clamp(value + 1, min, max)) }, '+');
    field.addEventListener('change', () => onchange(clamp(parseInt(field.value, 10), min, max)));
    return h('span', { class: 'stepper' }, minus, field, plus);
}

export function renderCondoEditor(container, { editId, navigate }) {
    const original = editId ? state.condos.find((condo) => condo.id === editId) : null;
    if (editId && !original) {
        mount(container, h('div', { class: 'page' }, formError('Condomínio não encontrado.')));
        return () => {};
    }
    const isNew = !original;
    const draft = {
        name: original?.name || '',
        address: original?.address || '',
        towers: original
            ? original.towers.map((tower) => ({ ...tower, floorOverrides: Object.fromEntries(Object.entries(tower.floorOverrides || {}).map(([k, v]) => [Number(k), v])) }))
            : [newTower([])]
    };
    let dirty = false;

    const name = input({ value: draft.name, maxlength: 48, placeholder: 'Ex.: Residencial Jardim das Flores' });
    const address = input({ value: draft.address, maxlength: 90, placeholder: 'Rua, número e bairro' });
    name.addEventListener('input', () => { draft.name = name.value; dirty = true; drawSummary(); });
    address.addEventListener('input', () => { draft.address = address.value; dirty = true; });

    const errorBox = h('div', {});
    const towersBox = h('div', { class: 'form-stack' });
    const summary = h('section', { class: 'card summary-card' });
    const save = button(isNew ? 'Criar condomínio' : 'Salvar alterações', { onclick: () => submit() });

    function update(towerId, transform) {
        draft.towers = draft.towers.map((tower) => (tower.id === towerId ? transform(tower) : tower));
        dirty = true;
        mount(errorBox);
        drawTowers();
        drawSummary();
    }

    function drawTowers() {
        mount(towersBox,
            draft.towers.map((tower) => {
                const nameInput = input({ value: tower.name, maxlength: 24, 'aria-label': 'Nome do bloco' });
                nameInput.addEventListener('change', () => update(tower.id, (t) => ({ ...t, name: nameInput.value.slice(0, 24) })));
                const floors = allFloors(tower);
                return h('section', { class: 'card tower-card' },
                    h('div', { class: 'tower-card-head' },
                        nameInput,
                        h('span', { class: 'total' }, `${totalUnits(tower)} apartamentos`),
                        draft.towers.length > 1
                            ? h('button', {
                                type: 'button', class: 'icon-btn danger', title: 'Remover bloco', 'aria-label': `Remover ${tower.name}`,
                                onclick: async () => {
                                    const ok = isNew || await confirmDialog({
                                        title: `Remover ${tower.name}?`,
                                        message: 'Os apartamentos deste bloco deixam de existir ao salvar. Moradores ligados a eles ficam sem apartamento.',
                                        confirmLabel: 'Remover', danger: true
                                    });
                                    if (!ok) return;
                                    draft.towers = draft.towers.filter((t) => t.id !== tower.id);
                                    dirty = true;
                                    drawTowers();
                                    drawSummary();
                                }
                            }, icon('trash', 18))
                            : null
                    ),
                    h('div', { class: 'tower-numbers' },
                        field('Andares', stepper(tower.floors, 1, MAX_FLOORS, (value) =>
                            // Exceções acima do novo último andar deixam de existir.
                            update(tower.id, (t) => ({ ...t, floors: value, floorOverrides: Object.fromEntries(Object.entries(t.floorOverrides).filter(([floor]) => Number(floor) <= value)) })), 'andares')),
                        field('Apartamentos por andar', stepper(tower.unitsPerFloor, 1, MAX_UNITS_PER_FLOOR, (value) =>
                            // Andares iguais ao novo padrão perdem a exceção; o térreo tem padrão próprio (0).
                            update(tower.id, (t) => ({ ...t, unitsPerFloor: value, floorOverrides: Object.fromEntries(Object.entries(t.floorOverrides).filter(([floor, count]) => Number(floor) === GROUND_FLOOR || count !== value)) })), 'apartamentos por andar'))
                    ),
                    h('details', { open: Object.keys(tower.floorOverrides).length > 0 },
                        h('summary', { class: 'muted small', style: { cursor: 'pointer' } }, 'Ajustar andares específicos (térreo, cobertura, andar de lazer)'),
                        h('div', { class: 'floors', style: { marginTop: '12px' } },
                            floors.slice().reverse().map((floor) => {
                                const count = unitsOn(tower, floor);
                                const changed = tower.floorOverrides[floor] !== undefined;
                                return h('div', { class: `floor-chip${changed ? ' changed' : ''}`, title: changed ? 'Diferente do padrão' : null },
                                    h('span', {}, floorLabel(floor)),
                                    stepper(count, 0, MAX_UNITS_PER_FLOOR, (value) =>
                                        update(tower.id, (t) => {
                                            const overrides = { ...t.floorOverrides };
                                            if (value === defaultUnitsOn(t, floor)) delete overrides[floor];
                                            else overrides[floor] = value;
                                            return { ...t, floorOverrides: overrides };
                                        }), `apartamentos no ${floorLabel(floor)}`)
                                );
                            })
                        )
                    )
                );
            }),
            h('div', {}, button('Adicionar bloco', {
                variant: 'secondary', iconName: 'plus',
                onclick: () => { draft.towers = [...draft.towers, newTower(draft.towers)]; dirty = true; drawTowers(); drawSummary(); }
            }))
        );
    }

    function drawSummary() {
        const units = draft.towers.flatMap(towerUnits);
        const sample = draft.towers.map((tower) => towerUnits(tower)).filter((list) => list.length);
        mount(summary,
            h('p', { class: 'eyebrow' }, 'Resumo'),
            h('h3', {}, draft.name.trim() || 'Sem nome'),
            h('div', { class: 'summary-line' }, 'Blocos', h('strong', {}, String(draft.towers.length))),
            h('div', { class: 'summary-line' }, 'Apartamentos', h('strong', {}, String(units.length))),
            sample.length
                ? h('div', { class: 'form-stack' },
                    h('p', { class: 'muted small' }, 'Os apartamentos ficam assim no app:'),
                    h('div', { class: 'sample-units' }, sample.map((list) => [list[0], list[list.length - 1]]).flat().filter((v, i, arr) => arr.indexOf(v) === i).slice(0, 6).map((unit) => h('span', { class: 'unit-tag' }, unit.label))))
                : null,
            !isNew ? h('p', { class: 'muted small' }, 'Renomear ou remover blocos muda os apartamentos que os moradores veem. Confira os cadastros depois de salvar.') : null,
            save,
            button('Cancelar', { variant: 'ghost', onclick: () => leave() })
        );
    }

    async function leave() {
        if (dirty) {
            const ok = await confirmDialog({ title: 'Descartar alterações?', message: 'O que você mudou neste condomínio não foi salvo.', confirmLabel: 'Descartar', danger: true });
            if (!ok) return;
        }
        dirty = false;
        navigate('unidades');
    }

    async function submit() {
        mount(errorBox);
        const names = draft.towers.map((tower) => tower.name.trim().toLowerCase());
        const empty = draft.towers.find((tower) => totalUnits(tower) === 0);
        const error = !draft.name.trim() ? 'Dê um nome ao condomínio.'
            : !draft.towers.length ? 'Adicione pelo menos um bloco.'
                : names.some((n) => !n) ? 'Todos os blocos precisam de nome.'
                    : new Set(names).size !== names.length ? 'Dois blocos estão com o mesmo nome.'
                        : empty ? `${empty.name.trim()} ficou sem nenhum apartamento.`
                            : null;
        if (error) {
            mount(errorBox, formError(error));
            errorBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
            return;
        }
        await busy(save, async () => {
            try {
                const saved = await api.post('condos', {
                    id: original?.id || '',
                    name: draft.name.trim(),
                    address: draft.address.trim(),
                    towers: draft.towers.map((tower) => ({
                        id: tower.id,
                        name: tower.name.trim(),
                        floors: tower.floors,
                        unitsPerFloor: tower.unitsPerFloor,
                        floorOverrides: Object.fromEntries(Object.entries(tower.floorOverrides).map(([floor, count]) => [String(floor), count]))
                    }))
                });
                dirty = false;
                navigate('unidades');
                await loadCondos(saved.id);
                toast(isNew ? `${saved.name} criado. Agora é o condomínio aberto.` : `${saved.name} atualizado.`);
            } catch (err) {
                mount(errorBox, formError(err.message));
            }
        });
    }

    mount(container,
        h('div', { class: 'page' },
            h('header', { class: 'page-header' },
                h('div', {},
                    h('p', { class: 'eyebrow' }, isNew ? 'Novo condomínio' : 'Editar condomínio'),
                    h('h1', {}, isNew ? 'Novo condomínio' : original.name),
                    h('p', {}, 'Os apartamentos saem dos blocos: andar + posição (1004 = 10º andar, apto 4). O térreo só tem apartamentos se você ajustar.')
                )
            ),
            h('div', { class: 'editor' },
                h('div', { class: 'form-stack' },
                    errorBox,
                    h('section', { class: 'card card-pad form-grid' },
                        h('div', { class: 'span-2' }, field('Nome do condomínio', name)),
                        h('div', { class: 'span-2' }, field('Endereço', address))
                    ),
                    towersBox
                ),
                h('aside', { class: 'editor-side' }, summary)
            )
        )
    );
    drawTowers();
    drawSummary();
    name.focus();

    // Aviso do navegador ao fechar a aba com alterações não salvas.
    const beforeUnload = (event) => {
        if (dirty) {
            event.preventDefault();
            event.returnValue = '';
        }
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
}
