// Regras dos apartamentos, iguais às do app (domain/model/CondoUnit.kt) e da API (tower_unit_labels).

export const GROUND_FLOOR = 0;
export const MAX_FLOORS = 40;
export const MAX_UNITS_PER_FLOOR = 12;

/** Quantidade quando o andar não tem exceção: o térreo não tem apartamentos. */
export function defaultUnitsOn(tower, floor) {
    return floor === GROUND_FLOOR ? 0 : tower.unitsPerFloor;
}

export function unitsOn(tower, floor) {
    const override = tower.floorOverrides?.[floor] ?? tower.floorOverrides?.[String(floor)];
    return override ?? defaultUnitsOn(tower, floor);
}

/** Térreo + andares, de baixo para cima. */
export function allFloors(tower) {
    return Array.from({ length: tower.floors + 1 }, (_, floor) => floor);
}

export function totalUnits(tower) {
    return allFloors(tower).reduce((sum, floor) => sum + unitsOn(tower, floor), 0);
}

/** Numeração: andar + posição com dois dígitos (1004); no térreo, "T" + posição (T01). */
export function towerUnits(tower) {
    return allFloors(tower).flatMap((floor) =>
        Array.from({ length: unitsOn(tower, floor) }, (_, index) => {
            const suffix = String(index + 1).padStart(2, '0');
            const number = floor === GROUND_FLOOR ? `T${suffix}` : `${floor}${suffix}`;
            return { towerId: tower.id, tower: tower.name, floor, number, label: `Apto ${number} - ${tower.name}` };
        })
    );
}

export function condoUnits(condo) {
    return (condo?.towers || []).flatMap(towerUnits);
}

/** "Térreo", "1º", "14º" */
export function floorLabel(floor) {
    return floor === GROUND_FLOOR ? 'Térreo' : `${floor}º`;
}

export const ROLES = {
    RESIDENT: 'Morador',
    MANAGER: 'Síndico',
    DOORMAN: 'Porteiro',
    ADMIN: 'Administrador'
};

/** Quem mora no apartamento (moradores e síndico) pode ter apartamentos. */
export function hasUnits(role) {
    return role === 'RESIDENT' || role === 'MANAGER';
}
