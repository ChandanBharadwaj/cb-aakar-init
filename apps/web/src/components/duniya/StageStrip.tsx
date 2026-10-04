"use client";

import dynamic from "next/dynamic";
import type { MaterialPbr } from "@/lib/api/types";
import { environmentBackdrop } from "@/lib/viewer/environments";

// three.js needs a window; keep the Canvas out of the server render.
const DesignViewer = dynamic(() => import("@/components/viewer/DesignViewer").then((m) => m.DesignViewer), { ssr: false });

export interface StageStripProps {
  /** The viewer preset that renders the backdrop (already resolved: an unbuilt preset arrives here as the studio). */
  environment: string;
  /** The backdrop's plain name, e.g. "Chettinad teak · candlelight". */
  label: string;
  /** Set when the experience's own backdrop is not built yet and the studio stands in for it. */
  standIn?: string;
  /** The finish of the stand-in form on the stage. */
  pbr: MaterialPbr;
  /** The experience's style: `comic_pop` (Katha) draws the stand-in cel-shaded with ink outlines. */
  look?: string;
  className?: string;
}

/**
 * A Duniya page's stage strip: the experience's backdrop rendered by the studio viewer's own presets
 * (`src/lib/viewer/environments.ts`), a stand-in form on it in the experience's look, still (no controls, frames on
 * demand) so the page scrolls freely. Hero renders of the Avatars replace the stand-in once they are shot.
 */
export function StageStrip({ environment, label, standIn, pbr, look, className }: StageStripProps) {
  return (
    <section
      data-surface="stage"
      aria-label={`The stage: ${label}`}
      className={["relative h-[220px] overflow-hidden rounded-card border border-surface-border shadow-card sm:h-[280px]", className].filter(Boolean).join(" ")}
      style={{ background: environmentBackdrop(environment) }}
    >
      <div className="pointer-events-none absolute inset-0" aria-hidden="true">
        <DesignViewer pbr={pbr} environment={environment} look={look} still />
      </div>
      <div className="pointer-events-none absolute inset-x-0 bottom-0 flex flex-wrap items-end justify-between gap-2 p-3">
        <span className="ak-well rounded-pill px-3 py-1.5 text-[11px] font-semibold text-surface-text">{label}</span>
        {standIn && <span className="ak-well rounded-pill px-3 py-1.5 text-[11px] text-surface-muted">{standIn}</span>}
      </div>
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-[var(--ak-duniya,var(--ak-marigold))]" />
    </section>
  );
}
