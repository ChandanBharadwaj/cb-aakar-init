import { stageCopy, type StageId } from "@aakar/design-tokens";
import type { Stage } from "@/lib/api/types";

export interface MandalaSpinnerProps {
  stage?: Stage | StageId;
  /** Server message for the stage; shown under the stage title when it adds something. */
  message?: string;
  percent?: number;
  size?: number;
  className?: string;
}

const PETAL = "M0 -34 L7 -20 L0 -14 L-7 -20 Z";
const OUTER = "M0 -46 L5 -38 L0 -33 L-5 -38 Z";

export function stageTitle(stage: Stage | StageId | undefined): string {
  if (!stage || stage === "queued") return "Taking shape";
  return stageCopy[stage as StageId] ?? "Taking shape";
}

/**
 * Generation spinner: a mandala of rotated jaali petals turning slowly (animate-mandala,
 * 9 s) with the customer-facing stage copy from the tokens underneath.
 */
export function MandalaSpinner({ stage, message, percent, size = 128, className }: MandalaSpinnerProps) {
  const title = stageTitle(stage);
  const rings = { inner: 8, outer: 16 };
  return (
    <div role="status" aria-live="polite" className={["grid justify-items-center gap-4 text-center", className].filter(Boolean).join(" ")}>
      <svg width={size} height={size} viewBox="-50 -50 100 100" aria-hidden="true" style={{ display: "block", overflow: "visible" }}>
        <g className="animate-mandala" style={{ transformOrigin: "0 0" }}>
          <g fill="none" stroke="var(--ak-accent)" strokeWidth={2.2} strokeLinejoin="round" opacity={0.9}>
            {Array.from({ length: rings.inner }, (_, i) => (
              <path key={`i${i}`} d={PETAL} transform={`rotate(${(360 / rings.inner) * i})`} />
            ))}
          </g>
          <g fill="var(--ak-accent)" opacity={0.55}>
            {Array.from({ length: rings.outer }, (_, i) => (
              <path key={`o${i}`} d={OUTER} transform={`rotate(${(360 / rings.outer) * i + 360 / rings.outer / 2})`} />
            ))}
          </g>
          <circle r="2.4" fill="currentColor" />
        </g>
        {/* counter-rotating hairline ring so the motion reads as a lattice, not a wheel */}
        <g className="animate-mandala" style={{ transformOrigin: "0 0", animationDirection: "reverse", animationDuration: "14s" }}>
          <circle r="40" fill="none" stroke="currentColor" strokeWidth="0.6" strokeDasharray="2 4" opacity="0.5" />
        </g>
      </svg>
      <div className="grid gap-1">
        <div className="font-display text-xl font-semibold italic">{title}…</div>
        {message && message.toLowerCase() !== title.toLowerCase() && <div className="text-sm text-surface-muted">{message}</div>}
        {typeof percent === "number" && (
          <div className="mx-auto mt-1 h-1 w-40 overflow-hidden rounded-pill bg-surface-border" aria-hidden="true">
            <div className="h-full rounded-pill bg-surface-accent transition-[width] duration-slow ease-ak" style={{ width: `${Math.max(4, Math.min(100, percent))}%` }} />
          </div>
        )}
      </div>
    </div>
  );
}
