import { environments, type EnvironmentId } from "@aakar/design-tokens";

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

/** Template `environment` → nearest drei preset (PLAN §8.2). */
export const ENVIRONMENT_PRESET: Record<EnvironmentId, DreiPreset> = {
  desk_oak: "apartment",
  teak_table_candlelight: "night",
  studio: "studio",
  dashboard: "warehouse",
  kitchen_marble: "lobby",
  balcony_daylight: "sunset",
};

export function isEnvironmentId(value: string | undefined): value is EnvironmentId {
  return value !== undefined && Object.prototype.hasOwnProperty.call(environments, value);
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
