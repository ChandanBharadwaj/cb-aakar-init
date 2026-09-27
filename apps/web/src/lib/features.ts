// Content features (the Chhaap) as the storefront sends and reads them.
//
// Hand-written mirror of packages/contracts/schemas/design-spec.v1.json `$defs`
// (feature, content_source, emboss_text, motif, relief_image, hero_mesh). The generated
// schema.d.ts types request features as open objects and marks every defaulted field
// required on the response side, so the UI keeps this discriminated union with optional
// defaults instead. Keep it in step with the schema whenever a feature changes.
import type { DesignSpec, Family, TemplateAnchor, TemplateDescriptor } from "@/lib/api/types";

export type FeatureType = "emboss_text" | "motif" | "relief_image" | "hero_mesh";

export type ContentFormat = "png" | "jpg" | "webp" | "heic" | "stl" | "glb" | "3mf" | "obj" | "ply" | "off" | "gltf";
export type TextScript = "latin" | "devanagari" | "telugu" | "tamil" | "kannada" | "bengali" | "gujarati";

/** `$defs/content_source`. A stored spec always has `url`; the API fills it from `upload_id`, so requests may omit it. */
export type ContentSource = {
  upload_id: string;
  url?: string;
  format?: ContentFormat;
  origin?: "upload" | "generated";
  provider?: string;
};

/** `$defs/emboss_text` — Naam. */
export type EmbossText = {
  type: "emboss_text";
  /** 1–40 characters. */
  text: string;
  script?: TextScript;
  font?: string;
  /** 0.4–3 mm, default 1.2. */
  depth_mm?: number;
  /** 4–60 mm. */
  height_mm?: number;
  anchor: string;
  projection?: "planar" | "cylindrical" | "conformal";
  mode?: "emboss" | "deboss";
};

/** `$defs/motif` — Buti. */
export type Motif = {
  type: "motif";
  motif_id: string;
  anchor: string;
  /** 0.2–3, default 1. */
  scale?: number;
  /** 0.4–3 mm, default 1.0. */
  depth_mm?: number;
  mode?: "emboss" | "deboss";
};

/** `$defs/relief_image` — Chhavi. */
export type ReliefImage = {
  type: "relief_image";
  source: ContentSource;
  /** A surface anchor id from the template descriptor. */
  anchor: string;
  mode?: "emboss" | "deboss" | "lithophane";
  /** 0.2–3 mm, default 0.6. */
  relief_mm?: number;
  fit?: "contain" | "cover";
  invert?: boolean;
  cutout?: "none" | "silhouette";
};

/** `$defs/hero_mesh` — Roop. */
export type HeroMesh = {
  type: "hero_mesh";
  source: ContentSource;
  /** A volume anchor id from the template descriptor. */
  anchor: string;
  fit?: "contain" | "longest";
  /** 5–250 mm; the target longest side when `fit` is `longest`. */
  longest_mm?: number;
  /** 0–360. */
  yaw_deg?: number;
  orientation?: "as_uploaded" | "lay_flat";
};

export type Feature = EmbossText | Motif | ReliefImage | HeroMesh;

// Schema limits, so sliders and counters never offer a value the API would refuse.
export const TEXT_MAX_CHARS = 40;
export const RELIEF_MM = { min: 0.2, max: 3, default: 0.6 } as const;
export const TEXT_DEPTH_MM = { min: 0.4, max: 3, default: 1.2 } as const;
export const LONGEST_MM = { min: 5, max: 250 } as const;

/** Display order of the Chhaap tabs: text, photo, your form, then motifs (which arrive later). */
export const FEATURE_TYPES: readonly FeatureType[] = ["emboss_text", "relief_image", "hero_mesh", "motif"];

export type FeatureCodename = "Naam" | "Buti" | "Chhavi" | "Roop";

const FEATURE_COPY: Record<FeatureType, { codename: FeatureCodename; descriptor: string }> = {
  emboss_text: { codename: "Naam", descriptor: "Name or text" },
  motif: { codename: "Buti", descriptor: "Motif" },
  relief_image: { codename: "Chhavi", descriptor: "Photo relief" },
  hero_mesh: { codename: "Roop", descriptor: "Your own 3D form" },
};

/** Brand name of a feature type: Naam · Buti · Chhavi · Roop. */
export function featureLabel(type: FeatureType): FeatureCodename {
  return FEATURE_COPY[type].codename;
}

/** Plain descriptor that always accompanies the codename. */
export function featureDescriptor(type: FeatureType): string {
  return FEATURE_COPY[type].descriptor;
}

/** "Naam · Name or text": the codename paired with its descriptor, the way every label in the app reads. */
export function featureTitle(type: FeatureType): string {
  return `${featureLabel(type)} · ${featureDescriptor(type)}`;
}

/** "Saathi · Keychain & bag charm": an Avatar's codename with its plain name. */
export function familyLabel(family: Pick<Family, "codename" | "name">): string {
  return `${family.codename} · ${family.name}`;
}

const SCRIPT_RANGES: readonly [TextScript, RegExp][] = [
  ["devanagari", /[ऀ-ॿ]/],
  ["bengali", /[ঀ-৿]/],
  ["gujarati", /[઀-૿]/],
  ["tamil", /[஀-௿]/],
  ["telugu", /[ఀ-౿]/],
  ["kannada", /[ಀ-೿]/],
];

/**
 * The script of a Naam when it is trivially clear: exactly one Indic script, or Latin letters only.
 * Mixed or unrecognised text returns undefined and the API's detection decides.
 */
export function detectScript(text: string): TextScript | undefined {
  const found = SCRIPT_RANGES.filter(([, re]) => re.test(text)).map(([script]) => script);
  if (found.length === 1) return found[0];
  if (found.length === 0 && /[A-Za-z]/.test(text) && /^[ -~À-ɏ]*$/.test(text)) return "latin";
  return undefined;
}

/**
 * Feature types an anchor takes: its own `accepts`, else the template's `features_supported`
 * (the contract's default). Only a volume anchor can hold a hero form; a surface anchor holds the rest.
 */
export function anchorAccepts(anchor: TemplateAnchor, template: Pick<TemplateDescriptor, "features_supported">): FeatureType[] {
  const kind = anchor.kind ?? "surface";
  const list: FeatureType[] = anchor.accepts ?? template.features_supported ?? [];
  return FEATURE_TYPES.filter((t) => list.includes(t) && (kind === "volume" ? t === "hero_mesh" : t !== "hero_mesh"));
}

/** Anchors that can carry content, in descriptor order. */
export function contentAnchors(template: Pick<TemplateDescriptor, "anchors" | "features_supported">): TemplateAnchor[] {
  return template.anchors.filter((a) => anchorAccepts(a, template).length > 0);
}

/** True when the template has somewhere to put a Chhaap. */
export function hasContentSlot(template: Pick<TemplateDescriptor, "anchors" | "features_supported">): boolean {
  return contentAnchors(template).length > 0;
}

const round1 = (n: number) => Math.round(n * 10) / 10;

/** Deepest relief this anchor allows (its `max_relief_mm`, capped by the schema). */
export function maxReliefMm(anchor: Pick<TemplateAnchor, "max_relief_mm">): number {
  return round1(Math.max(RELIEF_MM.min, Math.min(RELIEF_MM.max, anchor.max_relief_mm ?? RELIEF_MM.max)));
}

/** 0.6 mm, or less when the anchor is shallower. */
export function defaultReliefMm(anchor: Pick<TemplateAnchor, "max_relief_mm">): number {
  return Math.min(RELIEF_MM.default, maxReliefMm(anchor));
}

export function clampReliefMm(value: number, anchor: Pick<TemplateAnchor, "max_relief_mm">): number {
  return round1(Math.min(maxReliefMm(anchor), Math.max(RELIEF_MM.min, value)));
}

/** Text depth: the schema default, kept inside the anchor's relief cap. */
export function defaultTextDepthMm(anchor: Pick<TemplateAnchor, "max_relief_mm">): number {
  const cap = anchor.max_relief_mm ?? TEXT_DEPTH_MM.default;
  return round1(Math.min(TEXT_DEPTH_MM.default, Math.max(TEXT_DEPTH_MM.min, cap)));
}

/** Characters a Naam may have on this family (its `content_slot.max_text_chars`, never above the schema's 40). */
export function maxTextChars(family: Pick<Family, "content_slot"> | undefined): number {
  return Math.min(TEXT_MAX_CHARS, family?.content_slot.max_text_chars ?? TEXT_MAX_CHARS);
}

export interface LongestRange {
  min: number;
  max: number;
  default: number;
}

/** Slider bounds for a hero form's longest side: the family's envelope, capped by the anchor's bounds and the schema. */
export function longestMmRange(family: Pick<Family, "size_envelope_mm"> | undefined, anchor?: Pick<TemplateAnchor, "bounds_mm">): LongestRange {
  const env = family?.size_envelope_mm;
  const anchorMax = anchor?.bounds_mm && anchor.bounds_mm.length >= 3 ? Math.max(...anchor.bounds_mm) : undefined;
  const min = Math.max(LONGEST_MM.min, Math.round(env?.min_longest_mm ?? LONGEST_MM.min));
  const max = Math.max(min, Math.round(Math.min(LONGEST_MM.max, env?.max_longest_mm ?? LONGEST_MM.max, anchorMax ?? LONGEST_MM.max)));
  return { min, max, default: Math.min(max, Math.max(min, 80)) };
}

const TYPE_SET: ReadonlySet<string> = new Set(FEATURE_TYPES);

export function isFeature(value: unknown): value is Feature {
  if (typeof value !== "object" || value === null) return false;
  const v = value as { type?: unknown; anchor?: unknown };
  return typeof v.type === "string" && TYPE_SET.has(v.type) && typeof v.anchor === "string";
}

/** The features stored on a version's spec (defaults filled in by the API) as the union the UI edits. */
export function featuresFromSpec(spec: Pick<DesignSpec, "features"> | undefined): Feature[] {
  return ((spec?.features ?? []) as unknown[]).filter(isFeature);
}

/** Replace whatever sits on `anchorId` with `feature` (or clear it); other anchors keep their content. */
export function withFeature(features: readonly Feature[], anchorId: string, feature: Feature | null): Feature[] {
  const rest = features.filter((f) => f.anchor !== anchorId);
  return feature ? [...rest, feature] : rest;
}

export function featureOn(features: readonly Feature[], anchorId: string): Feature | undefined {
  return features.find((f) => f.anchor === anchorId);
}

function stable(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stable);
  if (typeof value === "object" && value !== null) {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, v]) => v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => [k, stable(v)]),
    );
  }
  return value;
}

/** Order-insensitive equality, so a draft rebuilt from the same spec is never "dirty". */
export function sameFeatures(a: readonly Feature[], b: readonly Feature[]): boolean {
  if (a.length !== b.length) return false;
  const key = (f: Feature) => `${f.anchor}\u0000${f.type}`;
  const sort = (list: readonly Feature[]) => [...list].sort((x, y) => key(x).localeCompare(key(y)));
  return JSON.stringify(stable(sort(a))) === JSON.stringify(stable(sort(b)));
}

/** One line describing a feature for chips and the karigar-style summary. */
export function featureSummary(feature: Feature): string {
  switch (feature.type) {
    case "emboss_text":
      return `${featureLabel("emboss_text")} · “${feature.text}”`;
    case "relief_image":
      return `${featureLabel("relief_image")} · photo, ${feature.mode === "deboss" ? "cut in" : "raised"} ${(feature.relief_mm ?? RELIEF_MM.default).toFixed(1)} mm`;
    case "hero_mesh":
      return `${featureLabel("hero_mesh")} · your form${feature.longest_mm ? `, ${Math.round(feature.longest_mm)} mm` : ""}`;
    case "motif":
      return `${featureLabel("motif")} · ${feature.motif_id.replace(/_/g, " ")}`;
  }
}
