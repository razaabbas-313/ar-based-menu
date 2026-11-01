import { api } from '../api/client.js';
/** Ephemeral event UUIDs only. No browser identity or persistent tracking cookie. */
export function observe(slug, eventType, {
  dishId = null,
  tableToken = null,
  category = null
} = {}) {
  if (!slug || !globalThis.crypto?.randomUUID) return;
  const input = {
    slug,
    eventType,
    eventId: crypto.randomUUID(),
    dishId,
    tableToken,
    category
  };
  api.recordInsight(input).catch(() => {});
}
