import type { Endpoint } from './common';
import { auth } from './auth';
import { health } from './health';
import { settings } from './settings';
import { adminCatalog, catalog } from './catalog';

export * from './common';
export * from './auth';
export * from './health';
export * from './settings';
export * from './catalog';

/** Every endpoint group. Server registers from this; OpenAPI is generated from it. */
export const contracts = { auth, health, settings, catalog, adminCatalog } as const;

export function allEndpoints(): Endpoint[] {
  return Object.values(contracts).flatMap((g) => Object.values(g) as Endpoint[]);
}
