import type { Metadata } from "next";
import { RequireSignIn } from "@/components/identity/RequireSignIn";
import { OrdersList } from "@/components/orders/OrdersList";

export const metadata: Metadata = { title: "Orders" };

export default function OrdersPage() {
  return (
    <main className="mx-auto flex w-full max-w-[980px] flex-1 flex-col px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <div className="mb-6 grid gap-2">
        <h1 className="font-display text-[40px] font-semibold leading-none">Orders</h1>
        <p className="text-sm text-surface-muted">Your pieces, from bay to doorstep.</p>
      </div>
      <RequireSignIn next="/orders">
        <OrdersList />
      </RequireSignIn>
    </main>
  );
}
