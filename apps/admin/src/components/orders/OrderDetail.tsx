"use client";

import Link from "next/link";
import { useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, saveBlob, toProblem } from "@/lib/api/client";
import type { MediaAsset, Payment, Problem, Shipment } from "@/lib/api/types";
import { formatDate, formatDateTime, formatRelative } from "@/lib/format";
import { STAGE_LABEL, isPackedOrLater, type Tone } from "@/lib/orders";
import { useQuery } from "@/lib/useQuery";
import { AdvancePanel } from "./AdvancePanel";
import { EventsLog } from "./EventsLog";
import { OrderItemsTable } from "./OrderItemsTable";
import { OrderTimeline } from "./OrderTimeline";
import { QcPhotos } from "./QcPhotos";
import { Loading } from "@/components/ui/Loading";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Stat } from "@/components/ui/Stat";
import { Pill, StatusPill } from "@/components/ui/StatusPill";

const PAYMENT_TONE: Record<Payment["status"], Tone> = { created: "neutral", pending: "warning", succeeded: "success", failed: "danger", refunded: "info" };
const SHIPMENT_TONE: Record<Shipment["status"], Tone> = { created: "neutral", picked_up: "info", in_transit: "info", out_for_delivery: "accent", delivered: "success", returned: "danger" };

export function OrderDetail({ id }: { id: string }) {
  const { data: order, problem, loading, reload, setData } = useQuery(() => api.orders.get(id), `order:${id}`);
  const [downloading, setDownloading] = useState<"pack" | "card">();
  const [downloadProblem, setDownloadProblem] = useState<Problem>();

  async function fetchFile(kind: "pack" | "card") {
    if (!order) return;
    setDownloading(kind);
    setDownloadProblem(undefined);
    try {
      const { blob, filename } = kind === "pack" ? await api.orders.printPack(order.id, order.number) : await api.orders.packagingCard(order.id, order.number);
      saveBlob(blob, filename);
    } catch (err) {
      setDownloadProblem(toProblem(err));
    } finally {
      setDownloading(undefined);
    }
  }

  if (problem && !order) {
    return <ProblemCard problem={problem} title={problem.status === 404 ? "No such order" : undefined} action={{ href: "/orders", label: "Back to orders" }} />;
  }
  if (!order) return <Loading />;

  const packed = isPackedOrLater(order.status);
  const resumeTo = order.status === "on_hold" ? order.next_actions.find((a) => a !== "cancelled" && a !== "on_hold") : undefined;

  return (
    <div className="grid gap-6" aria-busy={loading}>
      <div className="grid gap-3">
        <Link href="/orders" className="text-xs font-medium text-surface-muted hover:text-surface-text">
          ← Orders
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="grid gap-2">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="font-display text-[34px] font-semibold leading-none">{order.number}</h1>
              <StatusPill status={order.status} />
              <span className="text-xs text-surface-muted">customer sees: {STAGE_LABEL[order.stage]}</span>
            </div>
            <p className="text-sm text-surface-muted">
              {order.title} · {order.items_count} {order.items_count === 1 ? "piece" : "pieces"} · {formatPaise(order.total_paise)}
            </p>
            <p className="text-xs text-surface-muted">
              <span className="font-medium text-surface-text">{order.customer.name ?? "No name"}</span> · <span className="font-mono">{order.customer.phone}</span>
              {order.customer.email ? ` · ${order.customer.email}` : ""} · placed {formatRelative(order.placed_at)} ({formatDateTime(order.placed_at)}) · ETA {formatDate(order.eta)}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="ak-btn ak-btn-primary" onClick={() => void fetchFile("pack")} disabled={downloading !== undefined} title="Zip: model.3mf, model.stl and print-sheet.txt per item, for the outsourced printer (ADR-0004)">
              {downloading === "pack" ? "Preparing…" : "Print pack"}
            </button>
            <button type="button" className="ak-btn ak-btn-secondary" onClick={() => void fetchFile("card")} disabled={!packed || downloading !== undefined} title={packed ? "PDF card for the box" : "Available once the order is packed"}>
              {downloading === "card" ? "Preparing…" : "Packaging card"}
            </button>
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm self-center" onClick={() => void reload()} disabled={loading}>
              Refresh
            </button>
          </div>
        </div>
        {!packed && <p className="text-[11px] text-surface-muted">The packaging card (“Designed by You. Crafted by Aakar.”) is generated when the order reaches packed.</p>}
        {downloadProblem && <ProblemCard compact problem={downloadProblem} />}
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="grid gap-6">
          <section className="ak-card p-5">
            <OrderTimeline status={order.status} resumeTo={resumeTo} />
          </section>
          <AdvancePanel order={order} onAdvanced={(o) => setData(o)} />
          <OrderItemsTable items={order.items} />
          <QcPhotos orderId={order.id} photos={order.qc_photos ?? []} onUploaded={(asset: MediaAsset) => setData((prev) => (prev ? { ...prev, qc_photos: [...(prev.qc_photos ?? []), asset] } : prev))} />
          <EventsLog events={order.events} />
        </div>

        <aside className="grid content-start gap-4">
          <section className="ak-card grid gap-2.5 p-4" aria-labelledby="customer-heading">
            <h2 id="customer-heading" className="ak-label">
              Customer
            </h2>
            <Stat label="Name" value={order.customer.name ?? "—"} />
            <Stat label="Phone" value={<span className="font-mono">{order.customer.phone}</span>} />
            <Stat label="Email" value={order.customer.email ?? "—"} />
            <Stat label="WhatsApp updates" value={order.notify_whatsapp ? "Opted in" : "No"} />
            {order.note && (
              <p className="ak-well p-2.5 text-xs">
                <span className="ak-label mr-1.5">Note</span>
                {order.note}
              </p>
            )}
          </section>

          <section className="ak-card grid gap-1 p-4 text-sm" aria-labelledby="address-heading">
            <h2 id="address-heading" className="ak-label mb-1.5">
              Ship to {order.address.label ? `· ${order.address.label}` : ""}
            </h2>
            <span className="font-medium">{order.address.name}</span>
            <span>{order.address.line1}</span>
            {order.address.line2 && <span>{order.address.line2}</span>}
            <span>
              {order.address.city}, {order.address.state} {order.address.pincode}
            </span>
            <span className="font-mono text-xs text-surface-muted">{order.address.phone}</span>
          </section>

          <section className="ak-card grid gap-2.5 p-4" aria-labelledby="payment-heading">
            <div className="flex items-center justify-between">
              <h2 id="payment-heading" className="ak-label">
                Payment
              </h2>
              <Pill tone={PAYMENT_TONE[order.payment.status]}>{order.payment.status}</Pill>
            </div>
            <Stat label="Amount" value={formatPaise(order.payment.amount_paise)} />
            <Stat label="Gateway" value={`${order.payment.gateway}${order.payment.method ? ` · ${order.payment.method}` : ""}`} />
            <Stat label="Reference" value={<span className="font-mono text-[11px]">{order.payment.gateway_ref ?? "—"}</span>} />
            <Stat label="Invoice" value={order.payment.invoice_number ?? "—"} />
            <Stat label="Completed" value={formatDateTime(order.payment.finished_at)} />
            <div className="border-t border-surface-border pt-2">
              <Stat label="Subtotal" value={formatPaise(order.subtotal_paise)} />
              <Stat label={order.shipping_label ?? "Shipping"} value={order.shipping_paise === 0 ? "Free" : formatPaise(order.shipping_paise)} />
              <Stat label="Policy" value={<span className="font-mono text-[11px]">{order.policy_version ?? "—"}</span>} />
            </div>
          </section>

          <section className="ak-card grid gap-2.5 p-4" aria-labelledby="shipment-heading">
            <div className="flex items-center justify-between">
              <h2 id="shipment-heading" className="ak-label">
                Shipment
              </h2>
              {order.shipment && <Pill tone={SHIPMENT_TONE[order.shipment.status]}>{order.shipment.status.replace(/_/g, " ")}</Pill>}
            </div>
            {order.shipment ? (
              <>
                <Stat label="Carrier" value={order.shipment.carrier} />
                <Stat label="AWB" value={<span className="font-mono text-[12px]">{order.shipment.awb ?? "—"}</span>} />
                <Stat label="ETA" value={formatDate(order.shipment.eta)} />
                {order.shipment.tracking_url && (
                  <a href={order.shipment.tracking_url} target="_blank" rel="noreferrer" className="text-xs font-medium text-surface-accent hover:underline">
                    Carrier tracking page ↗
                  </a>
                )}
                {(order.shipment.events ?? []).length > 0 && (
                  <ol className="grid gap-1.5 border-t border-surface-border pt-2 text-xs">
                    {[...(order.shipment.events ?? [])].reverse().map((ev, i) => (
                      <li key={`${ev.at}-${i}`} className="grid gap-0.5">
                        <span className="font-medium">{ev.message ?? ev.status}</span>
                        <span className="text-surface-muted">{formatDateTime(ev.at)}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </>
            ) : (
              <p className="text-xs text-surface-muted">Created when the order is packed: the carrier assigns the AWB and a 4-day ETA.</p>
            )}
          </section>

          <section className="ak-card grid gap-2.5 p-4" aria-labelledby="notifications-heading">
            <div className="flex items-center justify-between">
              <h2 id="notifications-heading" className="ak-label">
                Messages for this order
              </h2>
              <Link href={`/messages?order_id=${order.id}`} className="text-xs font-medium text-surface-accent hover:underline">
                Log ↗
              </Link>
            </div>
            {(order.notifications ?? []).length === 0 ? (
              <p className="text-xs text-surface-muted">Nothing sent yet.</p>
            ) : (
              <ul className="grid gap-2 text-xs">
                {(order.notifications ?? []).map((n) => (
                  <li key={n.id} className="grid gap-0.5">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <Pill tone={n.channel === "whatsapp" ? "success" : n.channel === "email" ? "info" : "neutral"}>{n.channel}</Pill>
                      <span className="font-medium">{n.template.replace(/_/g, " ")}</span>
                      <span className="text-surface-muted">· {n.status}</span>
                    </span>
                    <span className="text-surface-muted">
                      to {n.to} · {formatRelative(n.created_at)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}

