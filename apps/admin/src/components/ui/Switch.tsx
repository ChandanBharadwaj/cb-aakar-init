"use client";

export interface SwitchProps {
  checked: boolean;
  onChange(next: boolean): void;
  disabled?: boolean;
  label: string;
  id?: string;
  busy?: boolean;
}

/** Accessible switch (role="switch"); the visual comes from `.ak-switch` in globals.css. */
export function Switch({ checked, onChange, disabled, label, id, busy }: SwitchProps) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      aria-busy={busy}
      disabled={disabled || busy}
      className={["ak-switch", busy ? "animate-pulse-soft" : ""].join(" ")}
      onClick={() => onChange(!checked)}
    />
  );
}
