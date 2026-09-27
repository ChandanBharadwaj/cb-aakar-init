import Link from "next/link";

export interface TileProps {
  label: string;
  value: React.ReactNode;
  hint?: string;
  href?: string;
  /** Small dot next to the label: neutral, accent, success, warning, danger. */
  tone?: "neutral" | "accent" | "success" | "warning" | "danger" | "info";
  className?: string;
}

const DOT: Record<NonNullable<TileProps["tone"]>, string> = {
  neutral: "bg-surface-border",
  accent: "bg-surface-accent",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-indigo",
};

/** A stability-card-style tile: small label with a status dot, a large value, an optional hint. Links when `href` is set. */
export function Tile({ label, value, hint, href, tone = "neutral", className }: TileProps) {
  const inner = (
    <>
      <div className="flex items-center gap-1.5 text-xs text-surface-muted">
        <span className={`h-1.5 w-1.5 rounded-full ${DOT[tone]}`} aria-hidden="true" />
        {label}
      </div>
      <div className="font-display text-[30px] font-semibold leading-none">{value}</div>
      {hint && <div className="text-[11px] text-surface-muted">{hint}</div>}
    </>
  );
  const cls = ["ak-card grid gap-2 p-4", href ? "transition-all duration-base ease-ak hover:-translate-y-0.5 hover:shadow-float" : "", className].filter(Boolean).join(" ");
  return href ? (
    <Link href={href} className={cls}>
      {inner}
    </Link>
  ) : (
    <div className={cls}>{inner}</div>
  );
}
