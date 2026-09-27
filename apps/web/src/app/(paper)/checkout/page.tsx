import type { Metadata } from "next";
import { Checkout } from "@/components/checkout/Checkout";
import { RequireSignIn } from "@/components/identity/RequireSignIn";

export const metadata: Metadata = { title: "Checkout" };

export default function CheckoutPage() {
  return (
    <main className="mx-auto flex w-full max-w-[1180px] flex-1 flex-col px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <div className="mb-6 grid gap-2">
        <h1 className="font-display text-[40px] font-semibold leading-none">Checkout</h1>
        <p className="text-sm text-surface-muted">Review, check stability, pay. Then follow your piece live from the studio.</p>
      </div>
      <RequireSignIn next="/checkout">
        <Checkout />
      </RequireSignIn>
    </main>
  );
}
