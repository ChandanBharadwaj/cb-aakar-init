"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminOrder, AdvanceRequest, OrderStatus, Problem } from "@/lib/api/types";
import { PRODUCTION_STATUSES, STATUS_LABEL, actionLabel, isDisruptive } from "@/lib/orders";
import { Field } from "@/components/ui/Field";
import { ProblemCard } from "@/components/ui/ProblemCard";

export interface AdvancePanelProps {
  order: AdminOrder;
  onAdvanced(order: AdminOrder): void;
}

interface DetailDraft {
  studio: string;
  printer_bay: string;
  layer_height_mm: string;
  layers_total: string;
  layer: string;
}

function lastDetail(order: AdminOrder): Partial<Record<keyof DetailDraft, unknown>> {
  for (let i = order.events.length - 1; i >= 0; i -= 1) {
    const d = order.events[i]?.detail;
    if (d && Object.keys(d).length) return d as Partial<Record<keyof DetailDraft, unknown>>;
  }
  return {};
}

const str = (v: unknown) => (v === undefined || v === null ? "" : String(v));

/** One button per allowed next status; the chosen one opens a small form (message, production details) and posts /advance. */
export function AdvancePanel({ order, onAdvanced }: AdvancePanelProps) {
  const [target, setTarget] = useState<OrderStatus>();
  const [message, setMessage] = useState("");
  const [detail, setDetail] = useState<DetailDraft>(() => {
    const d = lastDetail(order);
    return { studio: str(d.studio) || "Aakar Studio · Hyderabad", printer_bay: str(d.printer_bay), layer_height_mm: str(d.layer_height_mm), layers_total: str(d.layers_total), layer: str(d.layer) };
  });
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();

  const actions = order.next_actions;
  const production = target ? PRODUCTION_STATUSES.has(target) : false;

  function pick(to: OrderStatus) {
    setProblem(undefined);
    setTarget((t) => (t === to ? undefined : to));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!target) return;
    setBusy(true);
    setProblem(undefined);
    const body: AdvanceRequest = { status: target };
    if (message.trim()) body.message = message.trim();
    if (production) {
      const d: Record<string, unknown> = {};
      if (detail.studio.trim()) d.studio = detail.studio.trim();
      if (detail.printer_bay.trim()) d.printer_bay = detail.printer_bay.trim();
      if (detail.layer_height_mm !== "") d.layer_height_mm = Number(detail.layer_height_mm);
      if (detail.layers_total !== "") d.layers_total = Number(detail.layers_total);
      if (detail.layer !== "") d.layer = Number(detail.layer);
      if (Object.keys(d).length) body.detail = d;
    }
    try {
      const updated = await api.orders.advance(order.id, body);
      setTarget(undefined);
      setMessage("");
      onAdvanced(updated);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="ak-card grid gap-4 p-5" aria-labelledby="advance-heading">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="advance-heading" className="font-display text-2xl font-semibold">
          Advance
        </h2>
        <span className="text-xs text-surface-muted">Now: {STATUS_LABEL[order.status]}</span>
      </div>
      {actions.length === 0 ? (
        <p className="text-sm text-surface-muted">This order is closed; nothing more to do.</p>
      ) : (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Next status">
          {actions.map((to) => (
            <button key={to} type="button" className={`ak-btn ${isDisruptive(to) ? "ak-btn-secondary" : "ak-btn-primary"} ${target === to ? "ak-ring-accent" : ""}`} aria-pressed={target === to} onClick={() => pick(to)} disabled={busy}>
              {actionLabel(to, order.status)}
            </button>
          ))}
        </div>
      )}

      {target && (
        <form onSubmit={submit} className="ak-well grid gap-4 p-4 animate-fade-in" aria-busy={busy}>
          <div className="grid gap-1">
            <span className="ak-label">Move to</span>
            <span className="font-display text-xl font-semibold">{STATUS_LABEL[target]}</span>
            {isDisruptive(target) && <span className="text-xs text-warning">{target === "cancelled" ? "Cancelling can't be undone. Refunds are handled from the payment, not here." : target === "on_hold" ? "The customer sees the order paused. Resume returns it to the current stage." : "Marks the print failed; the order goes back to printing."}</span>}
          </div>
          <Field label="Message to the customer" hint="Optional, up to 200 characters; shown on the tracking page. Left empty, the API uses its standard line.">
            {(id) => <textarea id={id} className="ak-input" maxLength={200} rows={2} value={message} onChange={(e) => setMessage(e.target.value)} disabled={busy} placeholder="e.g. Printing on Bay 2 in Terracotta Silk." />}
          </Field>
          {production && (
            <fieldset className="grid gap-3 sm:grid-cols-2">
              <legend className="ak-label mb-2">Production detail (read by the tracking board)</legend>
              <Field label="Studio">{(id) => <input id={id} className="ak-input ak-input-sm" value={detail.studio} onChange={(e) => setDetail({ ...detail, studio: e.target.value })} disabled={busy} />}</Field>
              <Field label="Printer bay">{(id) => <input id={id} className="ak-input ak-input-sm" value={detail.printer_bay} onChange={(e) => setDetail({ ...detail, printer_bay: e.target.value })} disabled={busy} placeholder="Bay 2" />}</Field>
              <Field label="Layer height (mm)">{(id) => <input id={id} type="number" step="0.01" min="0.05" max="1" className="ak-input ak-input-sm" value={detail.layer_height_mm} onChange={(e) => setDetail({ ...detail, layer_height_mm: e.target.value })} disabled={busy} placeholder="0.2" />}</Field>
              <Field label="Layers total">{(id) => <input id={id} type="number" step="1" min="1" className="ak-input ak-input-sm" value={detail.layers_total} onChange={(e) => setDetail({ ...detail, layers_total: e.target.value })} disabled={busy} placeholder="480" />}</Field>
              <Field label="Current layer">{(id) => <input id={id} type="number" step="1" min="0" className="ak-input ak-input-sm" value={detail.layer} onChange={(e) => setDetail({ ...detail, layer: e.target.value })} disabled={busy} placeholder="212" />}</Field>
            </fieldset>
          )}
          {problem && <ProblemCard compact problem={problem} title={problem.code === "invalid_transition" ? "That move isn't allowed any more" : undefined} />}
          <div className="flex flex-wrap gap-2">
            <button type="submit" className={`ak-btn ${isDisruptive(target) ? "ak-btn-danger" : "ak-btn-primary"}`} disabled={busy}>
              {busy ? "Saving…" : `Confirm · ${STATUS_LABEL[target]}`}
            </button>
            <button type="button" className="ak-btn ak-btn-secondary" onClick={() => setTarget(undefined)} disabled={busy}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
