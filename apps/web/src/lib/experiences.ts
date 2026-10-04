// Duniya (experiences) on the storefront: the Shop's second persona, the experience pages and the presets a
// `?duniya=<slug>` link carries into the Create composer and the studio. An experience is a curation layer over
// existing Avatars, backdrops, styles and motifs; codenames, titles and accents are data from the API.
import type { CSSProperties } from "react";
import type { Environment, Experience, ExperienceStyle, SeasonWindow, TemplateDescriptor } from "@/lib/api/types";
import { environmentLabel, hasViewerPreset, presetKeyFor } from "@/lib/viewer/environments";

/** "Utsav · Festive & gifting": the codename never travels without its plain descriptor. */
export function experienceLabel(experience: Pick<Experience, "codename" | "title">): string {
  return `${experience.codename} · ${experience.title}`;
}

export function experienceHref(experience: Pick<Experience, "slug">): string {
  return `/duniya/${encodeURIComponent(experience.slug)}`;
}

// ---- The Shop's personas ------------------------------------------------------------------------------------------

export type ShopView = "shelves" | "duniya";

/** `?view=duniya` opens the Duniya persona; anything else is the shelves. */
export function shopView(value: string | undefined): ShopView {
  return value === "duniya" ? "duniya" : "shelves";
}

export function shopHref(view: ShopView): string {
  return view === "duniya" ? "/shop?view=duniya" : "/shop";
}

// ---- Seasons --------------------------------------------------------------------------------------------------------

/** The studio's time zone: season windows are read against the date in India. */
export const STUDIO_TIME_ZONE = "Asia/Kolkata";

/** Today as YYYY-MM-DD in the studio's time zone. */
export function studioToday(now: Date = new Date()): string {
  return now.toLocaleDateString("en-CA", { timeZone: STUDIO_TIME_ZONE });
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const MONTH_DAY = /^--\d{2}-\d{2}$/;

/**
 * True when `today` (YYYY-MM-DD) is inside the window, both ends included. Month-day windows (`--10-01`) recur every
 * year and may wrap the new year (`--12-20` to `--01-05`); date windows (`2026-10-20`) are absolute. A window mixing the
 * two is not a window (the API refuses it) and never matches.
 */
export function inSeason(window: Pick<SeasonWindow, "starts_on" | "ends_on">, today: string): boolean {
  const { starts_on: start, ends_on: end } = window;
  if (MONTH_DAY.test(start) && MONTH_DAY.test(end)) {
    const day = today.slice(5);
    const s = start.slice(2);
    const e = end.slice(2);
    return s <= e ? day >= s && day <= e : day >= s || day <= e;
  }
  if (DATE.test(start) && DATE.test(end)) return today >= start && today <= end;
  return false;
}

/** The first window the experience is in today, if any. */
export function activeSeason(season: readonly SeasonWindow[] | undefined, today: string = studioToday()): SeasonWindow | undefined {
  return (season ?? []).find((w) => inSeason(w, today));
}

// ---- Page theming ---------------------------------------------------------------------------------------------------

/**
 * CSS variables for a Duniya surface (the experience page, its Shop card). `--ak-duniya` is the raw accent for fills,
 * glows and rules; `--ak-accent`, which links, eyebrows, chips and focus rings read, is the accent deepened with ink so
 * text stays legible on cream (marigold alone is under 2:1). The cream paper stays; `paper_tint` lays a light wash.
 */
export function duniyaSurfaceStyle(surface: Experience["surface"]): CSSProperties {
  const style: Record<string, string> = {
    "--ak-duniya": surface.accent,
    "--ak-accent": `color-mix(in srgb, ${surface.accent} 45%, var(--ak-ink))`,
  };
  if (surface.paper_tint) style.backgroundImage = `linear-gradient(color-mix(in srgb, ${surface.paper_tint} 14%, transparent), color-mix(in srgb, ${surface.paper_tint} 14%, transparent))`;
  return style as CSSProperties;
}

// ---- Styles ---------------------------------------------------------------------------------------------------------

export const STYLE_LABELS: Record<ExperienceStyle, string> = {
  none: "Plain",
  jaipur_heritage: "Jaipur heritage",
  modern_zen: "Modern zen",
  cyber_desi: "Cyber desi",
  warli_line: "Warli line",
  comic_pop: "Comic pop",
};

/**
 * The experience's default style when the template offers it as a variant (`style_variants`); otherwise nothing, and
 * the style is ignored silently. Today the carriers list `comic_pop` (Katha); the night light and the frame list none.
 */
export function presetStyle(style: ExperienceStyle | undefined, template: Pick<TemplateDescriptor, "style_variants"> | undefined): ExperienceStyle | undefined {
  if (!style || style === "none" || !template) return undefined;
  return (template.style_variants ?? []).includes(style) ? style : undefined;
}

// ---- What `?duniya=<slug>` carries into Create and the studio -------------------------------------------------------

export interface DuniyaPreset {
  /** What `POST /api/designs` sends as `experience_id`. */
  id: string;
  slug: string;
  codename: string;
  title: string;
  accent: string;
  /** The backdrop's viewer preset key, only when the viewer has that preset (else the piece keeps its own backdrop). */
  environment?: string;
  /** The backdrop's plain name, e.g. "Chettinad teak · candlelight". */
  environmentLabel: string;
  style: ExperienceStyle;
  /** Motif ids (or pack ids) the Buti picker offers first. */
  motifPack: string[];
}

/** An available experience as presets; an unavailable one (Katha until staff switch it on) presets nothing. */
export function duniyaPreset(experience: Experience, environments?: readonly Environment[]): DuniyaPreset | undefined {
  if (!experience.available) return undefined;
  const key = presetKeyFor(experience.environment, environments);
  const listed = environments?.find((e) => e.id === experience.environment);
  return {
    id: experience.id,
    slug: experience.slug,
    codename: experience.codename,
    title: experience.title,
    accent: experience.surface.accent,
    environment: hasViewerPreset(key) ? key : undefined,
    environmentLabel: listed?.label ?? environmentLabel(key),
    style: experience.style,
    motifPack: [...(experience.motif_pack ?? [])],
  };
}

/** `/create/keychain?duniya=utsav` (plus `item` / `prompt` when given): an Avatar opened from a Duniya page. */
export function createHref(familyId: string, params: { duniya?: string; item?: string; prompt?: string } = {}): string {
  const query = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) query.set(k, v);
  const qs = query.toString();
  return `/create/${encodeURIComponent(familyId)}${qs ? `?${qs}` : ""}`;
}
