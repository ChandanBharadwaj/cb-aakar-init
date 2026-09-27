"use client";

import type { Environment } from "@/lib/api/types";
import { environmentLabel, presetBuilt, sortEnvironments, swatches } from "@/lib/environments";

/** A backdrop's palette as small swatches, the backdrop colour first (aria-hidden: the label says what it is). */
export function PaletteSwatches({ env, size = 14, className }: { env: Pick<Environment, "palette"> | undefined; size?: number; className?: string }) {
  const colours = swatches(env);
  if (colours.length === 0) return null;
  return (
    <span aria-hidden="true" className={["inline-flex flex-none items-center", className].filter(Boolean).join(" ")}>
      {colours.map((c, i) => (
        <span
          key={`${c}-${i}`}
          title={c}
          className="inline-block rounded-full border border-black/10 shadow-sm"
          style={{ width: size, height: size, background: c, marginLeft: i === 0 ? 0 : -Math.round(size / 3) }}
        />
      ))}
    </span>
  );
}

export interface EnvironmentSelectProps {
  id: string;
  value: string;
  onChange(id: string): void;
  environments: Environment[];
  disabled?: boolean;
  /** Offer "none" (an empty value), for fields where the backdrop is optional. */
  allowNone?: boolean;
}

/**
 * A backdrop select fed by GET /admin/api/environments: the label, a "preset pending" note for backdrops the storefront
 * hasn't built yet, and the current value kept (marked unknown) when the API no longer lists it.
 */
export function EnvironmentSelect({ id, value, onChange, environments, disabled, allowNone }: EnvironmentSelectProps) {
  const list = sortEnvironments(environments);
  const known = !value || list.some((e) => e.id === value);
  return (
    <select id={id} className="ak-input ak-input-sm" value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled}>
      {list.length === 0 && <option value={value}>{value ? `${environmentLabel(undefined, value)} (loading backdrops…)` : "Loading backdrops…"}</option>}
      {allowNone && <option value="">No backdrop (studio)</option>}
      {!known && list.length > 0 && <option value={value}>{value} (unknown backdrop)</option>}
      {list.map((e) => (
        <option key={e.id} value={e.id}>
          {e.label}
          {presetBuilt(e.preset_key) ? "" : " · preset pending (shows as studio)"}
        </option>
      ))}
    </select>
  );
}

export interface EnvironmentPickerProps {
  value: string;
  onChange(id: string): void;
  environments: Environment[];
  disabled?: boolean;
  /** Accessible name of the radio group. */
  label: string;
}

/** Backdrops as radio cards with their palette swatches (the Duniya drawer's background picker). */
export function EnvironmentPicker({ value, onChange, environments, disabled, label }: EnvironmentPickerProps) {
  const list = sortEnvironments(environments);
  const known = list.some((e) => e.id === value);
  return (
    <div role="radiogroup" aria-label={label} className="grid gap-2 sm:grid-cols-2">
      {list.length === 0 && <span className="text-xs text-surface-muted">Backdrops are still loading.</span>}
      {!known && value && list.length > 0 && (
        <span className="text-xs text-danger sm:col-span-2">
          <span className="font-mono">{value}</span> is not a backdrop the API knows; pick one below.
        </span>
      )}
      {list.map((e) => {
        const checked = e.id === value;
        const built = presetBuilt(e.preset_key);
        return (
          <button
            key={e.id}
            type="button"
            role="radio"
            aria-checked={checked}
            disabled={disabled}
            onClick={() => onChange(e.id)}
            className={[
              "ak-well flex items-center gap-3 border p-2.5 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-60",
              checked ? "border-surface-accent ring-1 ring-surface-accent" : "border-surface-border hover:border-surface-accent",
            ].join(" ")}
          >
            <span
              aria-hidden="true"
              className="grid h-10 w-14 flex-none place-items-end overflow-hidden rounded-[8px] border border-black/10 p-1"
              style={{ background: `linear-gradient(180deg, ${e.palette?.[0] ?? "#1B2238"} 0%, #1B2238 100%)` }}
            >
              <PaletteSwatches env={e} size={9} />
            </span>
            <span className="grid min-w-0 gap-0.5">
              <span className="truncate text-sm font-medium">{e.label}</span>
              <span className="truncate font-mono text-[11px] text-surface-muted">
                {e.id}
                {built ? "" : " · preset pending"}
              </span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
