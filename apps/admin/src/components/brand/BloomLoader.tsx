import { BLOOM_RING, BLOOM_TOP, BLOOM_TOP_SHADE } from "./BloomMark";

export interface BloomLoaderProps {
  size?: number;
  /** Italic display caption under the mark, e.g. "Taking shape". Pass null to hide. */
  label?: string | null;
  className?: string;
}

/**
 * Route and page loader: the Bloom draws itself on with a stroke-dash animation
 * (ring first, then the lifted petal), the solid petal settles in, and it repeats.
 * `prefers-reduced-motion` shows the finished mark (see globals.css).
 */
export function BloomLoader({ size = 96, label = "Taking shape", className }: BloomLoaderProps) {
  return (
    <div role="status" aria-live="polite" className={["grid justify-items-center gap-5", className].filter(Boolean).join(" ")}>
      <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" style={{ display: "block", overflow: "visible" }}>
        <path
          d={BLOOM_RING}
          fill="none"
          stroke="currentColor"
          strokeWidth={4.5}
          strokeLinejoin="round"
          strokeLinecap="round"
          pathLength={1}
          className="ak-draw-loop"
        />
        <path
          d={BLOOM_TOP}
          fill="none"
          stroke="currentColor"
          strokeWidth={4.5}
          strokeLinejoin="round"
          strokeLinecap="round"
          pathLength={1}
          className="ak-draw-loop"
          style={{ animationDelay: "0.35s" }}
        />
        <g className="ak-appear">
          <circle cx="52" cy="52" r="2.2" fill="currentColor" />
          <path d={BLOOM_TOP} fill="var(--ak-accent)" />
          <path d={BLOOM_TOP_SHADE} fill="var(--ak-accent-deep, #8F5238)" opacity=".45" />
        </g>
      </svg>
      {label && <div className="font-display text-lg font-semibold italic text-surface-muted">{label}</div>}
      <span className="sr-only">Loading</span>
    </div>
  );
}
