"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { Order, Payment, PaymentMethod, Problem } from "@/lib/api/types";
import { PAYMENT_LABEL } from "@/lib/orders";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { BloomMark } from "@/components/brand/BloomMark";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { useCartStore } from "@/store/cart";
import { PaymentMethodChips, paymentMethodLabel } from "./PaymentMethodChips";

export interface MockPayPageProps {
  paymentId: string;
  /** From `?method=` set by checkout; defaults to UPI. */
  method?: string;
}

function isMethod(m: string | undefined): m is PaymentMethod {
  return m === "upi" || m === "card" || m === "netbanking";
}

/** /checkout/pay/[paymentId]: the placeholder gateway (ADR-0013). Unmistakably a mock. */
export function MockPayPage({ paymentId, method: initialMethod }: MockPayPageProps) {
  const router = useRouter();
  const [payment, setPayment] = useState<Payment>();
  const [order, setOrder] = useState<Order>();
  const [problem, setProblem] = useState<Problem>();
  const [method, setMethod] = useState<PaymentMethod>(isMethod(initialMethod) ? initialMethod : "upi");
  const [busy, setBusy] = useState<"success" | "failure" | "retry" | undefined>();
  const [outcome, setOutcome] = useState<"failed" | undefined>();
  const [actionProblem, setActionProblem] = useState<Problem>();

  useEffect(() => {
    let cancelled = false;
    api.payments
      .get(paymentId)
      .then(async (p) => {
        if (cancelled) return;
        setPayment(p);
        try {
          const o = await api.orders.get(p.order_id);
          if (!cancelled) setOrder(o);
        } catch {
          /* the order number is a nicety here */
        }
      })
      .catch((err) => !cancelled && setProblem(toProblem(err)));
    return () => {
      cancelled = true;
    };
  }, [paymentId]);

  async function complete(result: "success" | "failure") {
    if (!payment) return;
    setBusy(result);
    setActionProblem(undefined);
    try {
      const done = await api.payments.mockComplete(payment.id, { outcome: result, method });
      setPayment(done);
      if (done.status === "succeeded") {
        void useCartStore.getState().refresh();
        router.replace(`/orders/${done.order_id}?placed=1`);
        return;
      }
      setOutcome("failed");
    } catch (err) {
      const p = toProblem(err);
      if (p.code === "payment_final") {
        try {
          setPayment(await api.payments.get(payment.id));
        } catch {
          /* keep what we have */
        }
      }
      setActionProblem(p);
    } finally {
      setBusy(undefined);
    }
  }

  async function retry() {
    if (!payment) return;
    setBusy("retry");
    setActionProblem(undefined);
    try {
      const fresh = await api.orders.newPayment(payment.order_id);
      const target = new URL(fresh.pay_url ?? `/checkout/pay/${fresh.id}`, window.location.origin);
      target.searchParams.set("method", method);
      window.location.assign(target.toString());
    } catch (err) {
      setActionProblem(toProblem(err));
      setBusy(undefined);
    }
  }

  if (problem) {
    return <ProblemCard problem={problem} title={problem.code === "not_found" ? "No such payment" : undefined} action={{ href: "/cart", label: "Back to cart" }} />;
  }
  if (!payment) return <BloomLoader size={112} label="Opening the gateway" className="py-16" />;

  const final = payment.status === "succeeded" || payment.status === "refunded" || payment.status === "failed";
  const notMock = payment.gateway !== "mock";
  const failed = payment.status === "failed" || outcome === "failed";

  return (
    <div className="mx-auto grid w-full max-w-lg gap-4">
      <div className="flex items-center gap-3 rounded-control border border-marigold bg-marigold/20 px-4 py-2.5 text-xs font-semibold text-ink" role="note">
        <span className="rounded-pill bg-marigold px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider">Mock</span>
        Mock payment gateway · local only. No money moves; this page stands in for Razorpay (ADR-0013).
      </div>

      <div className="ak-card relative grid gap-6 overflow-hidden p-7">
        <BloomMark size={200} className="pointer-events-none absolute -right-14 -top-12 opacity-[.06]" aria-hidden="true" />
        <div className="grid gap-1">
          <span className="ak-eyebrow">Pay Aakar</span>
          <div className="font-display text-[44px] font-bold leading-none">{formatPaise(payment.amount_paise)}</div>
          <p className="text-sm text-surface-muted">
            Order <span className="font-mono text-surface-text">{order?.number ?? payment.order_id}</span>
            {order?.title ? ` · ${order.title}` : ""}
          </p>
        </div>

        {notMock ? (
          <p className="text-sm text-surface-muted">This payment belongs to a real gateway, which confirms it on its own page. Nothing to simulate here.</p>
        ) : final && !outcome ? (
          <div className="grid gap-3">
            <p className="text-sm">
              This payment is already <strong>{PAYMENT_LABEL[payment.status].toLowerCase()}</strong>
              {payment.method ? ` (${paymentMethodLabel(payment.method)})` : ""}.
            </p>
            <div className="flex flex-wrap gap-2">
              {payment.status === "succeeded" || payment.status === "refunded" ? (
                <Link href={`/orders/${payment.order_id}`} className="ak-btn ak-btn-primary ak-btn-pill">
                  View the order
                </Link>
              ) : (
                <button type="button" className="ak-btn ak-btn-primary ak-btn-pill" onClick={() => void retry()} disabled={Boolean(busy)} aria-busy={busy === "retry"}>
                  {busy === "retry" ? "Starting a new attempt…" : "Try again"}
                </button>
              )}
              <Link href="/cart" className="ak-btn ak-btn-secondary ak-btn-pill">
                Back to cart
              </Link>
            </div>
          </div>
        ) : failed ? (
          <div className="grid gap-3" role="alert">
            <div className="flex items-start gap-3">
              <span className="grid h-8 w-8 flex-none place-items-center rounded-full bg-danger/15 font-display text-lg font-bold text-danger" aria-hidden="true">
                !
              </span>
              <div className="grid gap-1">
                <p className="font-display text-2xl font-semibold leading-tight">Payment failed (simulated)</p>
                <p className="text-sm text-surface-muted">Your order is still reserved and waiting for payment. Start a new attempt, or go back to your cart.</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <button type="button" className="ak-btn ak-btn-primary ak-btn-pill" onClick={() => void retry()} disabled={Boolean(busy)} aria-busy={busy === "retry"}>
                {busy === "retry" ? "Starting a new attempt…" : "Try again"}
              </button>
              <Link href="/cart" className="ak-btn ak-btn-secondary ak-btn-pill">
                Back to cart
              </Link>
            </div>
          </div>
        ) : (
          <>
            <div className="grid gap-2">
              <span className="ak-label">Pay with</span>
              <PaymentMethodChips value={method} onChange={setMethod} compact disabled={Boolean(busy)} />
            </div>
            <div className="grid gap-2">
              <button type="button" className="ak-btn ak-btn-primary" onClick={() => void complete("success")} disabled={Boolean(busy)} aria-busy={busy === "success"}>
                {busy === "success" ? "Confirming…" : "Simulate successful payment"}
              </button>
              <button type="button" className="ak-btn ak-btn-secondary" onClick={() => void complete("failure")} disabled={Boolean(busy)} aria-busy={busy === "failure"}>
                {busy === "failure" ? "Failing…" : "Simulate failed payment"}
              </button>
            </div>
          </>
        )}

        {actionProblem && (
          <p role="alert" className="text-xs text-danger">
            {actionProblem.detail ?? actionProblem.title}
            {actionProblem.code ? ` (${actionProblem.code})` : ""}
          </p>
        )}
        <p className="font-mono text-[10px] text-surface-muted">
          payment {payment.id} · gateway {payment.gateway} · {payment.status}
        </p>
      </div>
    </div>
  );
}
