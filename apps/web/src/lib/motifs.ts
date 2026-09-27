// The Buti motif library (GET /api/motifs) as the storefront shows it: artwork URLs a browser can load, and a
// Duniya experience's motif pack offered first. Pure helpers, usable from server and client components.
import { browserApiUrl } from "@/lib/api/client";
import type { Motif } from "@/lib/api/types";

/** The motif's artwork (`svg_url`) as a browser loads it; a site path is joined to the public API base. */
export function motifArtUrl(motif: Pick<Motif, "svg_url">): string {
  return browserApiUrl(motif.svg_url);
}

/** Motifs a pack entry names: the motif with that id, else every motif tagged with it (a pack id such as `comic_bursts`). */
export function motifsForPackEntry(motifs: readonly Motif[], entry: string): Motif[] {
  const byId = motifs.find((m) => m.id === entry);
  return byId ? [byId] : motifs.filter((m) => m.tags.includes(entry));
}

export interface OrderedMotifs {
  /** The pack's motifs, in pack order, each once. */
  picks: Motif[];
  /** Everything else, in library order. */
  rest: Motif[];
}

/** Split the library into an experience's pack (offered first) and the rest. */
export function orderByPack(motifs: readonly Motif[], pack: readonly string[] | undefined): OrderedMotifs {
  const seen = new Set<string>();
  const picks: Motif[] = [];
  for (const entry of pack ?? []) {
    for (const m of motifsForPackEntry(motifs, entry)) {
      if (!seen.has(m.id)) {
        seen.add(m.id);
        picks.push(m);
      }
    }
  }
  return { picks, rest: motifs.filter((m) => !seen.has(m.id)) };
}

/** "100%", "35%": a motif scale as the share of the spot it fills. */
export function formatScale(scale: number): string {
  return `${Math.round(scale * 100)}%`;
}
