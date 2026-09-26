export interface StatProps {
  label: string;
  value?: string;
  hint?: string;
}

/** Height · Weight · Print time rows from the Create board. */
export function Stat({ label, value, hint }: StatProps) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
      <span>{label}</span>
      <span className="font-semibold text-surface-text" title={hint}>
        {value ?? "—"}
      </span>
    </div>
  );
}
