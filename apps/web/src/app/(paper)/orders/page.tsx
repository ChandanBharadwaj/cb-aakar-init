import type { Metadata } from "next";
import { PhaseCard } from "@/components/ui/PhaseCard";

export const metadata: Metadata = { title: "Orders" };

export default function OrdersPage() {
  return (
    <main className="flex-1 px-4 py-12 sm:px-8">
      <PhaseCard eyebrow="Orders" title="Your pieces, from bay to doorstep" phase={1} links={[{ href: "/shop", label: "Browse the Shop", primary: true }, { href: "/", label: "Home" }]}>
        Live stages, the printer bay, layer height and an ETA — plus a five-second time-lapse on WhatsApp mid-print.
      </PhaseCard>
    </main>
  );
}
