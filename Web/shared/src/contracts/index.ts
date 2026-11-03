import type { Endpoint } from './common';
import { auth } from './auth';
import { health } from './health';
import { settings } from './settings';
import { adminCatalog, catalog } from './catalog';
import { classroom, dashboard, notifications, webhooks } from './classroom';

export * from './common';
export * from './auth';
export * from './health';
export * from './settings';
export * from './catalog';
export * from './classroom';

/** Every endpoint group. Server registers from this; OpenAPI is generated from it. */
export const contracts = { auth, health, settings, catalog, adminCatalog, classroom, notifications, dashboard, webhooks } as const;

export function allEndpoints(): Endpoint[] {
  return Object.values(contracts).flatMap((g) => Object.values(g) as Endpoint[]);
}
