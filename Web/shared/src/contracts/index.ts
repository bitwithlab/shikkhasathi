import type { Endpoint } from './common';
import { auth } from './auth';
import { health } from './health';
import { settings } from './settings';
import { adminCatalog, catalog } from './catalog';
import { classroom, dashboard, notifications, webhooks } from './classroom';
import { adminReview, adminSellers, sellerApply, sellerCourses } from './seller';
import { discovery, requests, wishlist } from './discovery';
import { adminCommerce, commerce, sellerMoney } from './commerce';

export * from './common';
export * from './auth';
export * from './health';
export * from './settings';
export * from './catalog';
export * from './classroom';
export * from './seller';
export * from './discovery';
export * from './commerce';

/** Every endpoint group. Server registers from this; OpenAPI is generated from it. */
export const contracts = { auth, health, settings, catalog, adminCatalog, classroom, notifications, dashboard, webhooks, sellerApply, adminSellers, sellerCourses, adminReview, discovery, requests, wishlist, commerce, sellerMoney, adminCommerce } as const;

export function allEndpoints(): Endpoint[] {
  return Object.values(contracts).flatMap((g) => Object.values(g) as Endpoint[]);
}
