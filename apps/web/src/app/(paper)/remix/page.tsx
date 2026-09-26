import type { Metadata } from "next";
import { PhaseCard } from "@/components/ui/PhaseCard";

export const metadata: Metadata = { title: "Remix" };

export default function RemixPage() {
  return (
    <main className="flex-1 px-4 py-12 sm:px-8">
      <PhaseCard eyebrow="Remix" title="Take a proven piece, make it yours" phase={2} links={[{ href: "/shop?from=remix", label: "Modify a Shop piece", primary: true }, { href: "/create", label: "Start from a template" }]}>
        Talking to your karigar — “add a small elephant on the side and make the back wider” — lands with the co-designer. Until then, every Shop piece
        already opens in the studio with size sliders and finishes.
      </PhaseCard>
    </main>
  );
}
