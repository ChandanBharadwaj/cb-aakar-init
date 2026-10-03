import { color, environments, type EnvironmentId } from "@aakar/design-tokens";
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

/** A point in the stage's units: the piece is fitted to about 1 unit, its base on the floor at y = 0. */
export type StagePoint = [number, number, number];

/** A light on a built stage. Intensities are three.js physical units (a spot light falls off with distance squared). */
export type StageLight =
  | { kind: "ambient"; color: string; intensity: number }
  | { kind: "hemisphere"; sky: string; ground: string; intensity: number }
  | { kind: "directional"; color: string; intensity: number; position: StagePoint }
  | { kind: "spot"; color: string; intensity: number; position: StagePoint; angle: number; penumbra: number };

/** A soft glowing panel baked once into a local environment map: reflections on glossy finishes, nothing downloaded. */
export interface StageGlow {
  color: string;
  intensity: number;
  position: StagePoint;
  /** Width and height of the panel. */
  scale: [number, number];
}

/**
 * How a backdrop is implemented: a drei HDRI preset (from its CDN; the viewer falls back to plain lights offline), or a
 * stage built here that needs no download (its lights, glows for reflections, the CSS painted behind the transparent
 * canvas and the colour of the contact shadow).
 */
export type BackdropPreset =
  | { kind: "hdri"; preset: DreiPreset }
  | { kind: "built"; lights: readonly StageLight[]; glows: readonly StageGlow[]; backdrop: string; shadow: string };

const STAGE_SHADOW = "#0E1220";

/** The stage behind every HDRI preset: a warm glow on the backdrop colour, fading to the indigo stage. */
function hdriBackdrop(bg: string): string {
  return `radial-gradient(60% 55% at 50% 62%, rgba(216,174,91,.16), transparent 70%), linear-gradient(180deg, ${bg} 0%, ${color.indigo_deep} 100%)`;
}

// ---- Katha: comic rooftop at night ------------------------------------------------------------------------------------

/** The rooftop's palette: the indigo night of the brand tokens, a violet horizon, the marigold of the spotlight. */
const NIGHT = {
  sky: color.indigo_deep,
  haze: "#2A2F5E",
  violet: "#4A3A78",
  skyline: "#0D1122",
  window: color.marigold,
  spot: "#FFD18A",
  moon: "rgba(245,240,230,.92)",
} as const;

/** A row of flat roofs, two water tanks and a mast, a few lit windows: tiled along the bottom of the sky. */
const SKYLINE_SVG = [
  "<svg xmlns='http://www.w3.org/2000/svg' width='480' height='120' viewBox='0 0 480 120'>",
  `<path fill='${NIGHT.skyline}' d='M0 120V78H22V62H44V70H58V48H66V40H70V48H82V66H104V56H128V74H140V36H150V30H156V36H168V60H186V52H206V68H222V44H236V20H240V8H242V20H248V44H262V64H284V50H300V58H316V40H340V72H356V54H380V62H396V46H404V38H412V46H428V70H446V58H466V74H480V120ZM108 56V46H122V56ZM288 50V41H299V50Z'/>`,
  `<g fill='${NIGHT.window}' fill-opacity='.5'>`,
  "<rect x='28' y='70' width='4' height='5'/><rect x='48' y='76' width='4' height='5'/><rect x='72' y='56' width='3' height='4'/>",
  "<rect x='112' y='64' width='4' height='5'/><rect x='144' y='44' width='3' height='4'/><rect x='158' y='52' width='3' height='4'/>",
  "<rect x='192' y='60' width='4' height='5'/><rect x='226' y='52' width='3' height='4'/><rect x='252' y='58' width='3' height='4'/>",
  "<rect x='290' y='60' width='4' height='5'/><rect x='322' y='50' width='4' height='5'/><rect x='364' y='62' width='4' height='5'/>",
  "<rect x='400' y='54' width='3' height='4'/><rect x='452' y='66' width='4' height='5'/>",
  "</g></svg>",
].join("");

/**
 * `comic_rooftop_night` (Katha): an indigo-to-violet night sky with a subtle halftone, a moon, a skyline silhouette
 * along the bottom and a warm spotlight on the piece. The sky is CSS behind the transparent canvas and the light is
 * built here, so it works offline: no HDRI, no CDN.
 */
const COMIC_ROOFTOP_NIGHT: BackdropPreset = {
  kind: "built",
  lights: [
    { kind: "hemisphere", sky: "#7C6FC4", ground: "#141A30", intensity: 0.65 },
    { kind: "directional", color: "#9FB0F0", intensity: 1.1, position: [-2.6, 2.8, -2.4] },
    { kind: "spot", color: NIGHT.spot, intensity: 28, position: [1.4, 3.2, 2], angle: 0.5, penumbra: 0.6 },
    { kind: "ambient", color: "#B8BDD0", intensity: 0.15 },
  ],
  glows: [
    { color: NIGHT.spot, intensity: 2.6, position: [1.5, 3, 2.5], scale: [3, 1.2] },
    { color: "#6B5BB8", intensity: 0.8, position: [-2, 1.5, -3.5], scale: [6, 2.5] },
  ],
  backdrop: [
    `radial-gradient(circle at 82% 15%, ${NIGHT.moon} 0 2.4%, rgba(245,240,230,.14) 3%, transparent 9%)`,
    "conic-gradient(from 196deg at 72% -10%, transparent 0deg, rgba(255,209,138,.1) 11deg, transparent 22deg)",
    "radial-gradient(42% 46% at 50% 64%, rgba(216,174,91,.28), transparent 72%)",
    `url("data:image/svg+xml,${encodeURIComponent(SKYLINE_SVG)}") center bottom / auto 30% repeat-x`,
    "radial-gradient(circle, rgba(184,189,208,.13) 1.1px, transparent 1.7px) 0 0 / 10px 10px",
    `linear-gradient(180deg, ${NIGHT.sky} 0%, ${NIGHT.haze} 50%, ${NIGHT.violet} 100%)`,
  ].join(", "),
  shadow: "#05070F",
};

/**
 * Viewer preset key → how the viewer renders it (PLAN §8.2). This file is the one place a backdrop preset is
 * implemented; which backdrop a piece, an Avatar or a Duniya experience uses is data (`GET /api/environments`). A
 * backdrop whose preset is not built yet renders as the studio.
 */
export const ENVIRONMENT_PRESET: Record<EnvironmentId, BackdropPreset> = {
  desk_oak: { kind: "hdri", preset: "apartment" },
  teak_table_candlelight: { kind: "hdri", preset: "night" },
  studio: { kind: "hdri", preset: "studio" },
  dashboard: { kind: "hdri", preset: "warehouse" },
  kitchen_marble: { kind: "hdri", preset: "lobby" },
  balcony_daylight: { kind: "hdri", preset: "sunset" },
  comic_rooftop_night: COMIC_ROOFTOP_NIGHT,
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

/** How the viewer renders a backdrop: its preset when built, else the studio's. */
export function presetFor(env: string | undefined): BackdropPreset {
  return ENVIRONMENT_PRESET[isEnvironmentId(env) ? env : FALLBACK_ENVIRONMENT];
}

export function environmentLabel(env: string | undefined): string {
  return isEnvironmentId(env) ? environments[env].label : environments.studio.label;
}

/** A tokens colour: a hex value, or a `{color.indigo_deep}` reference to the brand colours. */
function tokenColour(value: string): string {
  const ref = /^\{color\.([a-z_]+)\}$/.exec(value);
  if (!ref) return value;
  return (color as Record<string, string>)[ref[1] ?? ""] ?? color.indigo_deep;
}

/** Backdrop colour behind the model for an environment (token references resolved). */
export function environmentBackground(env: string | undefined): string {
  return tokenColour(isEnvironmentId(env) ? environments[env].bg : environments.studio.bg);
}

/** The CSS `background` painted behind the viewer's transparent canvas for an environment. */
export function environmentBackdrop(env: string | undefined): string {
  const preset = presetFor(env);
  return preset.kind === "built" ? preset.backdrop : hdriBackdrop(environmentBackground(env));
}

/** The colour of the contact shadow under the piece on this backdrop. */
export function environmentShadow(env: string | undefined): string {
  const preset = presetFor(env);
  return preset.kind === "built" ? preset.shadow : STAGE_SHADOW;
}
