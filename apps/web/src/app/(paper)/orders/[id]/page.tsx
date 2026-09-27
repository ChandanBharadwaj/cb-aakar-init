import type { Metadata } from "next";
import { RequireSignIn } from "@/components/identity/RequireSignIn";
import { OrderTracking } from "@/components/orders/OrderTracking";

export const metadata: Metadata = { title: "Order" };

interface OrderPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ placed?: string }>;
}

export default async function OrderPage({ params, searchParams }: OrderPageProps) {
  const [{ id }, { placed }] = await Promise.all([params, searchParams]);
  return (
    <main className="mx-auto flex w-full max-w-[1100px] flex-1 flex-col px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <RequireSignIn next={`/orders/${encodeURIComponent(id)}`}>
        <OrderTracking orderId={id} placed={placed === "1"} />
      </RequireSignIn>
    </main>
  );
}
