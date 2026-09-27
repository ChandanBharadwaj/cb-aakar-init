// Content features (the Chhaap) as the storefront sends and reads them.
//
// Hand-written mirror of packages/contracts/schemas/design-spec.v1.json `$defs`
// (feature, content_source, emboss_text, motif, relief_image, hero_mesh). The generated
// schema.d.ts types request features as open objects and marks every defaulted field
// required on the response side, so the UI keeps this discriminated union with optional
// defaults instead. Keep it in step with the schema whenever a feature changes.
import type { DesignSpec, Family, TemplateAnchor, TemplateDescriptor } from "@/lib/api/types";
import { capitalise, formatMm } from "@/lib/format";

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

/**
 * `$defs/hero_mesh` — Roop. On Swaroop (`raw_print@1`, no template params) this one feature carries the
 * piece's size (`fit: "longest"` + `longest_mm`) and `orientation`.
 */
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

export type Orientation = NonNullable<HeroMesh["orientation"]>;

// Schema limits, so sliders and counters never offer a value the API would refuse.
export const TEXT_MAX_CHARS = 40;
export const RELIEF_MM = { min: 0.2, max: 3, default: 0.6 } as const;
export const TEXT_DEPTH_MM = { min: 0.4, max: 3, default: 1.2 } as const;
export const LONGEST_MM = { min: 5, max: 250 } as const;

/** Display order of the Chhaap tabs: text, photo, your form, then motifs (which arrive later). */
export const FEATURE_TYPES: readonly FeatureType[] = ["emboss_text", "relief_image", "hero_mesh", "motif"];

/**
 * Buti (motifs) needs the motif library, which isn't live yet. Until it is, a motif is never offered as something
 * to add: the Avatar picker and the Shop's "Make it yours" line leave it out, and the Chhaap tabs mark it "soon".
 */
export const MOTIFS_LIVE = false;

/** True for the feature types a customer can add today. */
export function isLiveFeature(type: FeatureType): boolean {
  return type !== "motif" || MOTIFS_LIVE;
}

export type FeatureCodename = "Naam" | "Buti" | "Chhavi" | "Roop";

const FEATURE_COPY: Record<FeatureType, { codename: FeatureCodename; descriptor: string; noun: string }> = {
  emboss_text: { codename: "Naam", descriptor: "Name or text", noun: "text" },
  motif: { codename: "Buti", descriptor: "Motif", noun: "motif" },
  relief_image: { codename: "Chhavi", descriptor: "Photo relief", noun: "photo relief" },
  hero_mesh: { codename: "Roop", descriptor: "Your own 3D form", noun: "your own 3D form" },
};

/** The bare codename (Naam · Buti · Chhavi · Roop), only for places that show its descriptor right beside it, like the Chhaap tabs. */
export function featureCodename(type: FeatureType): FeatureCodename {
  return FEATURE_COPY[type].codename;
}

/** Plain descriptor that always accompanies the codename. */
export function featureDescriptor(type: FeatureType): string {
  return FEATURE_COPY[type].descriptor;
}

/** "Naam · Name or text": the codename paired with its descriptor, for labels and titles. */
export function featureLabel(type: FeatureType): string {
  return `${featureCodename(type)} · ${featureDescriptor(type)}`;
}

/** "text (Naam)", "your own 3D form (Roop)": plain words first and the codename after, for running copy. */
export function featurePhrase(type: FeatureType): string {
  return `${FEATURE_COPY[type].noun} (${featureCodename(type)})`;
}

/** The Chhaap itself, the brand word for everything a customer sets into a piece, with its plain words. */
export const CHHAAP = { label: "Chhaap · Your imprint", phrase: "your imprint (Chhaap)" } as const;

/** "Saathi · Keychain & bag charm": an Avatar's codename with its plain name. */
export function familyLabel(family: Pick<Family, "codename" | "name">): string {
  return `${family.codename} · ${family.name}`;
}

/** "As uploaded · Lay flat": how a customer's own form sits on the bed (Swaroop). */
export const ORIENTATIONS: readonly { value: Orientation; label: string; description: string }[] = [
  { value: "as_uploaded", label: "As uploaded", description: "Keep the file's own orientation" },
  { value: "lay_flat", label: "Lay flat", description: "Turn it to rest on its flattest face" },
];

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

type ContentTemplate = Pick<TemplateDescriptor, "anchors" | "features_supported">;

/**
 * Feature types an anchor takes: its own `accepts` (else the template's `features_supported`, the contract's
 * default), never more than the template supports; a descriptor without `features_supported` supports nothing.
 * Only a volume anchor holds a hero form; a surface anchor holds the rest.
 */
export function anchorAccepts(anchor: TemplateAnchor, template: Pick<TemplateDescriptor, "features_supported">): FeatureType[] {
  const supported: readonly string[] = template.features_supported ?? [];
  const list: readonly string[] = anchor.accepts ?? supported;
  const volume = (anchor.kind ?? "surface") === "volume";
  return FEATURE_TYPES.filter((t) => list.includes(t) && supported.includes(t) && (volume ? t === "hero_mesh" : t !== "hero_mesh"));
}

/** Anchors a customer can fill today (at least one live feature type), in descriptor order. */
export function contentAnchors(template: ContentTemplate): TemplateAnchor[] {
  return (template.anchors ?? []).filter((a) => anchorAccepts(a, template).some(isLiveFeature));
}

/** True when the template has somewhere to put a Chhaap. */
export function hasContentSlot(template: ContentTemplate): boolean {
  return contentAnchors(template).length > 0;
}

/** What the composer can put on this template today, across its anchors, in tab order (Buti waits for the motif library). */
export function templateTakes(template: ContentTemplate): FeatureType[] {
  const found = new Set(contentAnchors(template).flatMap((a) => anchorAccepts(a, template)));
  return FEATURE_TYPES.filter((t) => found.has(t) && isLiveFeature(t));
}

/** What a family's live templates take today: the picker's "Takes …" line (never the family's aspirational `content_slot.accepts`). */
export function familyTakes(family: Pick<Family, "templates">): FeatureType[] {
  const found = new Set(family.templates.flatMap((t) => templateTakes(t)));
  return FEATURE_TYPES.filter((t) => found.has(t));
}

const ADD_WORDS: readonly (readonly [FeatureType, string])[] = [
  ["relief_image", "a photo"],
  ["hero_mesh", "your own 3D form"],
  ["emboss_text", "your name"],
];

/** ["a photo", "your name"]: what a customer can add, in plain words, for "Make it yours · add a photo or your name". */
export function addWords(types: readonly FeatureType[]): string[] {
  return ADD_WORDS.filter(([t]) => types.includes(t)).map(([, words]) => words);
}

export interface DepthRange {
  min: number;
  max: number;
  default: number;
}

function depthRange(schema: DepthRange, anchor: Pick<TemplateAnchor, "max_relief_mm">): DepthRange {
  const cap = typeof anchor.max_relief_mm === "number" && anchor.max_relief_mm > 0 ? anchor.max_relief_mm : schema.max;
  const max = Math.min(schema.max, cap);
  // An anchor shallower than the schema's floor pulls the floor down to its cap, so the slider never offers more than the anchor takes.
  const min = Math.min(schema.min, max);
  return { min, max, default: Math.min(max, Math.max(min, schema.default)) };
}

/** Photo relief depth on this anchor: 0.2–3 mm, default 0.6, never deeper than its `max_relief_mm`. */
export function reliefRange(anchor: Pick<TemplateAnchor, "max_relief_mm">): DepthRange {
  return depthRange(RELIEF_MM, anchor);
}

/** Letter depth on this anchor: 0.4–3 mm, default 1.2, under the same `max_relief_mm` cap. */
export function textDepthRange(anchor: Pick<TemplateAnchor, "max_relief_mm">): DepthRange {
  return depthRange(TEXT_DEPTH_MM, anchor);
}

/** A slider value kept inside the range (hundredths, so a cap such as 1.25 mm is never rounded past). */
export function clampDepth(value: number, range: DepthRange): number {
  return Math.min(range.max, Math.max(range.min, Math.round(value * 100) / 100));
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

/** The upload behind a photo relief or a hero form. */
export function uploadIdOf(feature: Feature): string | undefined {
  return feature.type === "relief_image" || feature.type === "hero_mesh" ? feature.source.upload_id : undefined;
}

/**
 * The Chhaap as it is sent: each Naam is trimmed here, once, instead of on every keystroke, and a Naam that is
 * only spaces leaves its spot plain.
 */
export function featuresForSubmit(features: readonly Feature[]): Feature[] {
  return features.flatMap((f): Feature[] => {
    if (f.type !== "emboss_text") return [f];
    const text = f.text.trim();
    return text ? [{ ...f, text }] : [];
  });
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

/** One line describing a feature for summaries: "Text (Naam) · “Asha”", "Photo relief (Chhavi) · raised 0.6 mm". */
export function featureSummary(feature: Feature): string {
  const head = capitalise(featurePhrase(feature.type));
  switch (feature.type) {
    case "emboss_text":
      return `${head} · “${feature.text.trim()}”`;
    case "relief_image": {
      const how = feature.mode === "deboss" ? "cut in" : feature.mode === "lithophane" ? "lit from behind" : "raised";
      return `${head} · ${how} ${formatMm(feature.relief_mm ?? RELIEF_MM.default, 1)}`;
    }
    case "hero_mesh":
      return `${head}${feature.longest_mm ? ` · ${Math.round(feature.longest_mm)} mm` : ""}`;
    case "motif":
      return `${head} · ${feature.motif_id.replace(/_/g, " ")}`;
  }
}
