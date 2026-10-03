// Content features (the Chhaap) as the storefront sends and reads them.
//
// Hand-written mirror of packages/contracts/schemas/design-spec.v1.json `$defs`
// (feature, content_source, emboss_text, motif, relief_image, hero_mesh). The generated
// schema.d.ts types request features as open objects and marks every defaulted field
// required on the response side, so the UI keeps this discriminated union with optional
// defaults instead. Keep it in step with the schema whenever a feature changes.
import type { DesignSpec, Family, Motif, TemplateAnchor, TemplateDescriptor } from "@/lib/api/types";
import { capitalise, formatMm, joinList } from "@/lib/format";

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

/** `$defs/motif` — Buti. (The library entry it names is `Motif` in `@/lib/api/types`.) */
export type MotifFeature = {
  type: "motif";
  /** An id from the Buti library (`GET /api/motifs`). */
  motif_id: string;
  anchor: string;
  /** 0.2–1, default 1: 1 fills the spot; never below the motif's own `min_scale`. */
  scale?: number;
  /** 0.4–3 mm, default 1.0, never deeper than the anchor's `max_relief_mm`. */
  depth_mm?: number;
  /** Default `deboss` (cut in); thin pieces default to `emboss` (raised). */
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

export type Feature = EmbossText | MotifFeature | ReliefImage | HeroMesh;

export type Orientation = NonNullable<HeroMesh["orientation"]>;
export type ReliefMode = "emboss" | "deboss";
/** How content sits in the piece: raised, cut in, or (a photo only) the glowing plate itself. */
export type ContentMode = ReliefMode | "lithophane";

// Schema limits, so sliders and counters never offer a value the API would refuse.
export const TEXT_MAX_CHARS = 40;
/**
 * Katha's comic look (`comic_pop`): names and motifs stand raised and bolder by default, this deep or the anchor's
 * `max_relief_mm` if that is less. Mirrors the geometry service (`features/styles.py`), which applies the same default
 * to a name or motif that leaves its mode or depth out.
 */
export const COMIC_POP = "comic_pop";
export const COMIC_POP_DEPTH_MM = 1.5;
export const RELIEF_MM = { min: 0.2, max: 3, default: 0.6 } as const;
export const TEXT_DEPTH_MM = { min: 0.4, max: 3, default: 1.2 } as const;
export const MOTIF_DEPTH_MM = { min: 0.4, max: 3, default: 1 } as const;
/** `motif.scale`: 1 fills the spot; each motif's own `min_scale` raises the floor. */
export const MOTIF_SCALE = { min: 0.2, max: 1, default: 1 } as const;
export const LONGEST_MM = { min: 5, max: 250 } as const;

/**
 * Display order of the Chhaap tabs, as the naming convention lists them: Naam and Buti (which may share a spot),
 * then Chhavi and Roop (which fill a spot on their own).
 */
export const FEATURE_TYPES: readonly FeatureType[] = ["emboss_text", "motif", "relief_image", "hero_mesh"];

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

// ---- Modes: raised, cut in, or the plate itself -------------------------------------------------------------------
//
// A descriptor's anchor may list the only `modes` its content takes (template-descriptor.v1.json): Chaukhat's rails
// cut in only (the frame prints face down), Roshni's plate takes its photo as the plate itself (lithophane). The panel
// offers just those, hides the raised / cut-in toggle when one is left, and always sends the mode explicitly (a mode
// left out is the type's default, which the API refuses when the anchor does not list it).

/** The modes each type can take (design-spec.v1.json), in the order the toggle offers them. */
const TYPE_MODES: Record<Exclude<FeatureType, "hero_mesh">, readonly ContentMode[]> = {
  emboss_text: ["emboss", "deboss"],
  motif: ["emboss", "deboss"],
  relief_image: ["emboss", "deboss", "lithophane"],
};

/** The toggle's words for each mode. */
export const MODE_OPTIONS: Record<ContentMode, { value: ContentMode; label: string; description: string }> = {
  emboss: { value: "emboss", label: "Raised", description: "Stands out from the surface" },
  deboss: { value: "deboss", label: "Cut in", description: "Sunk into the surface" },
  lithophane: { value: "lithophane", label: "Lit from behind", description: "The photo becomes the plate that glows" },
};

/**
 * The modes this anchor offers for a type: the anchor's `modes` narrowed to the type's. An anchor that lists none
 * offers raised and cut in; a photo becomes the plate (lithophane) only where an anchor lists it, since only a night
 * light's template can build one. A hero form has no mode.
 */
export function anchorModes(anchor: Pick<TemplateAnchor, "modes">, type: FeatureType): ContentMode[] {
  if (type === "hero_mesh") return [];
  const allowed = TYPE_MODES[type];
  const listed: readonly string[] | undefined = anchor.modes;
  return listed ? allowed.filter((m) => listed.includes(m)) : allowed.filter((m) => m !== "lithophane");
}

/** The type's default when a feature leaves its mode out (design-spec.v1.json). */
function typeDefaultMode(type: Exclude<FeatureType, "hero_mesh">): ContentMode {
  return type === "motif" ? "deboss" : "emboss";
}

/**
 * Feature types an anchor takes: its own `accepts` (else the template's `features_supported`, the contract's
 * default), never more than the template supports, and only in a mode the anchor lists; a descriptor without
 * `features_supported` supports nothing. Only a volume anchor holds a hero form; a surface anchor holds the rest.
 */
export function anchorAccepts(anchor: TemplateAnchor, template: Pick<TemplateDescriptor, "features_supported">): FeatureType[] {
  const supported: readonly string[] = template.features_supported ?? [];
  const list: readonly string[] = anchor.accepts ?? supported;
  const volume = (anchor.kind ?? "surface") === "volume";
  return FEATURE_TYPES.filter(
    (t) =>
      list.includes(t) &&
      supported.includes(t) &&
      (volume ? t === "hero_mesh" : t !== "hero_mesh") &&
      (t === "hero_mesh" || anchorModes(anchor, t).length > 0),
  );
}

// ---- Required content ----------------------------------------------------------------------------------------------

/** True when the piece can't be made without content on this anchor (the night light's photo). */
export function isRequired(anchor: Pick<TemplateAnchor, "required">): boolean {
  return anchor.required === true;
}

/** The anchors a customer must fill before Sculpt, in descriptor order. */
export function requiredAnchors(template: ContentTemplate): TemplateAnchor[] {
  return contentAnchors(template).filter(isRequired);
}

/** Required anchors with nothing on them yet: Sculpt waits for these. */
export function missingContent(template: ContentTemplate, features: readonly Feature[]): TemplateAnchor[] {
  return requiredAnchors(template).filter((a) => !features.some((f) => f.anchor === a.id));
}

const NEED_WORDS: Record<FeatureType, string> = {
  relief_image: "a photo",
  hero_mesh: "your model file",
  emboss_text: "a name",
  motif: "a motif",
};

/** "a photo", "a name or a motif": what an anchor needs, in plain words. */
export function contentWords(anchor: TemplateAnchor, template: Pick<TemplateDescriptor, "features_supported">): string {
  return joinList(anchorAccepts(anchor, template).map((t) => NEED_WORDS[t]), "or") || "something";
}

/** "Add a photo to the photo plate to sculpt.": why Sculpt waits, for the first empty required anchor. */
export function missingContentLine(missing: readonly TemplateAnchor[], template: Pick<TemplateDescriptor, "features_supported">): string | undefined {
  const first = missing[0];
  return first ? `Add ${contentWords(first, template)} to the ${first.label.toLowerCase()} to sculpt.` : undefined;
}

/** Anchors a customer can fill (at least one feature type the anchor and the template both take), in descriptor order. */
export function contentAnchors(template: ContentTemplate): TemplateAnchor[] {
  return (template.anchors ?? []).filter((a) => anchorAccepts(a, template).length > 0);
}

/** True when the template has somewhere to put a Chhaap. */
export function hasContentSlot(template: ContentTemplate): boolean {
  return contentAnchors(template).length > 0;
}

/** What the composer can put on this template, across its anchors, in tab order. */
export function templateTakes(template: ContentTemplate): FeatureType[] {
  const found = new Set(contentAnchors(template).flatMap((a) => anchorAccepts(a, template)));
  return FEATURE_TYPES.filter((t) => found.has(t));
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
  ["motif", "a motif"],
];

/** ["a photo", "your name", "a motif"]: what a customer can add, in plain words, for "Make it yours · add a photo, your name or a motif". */
export function addWords(types: readonly FeatureType[]): string[] {
  return ADD_WORDS.filter(([t]) => types.includes(t)).map(([, words]) => words);
}

// ---- What one spot may hold -------------------------------------------------------------------------------------
//
// The geometry service's rule (services/geometry features/validate.py): per anchor at most one photo or form, at most
// one name and at most one motif. A name and a motif may share a spot (the motif first, the name beside it); a photo
// fills its spot on its own, so a photo with a name or a motif on the same anchor is refused as crowded. The panel
// never lets a customer build a crowded spot: the tabs that would crowd it are disabled with a hint.

/** Feature types that hold a spot on their own: a photo relief (surface) or a customer's form (volume). */
export const SOLO_TYPES: readonly FeatureType[] = ["relief_image", "hero_mesh"];

export function isSolo(type: FeatureType): boolean {
  return SOLO_TYPES.includes(type);
}

/** Two different types may sit on one spot only when neither holds it alone: a name and a motif. */
export function canShareSpot(a: FeatureType, b: FeatureType): boolean {
  return a !== b && !isSolo(a) && !isSolo(b);
}

/** Everything on one spot, in list order. */
export function featuresOn(features: readonly Feature[], anchorId: string): Feature[] {
  return features.filter((f) => f.anchor === anchorId);
}

/** The feature of one type on a spot, if any. */
export function featureOfType<T extends FeatureType>(features: readonly Feature[], anchorId: string, type: T): Extract<Feature, { type: T }> | undefined {
  return features.find((f): f is Extract<Feature, { type: T }> => f.anchor === anchorId && f.type === type);
}

/** What on this spot would crowd a new feature of `type`: a photo blocks a name and a motif; a name or a motif blocks a photo. */
export function crowdedBy(onSpot: readonly Feature[], type: FeatureType): Feature[] {
  return onSpot.filter((f) => f.type !== type && !canShareSpot(f.type, type));
}

/** Put `feature` on its spot: it replaces the same type there and anything it may not share the spot with. */
export function placeFeature(features: readonly Feature[], feature: Feature): Feature[] {
  const rest = features.filter((f) => f.anchor !== feature.anchor || (f.type !== feature.type && canShareSpot(f.type, feature.type)));
  return [...rest, feature];
}

/** Clear one type from a spot, or the whole spot ("Leave plain") when `type` is omitted. */
export function clearFeature(features: readonly Feature[], anchorId: string, type?: FeatureType): Feature[] {
  return features.filter((f) => f.anchor !== anchorId || (type !== undefined && f.type !== type));
}

/**
 * Pieces whose skin is too thin for a cut-in motif by default: a 1 mm cut on a 5 mm fridge magnet eats into the
 * 1.2 mm skin over its magnet pocket. Buti defaults to raised on these (the customer can still choose cut in).
 */
export const THIN_FAMILIES: ReadonlySet<string> = new Set(["fridge_magnet"]);

export function isThinPiece(template: Pick<TemplateDescriptor, "id" | "family"> | undefined, family?: Pick<Family, "id">): boolean {
  return Boolean((template && (THIN_FAMILIES.has(template.family) || THIN_FAMILIES.has(template.id))) || (family && THIN_FAMILIES.has(family.id)));
}

/** Buti's default relief: cut in, except raised on thin pieces. */
export function defaultMotifMode(template: Pick<TemplateDescriptor, "id" | "family"> | undefined, family?: Pick<Family, "id">): ReliefMode {
  return isThinPiece(template, family) ? "emboss" : "deboss";
}

/**
 * The mode and depth a new name or motif starts with on this anchor: raised for a name, cut in for a motif (raised on
 * a thin piece), raised and bolder under the comic look (`COMIC_POP_DEPTH_MM`, capped by the anchor), always a mode
 * the anchor lists (Chaukhat's rails: cut in).
 */
export function markDefaults(
  anchor: Pick<TemplateAnchor, "modes" | "max_relief_mm">,
  type: "emboss_text" | "motif",
  options: { thin?: boolean; look?: string } = {},
): { mode: ReliefMode; depth_mm: number } {
  const modes = anchorModes(anchor, type).filter((m): m is ReliefMode => m !== "lithophane");
  const comic = options.look === COMIC_POP;
  const preferred: ReliefMode = comic || type === "emboss_text" || options.thin ? "emboss" : "deboss";
  const mode = modes.includes(preferred) ? preferred : (modes[0] ?? preferred);
  const range = type === "emboss_text" ? textDepthRange(anchor) : motifDepthRange(anchor);
  const depth = comic && mode === "emboss" ? clampDepth(COMIC_POP_DEPTH_MM, range) : range.default;
  return { mode, depth_mm: depth };
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

/** Motif depth on this anchor: 0.4–3 mm, default 1.0, under the same `max_relief_mm` cap. */
export function motifDepthRange(anchor: Pick<TemplateAnchor, "max_relief_mm">): DepthRange {
  return depthRange(MOTIF_DEPTH_MM, anchor);
}

/** Scale slider bounds for a motif: its `min_scale` (never under the schema's 0.2) up to 1, default 1. */
export function motifScaleRange(motif: Pick<Motif, "min_scale"> | undefined): DepthRange {
  const min = Math.min(MOTIF_SCALE.max, Math.max(MOTIF_SCALE.min, motif?.min_scale ?? MOTIF_SCALE.min));
  return { min, max: MOTIF_SCALE.max, default: MOTIF_SCALE.default };
}

/** A scale kept inside the motif's range (hundredths). */
export function clampScale(value: number, range: DepthRange): number {
  return Math.min(range.max, Math.max(range.min, Math.round(value * 100) / 100));
}

/** A slider value kept inside the range (hundredths, so a cap such as 1.25 mm is never rounded past). */
export function clampDepth(value: number, range: DepthRange): number {
  return Math.min(range.max, Math.max(range.min, Math.round(value * 100) / 100));
}

/** Characters a Naam may have on this family (its `content_slot.max_text_chars`, never above the schema's 40). */
export function maxTextChars(family: Pick<Family, "content_slot"> | undefined): number {
  return Math.min(TEXT_MAX_CHARS, family?.content_slot.max_text_chars ?? TEXT_MAX_CHARS);
}

const MARK_OR_JOINER = /[\p{M}\p{Cf}]/u;

/**
 * Characters as a reader counts them, like the geometry service and the API: marks (Indic vowel signs and viramas,
 * accents) and joiners do not add, so "नमस्ते" is 4 and "श्री" is 2.
 */
export function textLength(text: string): number {
  let n = 0;
  for (const ch of text) if (!MARK_OR_JOINER.test(ch)) n += 1;
  return n;
}

/** The text cut to at most `max` characters (`textLength`), never inside a letter and its vowel signs. */
export function clampText(text: string, max: number): string {
  if (textLength(text) <= max) return text;
  const clusters =
    typeof Intl !== "undefined" && "Segmenter" in Intl
      ? Array.from(new Intl.Segmenter(undefined, { granularity: "grapheme" }).segment(text), (s) => s.segment)
      : Array.from(text);
  let out = "";
  for (const cluster of clusters) {
    if (textLength(out + cluster) > max) break;
    out += cluster;
  }
  return out;
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

/** An edit to the Chhaap as a whole list, applied functionally so an upload finishing late never undoes other edits. */
export type FeatureEdit = (features: Feature[]) => Feature[];

/**
 * A feature brought onto an anchor's rules: a mode the anchor lists (its first when the feature's is not; a night-light
 * photo drops its relief depth) and a depth within its `max_relief_mm`.
 */
export function fitToAnchor(feature: Feature, anchor: Pick<TemplateAnchor, "modes" | "max_relief_mm">): Feature {
  if (feature.type === "hero_mesh") return feature;
  const modes = anchorModes(anchor, feature.type);
  const current = feature.mode ?? typeDefaultMode(feature.type);
  const mode = modes.includes(current) ? current : (modes[0] ?? current);
  if (feature.type === "relief_image") {
    if (mode === "lithophane") {
      const plate: ReliefImage = { ...feature, mode };
      delete plate.relief_mm;
      return plate;
    }
    const range = reliefRange(anchor);
    return { ...feature, mode, relief_mm: clampDepth(feature.relief_mm ?? range.default, range) };
  }
  const relief: ReliefMode = mode === "deboss" ? "deboss" : "emboss";
  const range = feature.type === "emboss_text" ? textDepthRange(anchor) : motifDepthRange(anchor);
  return { ...feature, mode: relief, depth_mm: clampDepth(feature.depth_mm ?? range.default, range) };
}

/**
 * Features that still fit a template after a switch: the spot exists and still takes that type, brought onto the new
 * spot's modes and depth cap (`fitToAnchor`).
 */
export function featuresFitting(features: readonly Feature[], template: ContentTemplate): Feature[] {
  return features.flatMap((f) => {
    const anchor = (template.anchors ?? []).find((a) => a.id === f.anchor);
    return anchor !== undefined && anchorAccepts(anchor, template).includes(f.type) ? [fitToAnchor(f, anchor)] : [];
  });
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

/** A motif's plain name from the library ("Rangoli star"), else its humanised id. */
export function motifName(motifId: string, motifs?: readonly Pick<Motif, "id" | "label">[]): string {
  return motifs?.find((m) => m.id === motifId)?.label ?? capitalise(motifId.replace(/_/g, " "));
}

/**
 * One line describing a feature for summaries: "Text (Naam) · “Asha”", "Photo relief (Chhavi) · raised 0.6 mm",
 * "Motif (Buti) · Lotus, cut in". Pass the Buti library to name motifs as it does.
 */
export function featureSummary(feature: Feature, motifs?: readonly Pick<Motif, "id" | "label">[]): string {
  const head = capitalise(featurePhrase(feature.type));
  switch (feature.type) {
    case "emboss_text":
      return `${head} · “${feature.text.trim()}”`;
    case "relief_image": {
      if (feature.mode === "lithophane") return `${head} · the glowing plate`;
      const how = feature.mode === "deboss" ? "cut in" : "raised";
      return `${head} · ${how} ${formatMm(feature.relief_mm ?? RELIEF_MM.default, 1)}`;
    }
    case "hero_mesh":
      return `${head}${feature.longest_mm ? ` · ${Math.round(feature.longest_mm)} mm` : ""}`;
    case "motif": {
      const size = feature.scale !== undefined && feature.scale < 1 ? ` at ${Math.round(feature.scale * 100)}%` : "";
      return `${head} · ${motifName(feature.motif_id, motifs)}, ${(feature.mode ?? "deboss") === "emboss" ? "raised" : "cut in"}${size}`;
    }
  }
}
