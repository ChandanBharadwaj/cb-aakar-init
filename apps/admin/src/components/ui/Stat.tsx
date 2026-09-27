export interface StatProps {
  label: string;
  value?: React.ReactNode;
  hint?: string;
  className?: string;
}

/** Label / value row (from the storefront's Create board). */
export function Stat({ label, value, hint, className }: StatProps) {
  return (
    <div className={["flex items-baseline justify-between gap-3 text-xs text-surface-muted", className].filter(Boolean).join(" ")}>
      <span>{label}</span>
      <span className="text-right font-semibold text-surface-text" title={hint}>
        {value ?? "—"}
      </span>
    </div>
  );
}
