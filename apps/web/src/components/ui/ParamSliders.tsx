"use client";

import { useId } from "react";
import type { ParamValue, ParamValues, TemplateParam } from "@/lib/api/types";
import { formatParam } from "@/lib/format";

export interface ParamSlidersProps {
  params: Record<string, TemplateParam>;
  values: ParamValues;
  onChange(key: string, value: ParamValue): void;
  disabled?: boolean;
  className?: string;
}

function groupOf(p: TemplateParam): string {
  return p.group ?? "Shape";
}

/**
 * Sliders for template params (min/max/step from the descriptor), toggles for booleans,
 * selects for enums. Native inputs keep everything keyboard-operable.
 */
export function ParamSliders({ params, values, onChange, disabled, className }: ParamSlidersProps) {
  const entries = Object.entries(params);
  const groups = Array.from(new Set(entries.map(([, p]) => groupOf(p))));
  if (entries.length === 0) return null;
  return (
    <div className={["grid gap-4", className].filter(Boolean).join(" ")}>
      {groups.map((group) => (
        <fieldset key={group} className="grid gap-3" disabled={disabled}>
          <legend className="ak-label mb-1">{group}</legend>
          {entries
            .filter(([, p]) => groupOf(p) === group)
            .map(([key, p]) => (
              <ParamControl key={key} id={key} param={p} value={values[key] ?? (p.default as ParamValue)} onChange={(v) => onChange(key, v)} disabled={disabled} />
            ))}
        </fieldset>
      ))}
    </div>
  );
}

interface ParamControlProps {
  id: string;
  param: TemplateParam;
  value: ParamValue;
  onChange(value: ParamValue): void;
  disabled?: boolean;
}

function ParamControl({ id, param, value, onChange, disabled }: ParamControlProps) {
  const inputId = useId();
  const label = (
    <label htmlFor={inputId} className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
      <span title={param.description}>{param.label}</span>
      <output htmlFor={inputId} className="font-semibold text-surface-text">
        {formatParam(value, param.unit)}
      </output>
    </label>
  );

  if (param.type === "boolean") {
    return (
      <div className="flex items-center justify-between gap-3 text-xs">
        <label htmlFor={inputId} className="text-surface-muted" title={param.description}>
          {param.label}
        </label>
        <input id={inputId} type="checkbox" checked={Boolean(value)} disabled={disabled} onChange={(e) => onChange(e.target.checked)} className="h-4 w-4 accent-[var(--ak-accent)]" />
      </div>
    );
  }

  if (param.type === "enum") {
    return (
      <div className="grid gap-1">
        {label}
        <select id={inputId} value={String(value)} disabled={disabled} onChange={(e) => onChange(e.target.value)} className="ak-input min-h-10 py-1.5 text-sm">
          {(param.options ?? []).map((o) => (
            <option key={o} value={o}>
              {o.replace(/_/g, " ")}
            </option>
          ))}
        </select>
      </div>
    );
  }

  const min = param.min ?? 0;
  const max = param.max ?? Math.max(min + 1, Number(value) * 2 || 100);
  const step = param.step ?? (param.type === "integer" ? 1 : 0.1);
  const num = typeof value === "number" ? value : Number(value) || min;
  const fill = max > min ? ((num - min) / (max - min)) * 100 : 0;
  return (
    <div className="grid gap-0.5" data-param={id}>
      {label}
      <input
        id={inputId}
        type="range"
        min={min}
        max={max}
        step={step}
        value={num}
        disabled={disabled}
        aria-valuetext={formatParam(num, param.unit)}
        onChange={(e) => onChange(param.type === "integer" ? Math.round(Number(e.target.value)) : Number(e.target.value))}
        className="ak-range"
        style={{ ["--ak-range-fill" as string]: `${fill}%` }}
      />
      <div className="flex justify-between text-[10px] text-surface-muted" aria-hidden="true">
        <span>{formatParam(min, param.unit)}</span>
        <span>{formatParam(max, param.unit)}</span>
      </div>
    </div>
  );
}
