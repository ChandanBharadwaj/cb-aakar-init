"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { OrderSummary, Problem } from "@/lib/api/types";
import { formatArrives, formatPlaced } from "@/lib/orders";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { BloomMark } from "@/components/brand/BloomMark";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { StagePill } from "./StagePill";

/** /orders: OrderSummary cards, newest first. */
export function OrdersList() {
  const [orders, setOrders] = useState<OrderSummary[]>();
  const [problem, setProblem] = useState<Problem>();

  useEffect(() => {
    let cancelled = false;
    api.orders
      .list()
      .then((o) => !cancelled && setOrders(o))
      .catch((err) => !cancelled && setProblem(toProblem(err)));
    return () => {
      cancelled = true;
    };
  }, []);

  if (problem) return <ProblemCard problem={problem} action={{ href: "/orders", label: "Try again" }} />;
  if (!orders) return <BloomLoader size={112} label="Fetching your orders" className="py-16" />;

  if (orders.length === 0) {
    return (
      <div className="ak-card relative mx-auto grid max-w-xl gap-5 overflow-hidden p-8 text-center">
        <BloomMark size={220} className="pointer-events-none absolute -right-14 -top-10 opacity-[.06]" aria-hidden="true" />
        <BloomMark size={44} className="mx-auto" />
        <h2 className="font-display text-3xl font-semibold leading-tight">Nothing on the bench yet</h2>
        <p className="text-surface-muted">Your orders appear here the moment you pay, with live stages from the studio.</p>
        <div className="flex justify-center gap-3">
          <Link href="/shop" className="ak-btn ak-btn-primary ak-btn-pill">
            Browse the Shop
          </Link>
          <Link href="/cart" className="ak-btn ak-btn-secondary ak-btn-pill">
            Your cart
          </Link>
        </div>
      </div>
    );
  }

  return (
    <ul className="grid gap-3" aria-label="Orders">
      {orders.map((o) => {
        const arrives = formatArrives(o.eta);
        return (
          <li key={o.id}>
            <Link href={`/orders/${o.id}`} className="ak-card grid gap-3 p-5 transition-shadow duration-base ease-ak hover:shadow-float sm:grid-cols-[1fr_auto] sm:items-center">
              <div className="grid gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-xs text-surface-muted">Order {o.number}</span>
                  <StagePill stage={o.stage} />
                </div>
                <h2 className="font-display text-2xl font-semibold leading-tight">{o.title ?? `${o.items_count} ${o.items_count === 1 ? "piece" : "pieces"}`}</h2>
                <p className="text-xs text-surface-muted">
                  Placed {formatPlaced(o.placed_at)} · {o.items_count} {o.items_count === 1 ? "piece" : "pieces"}
                </p>
              </div>
              <div className="grid gap-0.5 text-right">
                <span className="font-display text-2xl font-bold">{formatPaise(o.total_paise)}</span>
                <span className="text-xs text-surface-muted">{o.stage === "delivered" ? "Delivered" : o.stage === "cancelled" ? "Cancelled" : arrives ? `Arrives ${arrives}` : "ETA once confirmed"}</span>
              </div>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
