import { timestamp, uuid } from 'drizzle-orm/pg-core';
import { uuidv7 } from 'uuidv7';

export const newId = () => uuidv7();

export const id = () => uuid('id').primaryKey().$defaultFn(newId);
export const ts = (name: string) => timestamp(name, { withTimezone: true, mode: 'date' });
export const timestamps = {
  createdAt: ts('created_at').notNull().defaultNow(),
  updatedAt: ts('updated_at')
    .notNull()
    .defaultNow()
    .$onUpdate(() => new Date()),
};
