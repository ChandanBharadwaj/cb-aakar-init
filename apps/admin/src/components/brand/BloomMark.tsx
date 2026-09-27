import type { SVGProps } from "react";

/** Exact Bloom geometry from design/source/Aakar Logo.dc.html: three outlined jaali petals, one lifted solid petal. */
export const BLOOM_PETALS = [
  "M80 52 L60 62 L52 52 L60 42 Z",
  "M52 80 L42 60 L52 52 L62 60 Z",
  "M24 52 L44 42 L52 52 L44 62 Z",
] as const;
export const BLOOM_TOP = "M52 8 L62 28 L52 36 L42 28 Z";
export const BLOOM_TOP_SHADE = "M52 8 L62 28 L52 36 Z";
/** The three outlined petals as one closed loop, used by the loader's draw-on stroke. */
export const BLOOM_RING = "M52 52 L60 42 L80 52 L60 62 L52 52 L62 60 L52 80 L42 60 L52 52 L44 62 L24 52 L44 42 Z";

export interface BloomMarkProps extends Omit<SVGProps<SVGSVGElement>, "width" | "height"> {
  size?: number;
  /** Stroke colour of the outlined petals. Defaults to the current text colour so it adapts to the surface. */
  ink?: string;
  /** Fill of the lifted petal. Defaults to the surface accent (terracotta on paper, marigold on stage). */
  accent?: string;
  title?: string;
}

/** Stroke weight steps up as the mark gets smaller so it stays legible at 16 px (logo board, 1e). */
function strokeFor(size: number): number {
  if (size <= 20) return 10;
  if (size <= 28) return 8;
  if (size <= 48) return 6;
  if (size <= 96) return 5;
  return 4.5;
}

export function BloomMark({ size = 28, ink = "currentColor", accent = "var(--ak-accent)", title = "Aakar", className, ...rest }: BloomMarkProps) {
  const sw = strokeFor(size);
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      role="img"
      aria-label={title}
      className={className}
      style={{ display: "block", flex: "none" }}
      {...rest}
    >
      <g fill="none" stroke={ink} strokeWidth={sw} strokeLinejoin="round">
        {BLOOM_PETALS.map((d) => (
          <path key={d} d={d} />
        ))}
      </g>
      {size > 28 && <circle cx="52" cy="52" r="2.2" fill={ink} />}
      <path d={BLOOM_TOP} fill={accent} />
      <path d={BLOOM_TOP_SHADE} fill="var(--ak-accent-deep, #8F5238)" opacity=".45" />
    </svg>
  );
}
