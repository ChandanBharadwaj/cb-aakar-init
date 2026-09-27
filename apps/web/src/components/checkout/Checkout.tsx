"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { Address, CartItem, PaymentMethod, PrintabilityReport, Problem, Serviceability } from "@/lib/api/types";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { CartItemRow, finishName } from "@/components/cart/CartItemRow";
import { CartTotals } from "@/components/cart/CartTotals";
import { FinishTile } from "@/components/cart/FinishTile";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { StabilityCard } from "@/components/ui/StabilityCard";
import { useCartStore } from "@/store/cart";
import { useSession } from "@/store/session";
import { AddressForm } from "./AddressForm";
import { AddressPicker, addressLines } from "./AddressPicker";
import { CheckoutSteps, type CheckoutStep } from "./CheckoutSteps";
import { PaymentMethodChips } from "./PaymentMethodChips";
import { serviceabilityLabel } from "./serviceability";

type ReportState = { status: "loading" } | { status: "ready"; report: PrintabilityReport } | { status: "error"; problem: Problem & { code: string } };

const ORDER: CheckoutStep[] = ["review", "stability", "pay", "track"];
const later = (a: CheckoutStep, b: CheckoutStep) => (ORDER.indexOf(a) >= ORDER.indexOf(b) ? a : b);

/** /checkout: Review → Stability → Pay; Track is the order page after the gateway returns. */
export function Checkout() {
  const { cart, status: cartStatus } = useCartStore();
  const { user } = useSession();
  const [step, setStepState] = useState<CheckoutStep>("review");
  const [reached, setReached] = useState<CheckoutStep>("review");
  const setStep = (s: CheckoutStep) => {
    setStepState(s);
    setReached((r) => later(r, s));
  };

  // Stability: one report per version in the cart.
  const [reports, setReports] = useState<Record<string, ReportState>>({});
  const versionIds = useMemo(() => Array.from(new Set((cart?.items ?? []).map((i) => i.version_id))), [cart]);

  const loadReports = useCallback(
    (ids: string[], force = false) => {
      for (const v of ids) {
        if (!force && reports[v] && reports[v].status !== "error") continue;
        setReports((r) => ({ ...r, [v]: { status: "loading" } }));
        api.versions
          .printability(v)
          .then((report) => setReports((r) => ({ ...r, [v]: { status: "ready", report } })))
          .catch((err) => setReports((r) => ({ ...r, [v]: { status: "error", problem: toProblem(err) } })));
      }
    },
    [reports],
  );

  useEffect(() => {
    if (step === "stability") loadReports(versionIds);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- refetch when the set of versions changes, not on every report update
  }, [step, versionIds.join("|")]);

  // Pay: addresses, serviceability, method, WhatsApp.
  const [addresses, setAddresses] = useState<Address[]>();
  const [addressProblem, setAddressProblem] = useState<Problem>();
  const [addressId, setAddressId] = useState<string>();
  const [editing, setEditing] = useState<Address | "new">();
  const [serviceability, setServiceability] = useState<Record<string, Serviceability | undefined>>({});
  const [method, setMethod] = useState<PaymentMethod>("upi");
  const [whatsapp, setWhatsapp] = useState(true);
  const [paying, setPaying] = useState(false);
  const [checkoutProblem, setCheckoutProblem] = useState<(Problem & { code: string }) | undefined>();

  useEffect(() => {
    if (step !== "pay" || addresses) return;
    let cancelled = false;
    api.addresses
      .list()
      .then((list) => {
        if (cancelled) return;
        setAddresses(list);
        setAddressId((id) => id ?? (list.find((a) => a.is_default) ?? list[0])?.id);
        if (list.length === 0) setEditing("new");
      })
      .catch((err) => !cancelled && setAddressProblem(toProblem(err)));
    return () => {
      cancelled = true;
    };
  }, [step, addresses]);

  const selected = addresses?.find((a) => a.id === addressId);
  useEffect(() => {
    const pin = selected?.pincode;
    if (!pin || serviceability[pin] !== undefined) return;
    let cancelled = false;
    api.shipping
      .serviceability(pin)
      .then((s) => !cancelled && setServiceability((m) => ({ ...m, [pin]: s })))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [selected?.pincode, serviceability]);

  const items: CartItem[] = cart?.items ?? [];
  const blocked = items.filter((i) => !i.purchasable);
  const stabilityDone = versionIds.every((v) => reports[v]?.status === "ready");
  const stabilityFailed = versionIds.filter((v) => {
    const r = reports[v];
    return (r?.status === "ready" && !r.report.passed) || (r?.status === "error" && r.problem.code !== "version_not_ready");
  });
  const stabilityOk = stabilityDone && stabilityFailed.length === 0 && blocked.length === 0;
  const svc = selected ? serviceability[selected.pincode] : undefined;
  const svcLabel = svc ? serviceabilityLabel(svc) : undefined;
  const canPay = Boolean(cart && items.length > 0 && selected && (svc === undefined || svc.serviceable) && !paying && !editing);

  async function pay() {
    if (!selected || !cart) return;
    setPaying(true);
    setCheckoutProblem(undefined);
    try {
      const result = await api.checkout({ address_id: selected.id, notify_whatsapp: whatsapp });
      const target = new URL(result.payment.pay_url ?? `/checkout/pay/${result.payment.id}`, window.location.origin);
      target.searchParams.set("method", method);
      window.location.assign(target.toString());
    } catch (err) {
      const p = toProblem(err);
      setCheckoutProblem(p);
      setPaying(false);
      if (p.code === "cart_empty" || p.code === "not_printable") void useCartStore.getState().refresh();
      if (p.code === "not_printable") {
        setStep("stability");
        loadReports(versionIds, true);
      }
    }
  }

  if (!cart && (cartStatus === "loading" || cartStatus === "idle")) {
    return <BloomLoader size={112} label="Opening your cart" className="py-16" />;
  }
  if (!cart) {
    return <ProblemCard problem={useCartStore.getState().problem ?? { title: "Couldn't open your cart", code: "unknown" }} title="Couldn't reach the studio" action={{ href: "/checkout", label: "Try again" }} />;
  }
  if (items.length === 0) {
    return (
      <ProblemCard problem={{ title: "Your cart is empty", detail: "Add a piece from the Shop or the studio, then come back to check out.", code: "cart_empty" }} action={{ href: "/shop", label: "Browse the Shop" }} />
    );
  }

  return (
    <div className="grid gap-6">
      <CheckoutSteps current={step} reached={reached} onSelect={(s) => setStep(s)} />

      {step === "review" && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <section className="grid gap-3" aria-labelledby="review-h">
            <h2 id="review-h" className="font-display text-2xl font-semibold">
              Your pieces
            </h2>
            <ul className="grid gap-3">
              {items.map((item) => (
                <CartItemRow key={item.id} item={item} />
              ))}
            </ul>
          </section>
          <aside className="ak-card grid gap-4 p-5 lg:sticky lg:top-4">
            <CartTotals subtotalPaise={cart.subtotal_paise} shippingPaise={cart.shipping_paise} shippingLabel={cart.shipping_label} totalPaise={cart.total_paise} />
            <button type="button" className="ak-btn ak-btn-primary" onClick={() => setStep("stability")} disabled={blocked.length > 0}>
              Continue to stability check
            </button>
            {blocked.length > 0 && (
              <p role="alert" className="text-xs text-danger">
                Adjust or remove the {blocked.length === 1 ? "piece" : "pieces"} that can&apos;t be printed to continue.
              </p>
            )}
          </aside>
        </div>
      )}

      {step === "stability" && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <section className="grid gap-4" aria-labelledby="stability-h">
            <div className="grid gap-1">
              <h2 id="stability-h" className="font-display text-2xl font-semibold">
                Stability check
              </h2>
              <p className="text-sm text-surface-muted">Balance, wall thickness and fit for every piece, straight from the studio&apos;s physics check.</p>
            </div>
            <ul className="grid gap-4">
              {items.map((item) => {
                const r = reports[item.version_id];
                return (
                  <li key={item.id} className="grid gap-2">
                    <div className="flex items-center gap-3">
                      <FinishTile materialId={item.material_id} thumbnailUrl={item.thumbnail_url} title={item.title} size={40} />
                      <div className="grid">
                        <span className="font-semibold">{item.title}</span>
                        <span className="text-xs text-surface-muted">
                          {finishName(item)} · qty {item.qty}
                        </span>
                      </div>
                    </div>
                    <StabilityCard report={r?.status === "ready" ? r.report : undefined} pending={!r || r.status === "loading" || (r.status === "error" && r.problem.code === "version_not_ready")} />
                    {r?.status === "error" && r.problem.code !== "version_not_ready" && (
                      <p role="alert" className="text-xs text-danger">
                        Couldn&apos;t fetch this report: {r.problem.detail ?? r.problem.title}.{" "}
                        <button type="button" className="underline" onClick={() => loadReports([item.version_id], true)}>
                          Try again
                        </button>
                      </p>
                    )}
                    {((r?.status === "ready" && !r.report.passed) || !item.purchasable) && (
                      <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-xs text-danger">
                        This piece needs a small change before it can be printed.{" "}
                        <Link href={`/design/${item.design_id}`} className="font-semibold underline">
                          Open it in the studio
                        </Link>{" "}
                        or remove it from your cart.
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
          <aside className="ak-card grid gap-4 p-5 lg:sticky lg:top-4">
            <div className="flex items-center gap-3">
              <span className={`grid h-8 w-8 place-items-center rounded-full text-sm font-bold ${stabilityOk ? "bg-sage text-cream" : stabilityDone ? "bg-danger text-cream" : "bg-surface-border text-surface-muted animate-pulse-soft"}`} aria-hidden="true">
                {stabilityOk ? "✓" : stabilityDone ? "!" : "·"}
              </span>
              <span className="font-display text-lg font-semibold">{stabilityOk ? "Every piece stands" : stabilityDone ? "Needs a small change" : "Checking physics"}</span>
            </div>
            <CartTotals subtotalPaise={cart.subtotal_paise} shippingPaise={cart.shipping_paise} shippingLabel={cart.shipping_label} totalPaise={cart.total_paise} />
            <button type="button" className="ak-btn ak-btn-primary" onClick={() => setStep("pay")} disabled={!stabilityOk}>
              Continue to payment
            </button>
            {stabilityDone && !stabilityOk && <p className="text-xs text-danger">Fix or remove the flagged {stabilityFailed.length + blocked.length === 1 ? "piece" : "pieces"} to continue.</p>}
          </aside>
        </div>
      )}

      {step === "pay" && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
          <div className="grid gap-6">
            <section className="ak-card grid gap-4 p-5" aria-labelledby="deliver-h">
              <div className="flex items-baseline justify-between">
                <h2 id="deliver-h" className="font-display text-2xl font-semibold">
                  Deliver to
                </h2>
                {svcLabel && (
                  <span className={`text-xs font-semibold ${svcLabel.ok ? "text-success" : "text-danger"}`} role="status">
                    {svcLabel.text}
                    {svcLabel.mock && <span className="ml-1 font-normal text-surface-muted">(mock)</span>}
                  </span>
                )}
              </div>
              {addressProblem ? (
                <ProblemCard problem={addressProblem} title="Couldn't load your addresses" action={{ href: "/checkout", label: "Try again" }} />
              ) : !addresses ? (
                <BloomLoader size={64} label={null} />
              ) : editing ? (
                <AddressForm
                  initial={editing === "new" ? undefined : editing}
                  defaultPhone={user?.phone}
                  onSaved={(a) => {
                    setAddresses((list) => {
                      const rest = (list ?? []).filter((x) => x.id !== a.id).map((x) => (a.is_default ? { ...x, is_default: false } : x));
                      return [a, ...rest];
                    });
                    setAddressId(a.id);
                    setEditing(undefined);
                    setServiceability((m) => ({ ...m, [a.pincode]: undefined }));
                    setCheckoutProblem(undefined);
                  }}
                  onCancel={() => setEditing(undefined)}
                />
              ) : (
                <AddressPicker addresses={addresses} selectedId={addressId} onSelect={setAddressId} onAdd={() => setEditing("new")} onEdit={setEditing} serviceability={serviceability} disabled={paying} />
              )}
              {svc && !svc.serviceable && (
                <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-xs text-danger">
                  We can&apos;t deliver to {selected?.pincode} yet. Try another address.
                </p>
              )}
            </section>

            <section className="ak-card grid gap-4 p-5" aria-labelledby="pay-h">
              <h2 id="pay-h" className="font-display text-2xl font-semibold">
                Pay with
              </h2>
              <PaymentMethodChips value={method} onChange={setMethod} disabled={paying} />
              <p className="text-[11px] text-surface-muted">You&apos;ll confirm the payment on the gateway&apos;s page. Until the real gateway is connected, that page is a clearly marked mock.</p>
            </section>

            <label className="ak-card flex items-start gap-3 p-5 text-sm">
              <input type="checkbox" checked={whatsapp} onChange={(e) => setWhatsapp(e.target.checked)} className="mt-0.5 h-4 w-4 accent-[var(--ak-accent)]" disabled={paying} />
              <span className="grid gap-0.5">
                <span className="font-semibold">WhatsApp updates</span>
                <span className="text-xs text-surface-muted">Order confirmed, a five-second time-lapse while your piece prints, and the tracking link when it ships.</span>
              </span>
            </label>
          </div>

          <aside className="ak-card grid gap-4 p-5 lg:sticky lg:top-4">
            <h2 className="font-display text-2xl font-semibold">Order</h2>
            <ul className="grid gap-2 text-[13px]">
              {items.map((item) => (
                <li key={item.id} className="flex justify-between gap-3">
                  <span className="min-w-0 truncate text-surface-muted">
                    {item.title}
                    {item.qty > 1 ? ` × ${item.qty}` : ""} · {finishName(item)}
                  </span>
                  <span>{formatPaise(item.line_total_paise)}</span>
                </li>
              ))}
            </ul>
            <CartTotals subtotalPaise={cart.subtotal_paise} shippingPaise={cart.shipping_paise} shippingLabel={cart.shipping_label} totalPaise={cart.total_paise} className="border-t border-surface-border pt-3" />
            {selected && (
              <div className="ak-well grid gap-0.5 p-3 text-xs">
                <span className="ak-label">Deliver to</span>
                <span className="font-semibold text-surface-text">{selected.name}</span>
                <span className="text-surface-muted">{addressLines(selected)}</span>
              </div>
            )}
            <button type="button" className="ak-btn ak-btn-primary" onClick={() => void pay()} disabled={!canPay} aria-busy={paying}>
              {paying ? "Reserving your pieces…" : `Pay ${formatPaise(cart.total_paise)}`}
            </button>
            {checkoutProblem && (
              <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-xs text-danger">
                {checkoutProblem.code === "cart_empty"
                  ? "Your cart is empty now."
                  : checkoutProblem.code === "not_printable"
                    ? "A piece in your cart can't be printed as designed any more. Re-check stability."
                    : checkoutProblem.code === "not_serviceable"
                      ? "We can't deliver to that pincode yet. Pick or add another address."
                      : (checkoutProblem.detail ?? checkoutProblem.title)}
                {checkoutProblem.code === "cart_empty" && (
                  <>
                    {" "}
                    <Link href="/shop" className="font-semibold underline">
                      Browse the Shop
                    </Link>
                  </>
                )}
              </p>
            )}
            <p className="text-[11px] leading-relaxed text-surface-muted">Custom pieces are replaced or refunded for defects and mismatches only. GST invoice arrives with your order.</p>
          </aside>
        </div>
      )}
    </div>
  );
}
