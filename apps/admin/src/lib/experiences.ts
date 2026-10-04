// Vocabulary and form helpers for Duniya (experiences) in the portal. Code ids stay snake_case English; codename,
// title and tagline are brand data edited here and always shown together ("Utsav · Festive & gifting").
import { color } from "@aakar/design-tokens";
import type { AdminExperience, AdminExperienceInput, ExperienceStyle, Motif, SeasonWindow } from "@/lib/api/types";
import { humanize } from "@/lib/format";

export const STYLES: readonly { id: ExperienceStyle; label: string; hint: string }[] = [
  { id: "none", label: "None", hint: "No style variant: templates render as designed" },
  { id: "jaipur_heritage", label: "Jaipur heritage", hint: "Utsav's festive look" },
  { id: "modern_zen", label: "Modern zen", hint: "Yaadein's quiet look" },
  { id: "cyber_desi", label: "Cyber desi", hint: "Adda's desk and gaming look" },
  { id: "warli_line", label: "Warli line", hint: "Masti's folk line look" },
  { id: "comic_pop", label: "Comic pop", hint: "Katha's comic-book look (toon shading lands with its backdrop)" },
];

export function styleLabel(id: string | undefined): string {
  return STYLES.find((s) => s.id === id)?.label ?? humanize(id ?? "none");
}

/** "Utsav · Festive & gifting": the codename always with its plain descriptor. */
export function experienceTitle(e: Pick<AdminExperienceInput, "codename" | "title">): string {
  return e.codename && e.title ? `${e.codename} · ${e.title}` : e.codename || e.title;
}

/** Experiences in display order (`sort_order`, then id), as the API lists them. */
export function sortExperiences<T extends Pick<AdminExperienceInput, "sort_order" | "id">>(list: readonly T[]): T[] {
  return [...list].sort((a, b) => a.sort_order - b.sort_order || a.id.localeCompare(b.id));
}

/** Row → input body: the read-only `updated_at` never travels back on PUT; optional lists become explicit. */
export function experienceInput(e: AdminExperience): AdminExperienceInput {
  return {
    id: e.id,
    codename: e.codename,
    slug: e.slug,
    title: e.title,
    ...(e.tagline !== undefined ? { tagline: e.tagline } : {}),
    ...(e.description !== undefined ? { description: e.description } : {}),
    environment: e.environment,
    surface: { accent: e.surface.accent, paper_tint: e.surface.paper_tint ?? null, hero_media: e.surface.hero_media ?? null },
    style: e.style ?? "none",
    motif_pack: [...(e.motif_pack ?? [])],
    avatars: [...e.avatars],
    items: [...(e.items ?? [])],
    collections: (e.collections ?? []).map((c) => ({ id: c.id, title: c.title, licence_ref: c.licence_ref ?? null })),
    season: (e.season ?? []).map((w) => ({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label })),
    available: e.available,
    sort_order: e.sort_order,
  };
}

// ---- Brand palette for the surface accent and paper tint ------------------------------------------------------------

export interface PaletteColour {
  token: string;
  label: string;
  hex: string;
}

const PALETTE_LABELS: Record<string, string> = {
  marigold: "Marigold",
  terracotta: "Terracotta",
  terracotta_deep: "Terracotta deep",
  indigo: "Indigo",
  indigo_deep: "Indigo deep",
  indigo_surface: "Indigo surface",
  sage: "Sage",
  success: "Leaf",
  warning: "Turmeric",
  danger: "Sindoor",
  ink: "Ink",
  ink_muted: "Ink muted",
  cream: "Cream",
  paper: "Paper",
  sand: "Sand",
  line: "Line",
};

const LIGHT = new Set(["cream", "paper", "sand", "line"]);

function paletteOf(filter: (token: string) => boolean): PaletteColour[] {
  return Object.entries(color as Record<string, string>)
    .filter(([token]) => filter(token))
    .map(([token, hex]) => ({ token, label: PALETTE_LABELS[token] ?? humanize(token), hex: hex.toUpperCase() }));
}

/** Accents: the brand colours that carry on cream paper (the light paper tones are left out). */
export const ACCENTS: readonly PaletteColour[] = paletteOf((t) => !LIGHT.has(t));
/** Paper tints: the light tones laid thinly over the cream; none keeps plain cream. */
export const TINTS: readonly PaletteColour[] = paletteOf((t) => LIGHT.has(t));

export function paletteColour(hex: string | null | undefined): PaletteColour | undefined {
  if (!hex) return undefined;
  const h = hex.toUpperCase();
  return [...ACCENTS, ...TINTS].find((c) => c.hex === h);
}

export const HEX = /^#[0-9A-Fa-f]{6}$/;

// ---- Motif packs --------------------------------------------------------------------------------------------------

export type PackEntryState =
  | { kind: "motif"; motif: Motif }
  | { kind: "pack"; motifs: Motif[] }
  | { kind: "pending" };

/**
 * What a motif-pack entry names today: a motif of the library, a pack id (every motif tagged with it), or nothing yet —
 * "pending", like Katha's `comic_bursts` before its artwork lands. Pending entries stay in the pack.
 */
export function packEntry(entry: string, motifs: readonly Motif[] | undefined): PackEntryState {
  const motif = motifs?.find((m) => m.id === entry);
  if (motif) return { kind: "motif", motif };
  const tagged = motifs?.filter((m) => m.tags.includes(entry)) ?? [];
  return tagged.length > 0 ? { kind: "pack", motifs: tagged } : { kind: "pending" };
}

// ---- Season windows -------------------------------------------------------------------------------------------------

export const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"] as const;
/** Days per month for a recurring window (29 February is allowed: it recurs in leap years). */
const DAYS_IN_MONTH = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const MONTH_DAY = /^--(\d{2})-(\d{2})$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isMonthDay(value: string): boolean {
  return MONTH_DAY.test(value);
}

/** "--10-01" → { month: 10, day: 1 }. */
export function parseMonthDay(value: string): { month: number; day: number } | undefined {
  const m = MONTH_DAY.exec(value);
  if (!m) return undefined;
  return { month: Number(m[1]), day: Number(m[2]) };
}

export function monthDay(month: number, day: number): string {
  return `--${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** "--10-01" → "1 Oct"; "2026-10-20" → "20 Oct 2026". */
export function formatSeasonDay(value: string): string {
  const md = parseMonthDay(value);
  if (md) return `${md.day} ${MONTHS[md.month - 1] ?? "?"}`;
  if (DATE.test(value)) {
    const [y, mo, d] = value.split("-").map(Number);
    return `${d} ${MONTHS[(mo ?? 1) - 1] ?? "?"} ${y}`;
  }
  return value;
}

/** "Diwali · 1 Oct – 30 Nov" (recurring) or "Launch · 20 Oct 2026 – 10 Nov 2026". */
export function formatWindow(w: SeasonWindow): string {
  return `${w.label} · ${formatSeasonDay(w.starts_on)} – ${formatSeasonDay(w.ends_on)}`;
}

/** What is wrong with a window, in staff words; undefined when the API would take it. */
export function windowProblem(w: SeasonWindow): string | undefined {
  if (!w.label.trim()) return "Give the window a label (the badge text)";
  if (w.label.length > 40) return "Labels are at most 40 characters";
  const a = parseMonthDay(w.starts_on);
  const b = parseMonthDay(w.ends_on);
  if (a || b) {
    if (!a || !b) return "Use two month-days or two dates";
    for (const { month, day } of [a, b]) {
      if (month < 1 || month > 12 || day < 1 || day > (DAYS_IN_MONTH[month - 1] ?? 31)) return "That day doesn't exist";
    }
    return undefined;
  }
  if (!DATE.test(w.starts_on) || !DATE.test(w.ends_on)) return "Pick both dates";
  const s = new Date(`${w.starts_on}T00:00:00Z`);
  const e = new Date(`${w.ends_on}T00:00:00Z`);
  if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime()) || s.toISOString().slice(0, 10) !== w.starts_on || e.toISOString().slice(0, 10) !== w.ends_on) {
    return "That day doesn't exist";
  }
  if (w.starts_on > w.ends_on) return "The window ends before it starts";
  return undefined;
}

/** Today as YYYY-MM-DD in the studio's time zone (IST). */
export function studioToday(now: Date = new Date()): string {
  return now.toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
}

/** True when `today` is inside the window: month-day windows recur and may wrap the new year; dates are absolute. */
export function inSeason(w: SeasonWindow, today: string = studioToday()): boolean {
  if (isMonthDay(w.starts_on) && isMonthDay(w.ends_on)) {
    const day = today.slice(5);
    const s = w.starts_on.slice(2);
    const e = w.ends_on.slice(2);
    return s <= e ? day >= s && day <= e : day >= s || day <= e;
  }
  if (DATE.test(w.starts_on) && DATE.test(w.ends_on)) return today >= w.starts_on && today <= w.ends_on;
  return false;
}
