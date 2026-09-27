import { useId } from "react";

export interface FieldProps {
  label: string;
  hint?: string;
  error?: string;
  /** Render prop receives the id to bind to the control. */
  children: (id: string) => React.ReactNode;
  className?: string;
  /** Inline label + control on one row (checkboxes). */
  inline?: boolean;
}

/** Label above a control, optional hint and error. Keeps forms consistent across drawers and panels. */
export function Field({ label, hint, error, children, className, inline }: FieldProps) {
  const id = useId();
  if (inline) {
    return (
      <div className={["flex items-center justify-between gap-3", className].filter(Boolean).join(" ")}>
        <label htmlFor={id} className="grid gap-0.5 text-sm">
          <span className="font-medium">{label}</span>
          {hint && <span className="text-[11px] text-surface-muted">{hint}</span>}
        </label>
        {children(id)}
      </div>
    );
  }
  return (
    <div className={["grid gap-1.5", className].filter(Boolean).join(" ")}>
      <label htmlFor={id} className="ak-label">
        {label}
      </label>
      {children(id)}
      {error ? <span className="text-xs text-danger">{error}</span> : hint ? <span className="text-[11px] text-surface-muted">{hint}</span> : null}
    </div>
  );
}
