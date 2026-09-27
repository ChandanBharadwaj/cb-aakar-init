"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { Order, OrderEvent, Problem } from "@/lib/api/types";
import { formatPhone } from "@/lib/identity";
import { PAYMENT_LABEL, STAGE_BLURB, STAGE_LABEL, STATUS_LABEL, formatArrives, formatPlaced, isFinalStage, latestEvent, liveDetail, type LiveDetail } from "@/lib/orders";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { BloomMark } from "@/components/brand/BloomMark";
import { MandalaSpinner } from "@/components/brand/MandalaSpinner";
import { finishName } from "@/components/cart/CartItemRow";
import { FinishTile } from "@/components/cart/FinishTile";
import { addressLines } from "@/components/checkout/AddressPicker";
import { paymentMethodLabel } from "@/components/checkout/PaymentMethodChips";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { OrderStageTimeline } from "./OrderStageTimeline";
import { StagePill } from "./StagePill";
import { useOrderStream } from "./useOrderStream";

export interface OrderTrackingProps {
  orderId: string;
  /** `?placed=1`: just paid, show the confirmation banner. */
  placed?: boolean;
}

function upsert(events: OrderEvent[], ev: OrderEvent): OrderEvent[] {
  if (events.some((e) => e.sequence === ev.sequence && e.stage === ev.stage)) return events;
  return [...events, ev].sort((a, b) => a.sequence - b.sequence);
}

/** /orders/[id]: the tracking board (06) with live stages over SSE. */
export function OrderTracking({ orderId, placed }: OrderTrackingProps) {
  const [order, setOrder] = useState<Order>();
  const [problem, setProblem] = useState<Problem>();
  const [detail, setDetail] = useState<LiveDetail>();
  const [retrying, setRetrying] = useState(false);
  const [retryProblem, setRetryProblem] = useState<Problem>();

  const load = useCallback(async () => {
    try {
      const o = await api.orders.get(orderId);
      setOrder(o);
      setDetail((d) => liveDetail(latestEvent(o.events)) ?? d);
    } catch (err) {
      setProblem(toProblem(err));
    }
  }, [orderId]);

  useEffect(() => {
    void load();
  }, [load]);

  const streaming = order && !isFinalStage(order.stage) ? orderId : undefined;
  const { live } = useOrderStream(streaming, {
    onEvent: (ev) => {
      const d = liveDetail(ev);
      if (d) setDetail((prev) => ({ ...prev, ...d }));
      setOrder((o) => {
        if (!o) return o;
        const changed = o.stage !== ev.stage || o.status !== ev.status;
        // A stage change may bring an eta, a shipment or an invoice; fetch the whole order once.
        if (changed) void load();
        return { ...o, status: ev.status, stage: ev.stage, events: upsert(o.events, ev) };
      });
    },
    onOrder: (o) => setOrder(o),
  });

  const last = useMemo(() => latestEvent(order?.events), [order]);

  async function retryPayment() {
    if (!order) return;
    setRetrying(true);
    setRetryProblem(undefined);
    try {
      const fresh = await api.orders.newPayment(order.id);
      window.location.assign(fresh.pay_url ?? `/checkout/pay/${fresh.id}`);
    } catch (err) {
      setRetryProblem(toProblem(err));
      setRetrying(false);
    }
  }

  if (problem) {
    return <ProblemCard problem={problem} title={problem.code === "not_found" ? "No such order" : undefined} action={{ href: "/orders", label: "Your orders" }} />;
  }
  if (!order) return <BloomLoader size={112} label="Finding your order" className="py-16" />;

  const arrives = formatArrives(order.eta);
  const title = order.title ?? order.items[0]?.title ?? "Your piece";
  const awaiting = order.stage === "payment";
  const payable = awaiting && (order.payment.status === "created" || order.payment.status === "pending");

  return (
    <div className="grid gap-5">
      {placed && (
        <div className="ak-card relative flex items-center gap-4 overflow-hidden border-sage/60 bg-sage/10 p-5 animate-fade-in" role="status">
          <BloomMark size={40} className="flex-none" />
          <div className="grid gap-0.5">
            <p className="font-display text-2xl font-semibold leading-tight">Designed by you. Crafted by Aakar.</p>
            <p className="text-sm text-surface-muted">Your order is confirmed. Follow every stage here{order.notify_whatsapp ? ", and on WhatsApp" : ""}.</p>
          </div>
        </div>
      )}

      <header className="ak-card grid gap-5 p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="grid gap-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs text-surface-muted">Order {order.number}</span>
              <StagePill stage={order.stage} live={live} />
            </div>
            <h1 className="font-display text-[34px] font-semibold leading-tight sm:text-[40px]">{title}</h1>
            <p className="text-xs text-surface-muted">Placed {formatPlaced(order.placed_at)}</p>
          </div>
          <div className="grid gap-0.5 text-right">
            <span className="ak-label">{order.stage === "delivered" ? "Delivered" : order.stage === "cancelled" ? "Cancelled" : "Arrives"}</span>
            <span className="font-display text-3xl font-semibold leading-none">
              {order.stage === "delivered" || order.stage === "cancelled" ? "—" : (arrives ?? (awaiting ? "After payment" : "Soon"))}
            </span>
          </div>
        </div>
        <OrderStageTimeline stage={order.stage} live={live} className="pt-6" />
      </header>

      {awaiting && (
        <section className="ak-card grid gap-3 border-marigold/60 bg-marigold/10 p-5" aria-labelledby="pay-h">
          <h2 id="pay-h" className="font-display text-2xl font-semibold leading-tight">
            Waiting for payment
          </h2>
          <p className="text-sm text-surface-muted">
            {order.payment.status === "failed" ? "The last attempt failed. Your pieces are still reserved." : "Your pieces are reserved. Finish paying and the studio picks them up."}
          </p>
          <div className="flex flex-wrap gap-2">
            {payable && order.payment.pay_url ? (
              <a href={order.payment.pay_url} className="ak-btn ak-btn-primary ak-btn-pill">
                Complete payment · {formatPaise(order.payment.amount_paise)}
              </a>
            ) : (
              <button type="button" className="ak-btn ak-btn-primary ak-btn-pill" onClick={() => void retryPayment()} disabled={retrying} aria-busy={retrying}>
                {retrying ? "Starting a new attempt…" : `Pay ${formatPaise(order.total_paise)}`}
              </button>
            )}
            <Link href="/cart" className="ak-btn ak-btn-secondary ak-btn-pill">
              Back to cart
            </Link>
          </div>
          {retryProblem && (
            <p role="alert" className="text-xs text-danger">
              {retryProblem.detail ?? retryProblem.title}
            </p>
          )}
        </section>
      )}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <div className="grid gap-5">
          {!awaiting && (
            <section className="ak-card grid gap-4 p-5 sm:grid-cols-[auto_1fr] sm:items-center" aria-labelledby="live-h">
              <MandalaSpinner size={84} title={STAGE_LABEL[order.stage]} className={isFinalStage(order.stage) ? "[&_.animate-mandala]:[animation-play-state:paused]" : ""} />
              <div className="grid gap-3">
                <div className="grid gap-1">
                  <div className="flex items-center gap-2">
                    <h2 id="live-h" className="ak-eyebrow">
                      From the studio
                    </h2>
                    {live && <span className="rounded-pill bg-danger px-1.5 py-0.5 text-[9px] font-bold tracking-[0.18em] text-cream">LIVE</span>}
                  </div>
                  <p className="text-sm">{last?.message?.trim() || STAGE_BLURB[order.stage]}</p>
                  {last && last.message.trim() && <p className="text-xs text-surface-muted">{STAGE_BLURB[order.stage]}</p>}
                </div>
                {detail && (detail.studio || detail.printerBay || detail.layerHeightMm !== undefined) && (
                  <dl className="grid grid-cols-3 gap-2 text-xs">
                    {detail.studio && (
                      <div className="ak-well grid gap-0.5 p-2.5">
                        <dt className="text-surface-muted">Studio</dt>
                        <dd className="font-semibold">{detail.studio}</dd>
                      </div>
                    )}
                    {detail.printerBay && (
                      <div className="ak-well grid gap-0.5 p-2.5">
                        <dt className="text-surface-muted">Printer</dt>
                        <dd className="font-semibold">{detail.printerBay}</dd>
                      </div>
                    )}
                    {detail.layerHeightMm !== undefined && (
                      <div className="ak-well grid gap-0.5 p-2.5">
                        <dt className="text-surface-muted">Layer height</dt>
                        <dd className="font-semibold">{detail.layerHeightMm} mm</dd>
                      </div>
                    )}
                  </dl>
                )}
                <p className="text-[11px] text-surface-muted">
                  {STATUS_LABEL[order.status]}
                  {last ? ` · ${formatPlaced(last.at)}` : ""}
                </p>
              </div>
            </section>
          )}

          {order.stage === "printing" && (
            <section className="ak-card relative grid gap-3 overflow-hidden p-5" aria-labelledby="timelapse-h">
              <BloomMark size={180} className="pointer-events-none absolute -right-10 -top-10 opacity-[.06]" aria-hidden="true" />
              <div className="flex items-center gap-4">
                <MandalaSpinner size={56} title="" className="[&>div]:hidden" />
                <div className="grid gap-1">
                  <h2 id="timelapse-h" className="font-display text-2xl font-semibold leading-tight">
                    Your time-lapse arrives on WhatsApp when the print is under way
                  </h2>
                  <p className="text-sm text-surface-muted">
                    Five seconds of your piece growing
                    {detail?.layer !== undefined && detail?.layersTotal !== undefined ? ` — layer ${detail.layer} of ${detail.layersTotal} right now` : ""}.
                    {order.notify_whatsapp === false ? " You opted out of WhatsApp updates for this order; it will be on this page instead." : ""}
                  </p>
                </div>
              </div>
            </section>
          )}

          <section className="ak-card grid gap-3 p-5" aria-labelledby="items-h">
            <h2 id="items-h" className="font-display text-2xl font-semibold">
              {order.items.length === 1 ? "Your piece" : "Your pieces"}
            </h2>
            <ul className="grid gap-3">
              {order.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3">
                  <FinishTile materialId={item.material_id} title={item.title} size={56} />
                  <div className="grid min-w-0 flex-1 gap-0.5">
                    <span className="font-semibold">{item.title}</span>
                    <span className="text-xs text-surface-muted">
                      {finishName(item)}
                      {item.specs_line ? ` · ${item.specs_line}` : ""}
                      {item.qty > 1 ? ` · × ${item.qty}` : ""}
                    </span>
                  </div>
                  <span className="font-display text-xl font-bold">{formatPaise(item.line_total_paise)}</span>
                </li>
              ))}
            </ul>
            <dl className="grid gap-1.5 border-t border-surface-border pt-3 text-[13px]">
              <div className="flex justify-between">
                <dt className="text-surface-muted">Subtotal</dt>
                <dd>{formatPaise(order.subtotal_paise)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-surface-muted">{order.shipping_label ?? "Shipping"}</dt>
                <dd className={order.shipping_paise === 0 ? "font-semibold text-success" : ""}>{order.shipping_paise === 0 ? "Free" : formatPaise(order.shipping_paise)}</dd>
              </div>
              <div className="flex justify-between text-[15px] font-bold">
                <dt>Total</dt>
                <dd>{formatPaise(order.total_paise)}</dd>
              </div>
            </dl>
          </section>
        </div>

        <aside className="grid gap-5">
          <section className="ak-card grid gap-2 p-5" aria-labelledby="addr-h">
            <h2 id="addr-h" className="ak-label">
              Deliver to
            </h2>
            <p className="font-semibold">{order.address.name}</p>
            <p className="text-sm text-surface-muted">{addressLines(order.address)}</p>
            <p className="text-xs text-surface-muted">
              {order.address.state} · {formatPhone(order.address.phone)}
            </p>
            {order.shipment && (
              <p className="mt-1 text-xs text-surface-muted">
                {order.shipment.carrier.replace(/^mock-?/, "").replace(/^\w/, (c) => c.toUpperCase())}
                {order.shipment.carrier.startsWith("mock") ? " (mock)" : ""}
                {order.shipment.awb ? ` · AWB ${order.shipment.awb}` : ""} · {order.shipment.status.replace(/_/g, " ")}
              </p>
            )}
          </section>

          <section className="ak-card grid gap-2 p-5" aria-labelledby="payment-h">
            <h2 id="payment-h" className="ak-label">
              Payment
            </h2>
            <p className="font-semibold">
              {PAYMENT_LABEL[order.payment.status]}
              {order.payment.method ? ` · ${paymentMethodLabel(order.payment.method)}` : ""}
            </p>
            <p className="text-sm text-surface-muted">{formatPaise(order.payment.amount_paise)}</p>
            <p className="text-xs text-surface-muted">
              {order.payment.invoice_number ? `Invoice ${order.payment.invoice_number}` : "Invoice number arrives with confirmation"}
              {order.payment.gateway === "mock" ? " · mock gateway" : ""}
            </p>
          </section>

          {order.events.length > 0 && (
            <section className="ak-card grid gap-2 p-5" aria-labelledby="history-h">
              <h2 id="history-h" className="ak-label">
                History
              </h2>
              <ol className="grid gap-2 text-xs">
                {[...order.events]
                  .sort((a, b) => b.sequence - a.sequence)
                  .map((ev) => (
                    <li key={`${ev.sequence}-${ev.stage}`} className="grid gap-0.5">
                      <span className="font-semibold">
                        {STAGE_LABEL[ev.stage]}
                        <span className="font-normal text-surface-muted"> · {formatPlaced(ev.at)}</span>
                      </span>
                      {ev.message && <span className="text-surface-muted">{ev.message}</span>}
                    </li>
                  ))}
              </ol>
            </section>
          )}

          <Link href="/orders" className="ak-btn ak-btn-secondary ak-btn-pill justify-self-start">
            All orders
          </Link>
        </aside>
      </div>
    </div>
  );
}
