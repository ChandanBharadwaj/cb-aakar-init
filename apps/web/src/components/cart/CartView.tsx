"use client";

import Link from "next/link";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { BloomMark } from "@/components/brand/BloomMark";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { useCartStore } from "@/store/cart";
import { useSession } from "@/store/session";
import { CartItemRow } from "./CartItemRow";
import { CartTotals } from "./CartTotals";

/** /cart: items with qty and remove, price lines, "Continue to checkout" (via sign-in for guests). */
export function CartView() {
  const { cart, status, problem } = useCartStore();
  const session = useSession();

  if (!cart && (status === "loading" || status === "idle")) {
    return <BloomLoader size={112} label="Opening your cart" className="py-16" />;
  }
  if (!cart) {
    return (
      <ProblemCard problem={problem ?? { title: "Couldn't open your cart", code: "unknown" }} title="Couldn't reach the studio" action={{ href: "/cart", label: "Try again" }} />
    );
  }

  if (cart.items.length === 0) {
    return (
      <div className="ak-card relative mx-auto grid max-w-xl gap-5 overflow-hidden p-8 text-center">
        <BloomMark size={220} className="pointer-events-none absolute -right-14 -top-10 opacity-[.06]" aria-hidden="true" />
        <BloomMark size={44} className="mx-auto" />
        <h2 className="font-display text-3xl font-semibold leading-tight">Your cart is empty</h2>
        <p className="text-surface-muted">Every piece here is printed to order. Pick one from the Shop or start with a template.</p>
        <div className="flex flex-wrap justify-center gap-3">
          <Link href="/shop" className="ak-btn ak-btn-primary ak-btn-pill">
            Browse the Shop
          </Link>
          <Link href="/create" className="ak-btn ak-btn-secondary ak-btn-pill">
            Create
          </Link>
        </div>
      </div>
    );
  }

  const blocked = cart.items.filter((i) => !i.purchasable);
  const checkoutHref = session.status === "user" ? "/checkout" : "/signin?next=%2Fcheckout";

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
      <ul className="grid gap-3" aria-label="Cart items">
        {cart.items.map((item) => (
          <CartItemRow key={item.id} item={item} />
        ))}
      </ul>
      <aside className="ak-card grid gap-4 p-5 lg:sticky lg:top-4">
        <h2 className="font-display text-2xl font-semibold">Summary</h2>
        <CartTotals subtotalPaise={cart.subtotal_paise} shippingPaise={cart.shipping_paise} shippingLabel={cart.shipping_label} totalPaise={cart.total_paise} />
        {blocked.length > 0 ? (
          <p role="alert" className="text-xs text-danger">
            {blocked.length === 1 ? "One piece" : `${blocked.length} pieces`} can&apos;t be printed as designed. Adjust or remove {blocked.length === 1 ? "it" : "them"} to continue.
          </p>
        ) : (
          <Link href={checkoutHref} className="ak-btn ak-btn-primary">
            Continue to checkout
          </Link>
        )}
        {session.status === "guest" && <p className="text-[11px] text-surface-muted">You&apos;ll sign in with your phone first; your cart comes with you.</p>}
        <p className="text-[11px] leading-relaxed text-surface-muted">Prices include hand finishing. Custom pieces are replaced or refunded for defects and mismatches only.</p>
      </aside>
    </div>
  );
}
