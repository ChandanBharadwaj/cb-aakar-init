// Viewer backdrops (Mahaul) for the portal. The list itself is data from GET /admin/api/environments (nothing is
// hard-coded here any more); which preset keys the storefront viewer has actually built comes from the design tokens,
// whose `environments` keys are the storefront's preset keys (apps/web src/lib/viewer/environments.ts is typed on them).
import { environments as viewerPresets } from "@aakar/design-tokens";
import type { Environment } from "@/lib/api/types";
import { humanize } from "@/lib/format";

/** Backdrops in display order (`sort_order`, then id). */
export function sortEnvironments(list: readonly Environment[]): Environment[] {
  return [...list].sort((a, b) => a.sort_order - b.sort_order || a.id.localeCompare(b.id));
}

/** A backdrop's plain name from the fetched list; a humanised id while the list loads or for an id the API doesn't know. */
export function environmentLabel(list: readonly Environment[] | undefined, id: string | null | undefined): string {
  if (!id) return "—";
  return list?.find((e) => e.id === id)?.label ?? humanize(id);
}

/** True when the storefront viewer has built this preset; a pending one renders as the studio until engineering adds it. */
export function presetBuilt(presetKey: string): boolean {
  return Object.prototype.hasOwnProperty.call(viewerPresets, presetKey);
}

/** The palette's swatches, the backdrop colour first; an environment without a palette shows none. */
export function swatches(env: Pick<Environment, "palette"> | undefined): string[] {
  return env?.palette ?? [];
}
