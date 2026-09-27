import type { Metadata } from "next";
import { Suspense } from "react";
import { OrderQueue } from "@/components/orders/OrderQueue";
import { Loading } from "@/components/ui/Loading";

export const metadata: Metadata = { title: "Orders" };

export default function OrdersPage() {
  return (
    <Suspense fallback={<Loading />}>
      <OrderQueue />
    </Suspense>
  );
}
