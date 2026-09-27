import type { Metadata } from "next";
import { MockPayPage } from "@/components/checkout/MockPayPage";
import { RequireSignIn } from "@/components/identity/RequireSignIn";

export const metadata: Metadata = { title: "Mock payment" };

interface PayPageProps {
  params: Promise<{ paymentId: string }>;
  searchParams: Promise<{ method?: string }>;
}

export default async function PayPage({ params, searchParams }: PayPageProps) {
  const [{ paymentId }, { method }] = await Promise.all([params, searchParams]);
  const next = `/checkout/pay/${encodeURIComponent(paymentId)}${method ? `?method=${encodeURIComponent(method)}` : ""}`;
  return (
    <main className="flex flex-1 flex-col items-center px-4 py-8 sm:py-12">
      <div className="w-full">
        <RequireSignIn next={next}>
          <MockPayPage paymentId={paymentId} method={method} />
        </RequireSignIn>
      </div>
    </main>
  );
}
