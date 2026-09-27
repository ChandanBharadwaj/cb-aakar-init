import type { Metadata } from "next";
import { CartView } from "@/components/cart/CartView";

export const metadata: Metadata = { title: "Cart" };

export default function CartPage() {
  return (
    <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <div className="mb-6 grid gap-2">
        <h1 className="font-display text-[40px] font-semibold leading-none">Cart</h1>
        <p className="text-sm text-surface-muted">Pieces waiting to be printed. Nothing is made until you pay.</p>
      </div>
      <CartView />
    </main>
  );
}
