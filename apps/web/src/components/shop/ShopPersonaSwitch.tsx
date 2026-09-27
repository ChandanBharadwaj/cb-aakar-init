import Link from "next/link";
import { shopHref, type ShopView } from "@/lib/experiences";

const PERSONAS: readonly { view: ShopView; label: string; hint: string }[] = [
  { view: "shelves", label: "Shelves", hint: "Browse by kind of piece" },
  { view: "duniya", label: "Duniya · Experiences", hint: "Browse by world: festive, desk, memories, play" },
];

/**
 * The Shop's two ways in: Shelves (the catalogue by shelf) and Duniya · Experiences (themes). The choice lives in the
 * URL (`?view=duniya`), so it survives a reload and can be shared.
 */
export function ShopPersonaSwitch({ view }: { view: ShopView }) {
  return (
    <nav aria-label="Browse the Shop by" className="inline-flex flex-wrap gap-1 rounded-pill border border-surface-border bg-surface-card p-1">
      {PERSONAS.map((p) => (
        <Link
          key={p.view}
          href={shopHref(p.view)}
          aria-current={view === p.view ? "page" : undefined}
          title={p.hint}
          className="ak-chip min-h-9 px-4"
        >
          {p.label}
        </Link>
      ))}
    </nav>
  );
}
