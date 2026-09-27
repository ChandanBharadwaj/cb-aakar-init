import { environments, type EnvironmentId } from "@aakar/design-tokens";
import type { Environment } from "@/lib/api/types";

/** drei's built-in HDRI presets (loaded at runtime from the drei-assets CDN). */
export type DreiPreset =
  | "apartment"
  | "city"
  | "dawn"
  | "forest"
  | "lobby"
  | "night"
  | "park"
  | "studio"
  | "sunset"
  | "warehouse";

/**
 * Viewer preset key → nearest drei preset (PLAN §8.2). This file is the one place a backdrop preset is implemented;
 * which backdrop a piece, an Avatar or a Duniya experience uses is data (`GET /api/environments`). A backdrop whose
 * preset is not built yet (Katha's `comic_rooftop_night` until PR 12) renders as the studio.
 */
export const ENVIRONMENT_PRESET: Record<EnvironmentId, DreiPreset> = {
  desk_oak: "apartment",
  teak_table_candlelight: "night",
  studio: "studio",
  dashboard: "warehouse",
  kitchen_marble: "lobby",
  balcony_daylight: "sunset",
};

/** The preset every unknown or unbuilt backdrop falls back to. */
export const FALLBACK_ENVIRONMENT: EnvironmentId = "studio";

export function isEnvironmentId(value: string | undefined): value is EnvironmentId {
  return value !== undefined && Object.prototype.hasOwnProperty.call(environments, value);
}

/** True when the storefront viewer implements this preset key. */
export function hasViewerPreset(key: string | undefined): key is EnvironmentId {
  return isEnvironmentId(key);
}

/**
 * The preset key that renders a backdrop: its `preset_key` from `GET /api/environments` when the list is at hand, else
 * the id itself (the seeded ids equal their keys). Not necessarily built yet: check `hasViewerPreset`.
 */
export function presetKeyFor(environmentId: string, list?: readonly Pick<Environment, "id" | "preset_key">[]): string {
  return list?.find((e) => e.id === environmentId)?.preset_key ?? environmentId;
}

/** The viewer preset that will actually render a backdrop: its own when built, else the studio. */
export function viewerPresetFor(environmentId: string | undefined, list?: readonly Pick<Environment, "id" | "preset_key">[]): EnvironmentId {
  const key = environmentId ? presetKeyFor(environmentId, list) : undefined;
  return hasViewerPreset(key) ? key : FALLBACK_ENVIRONMENT;
}

export function presetFor(env: string | undefined): DreiPreset {
  return isEnvironmentId(env) ? ENVIRONMENT_PRESET[env] : "studio";
}

export function environmentLabel(env: string | undefined): string {
  return isEnvironmentId(env) ? environments[env].label : environments.studio.label;
}

/** Backdrop colour behind the model for an environment; resolves the `{color.indigo_deep}` token reference. */
export function environmentBackground(env: string | undefined): string {
  const bg = isEnvironmentId(env) ? environments[env].bg : environments.studio.bg;
  return bg.startsWith("{") ? "#1B2238" : bg;
}
