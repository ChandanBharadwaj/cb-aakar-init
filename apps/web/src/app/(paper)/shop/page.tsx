import type { Metadata } from "next";
import { Suspense } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { MakeItYours } from "@/lib/catalog";
import { makeItYours, shelvesFromItems, shopItems } from "@/lib/catalog";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { ShopGrid } from "@/components/shop/ShopGrid";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const metadata: Metadata = { title: "Shop" };
// Always render at request time: the catalogue comes from the API, never from a build-time snapshot.
export const dynamic = "force-dynamic";

interface ShopPageProps {
  searchParams: Promise<{ category?: string; from?: string }>;
}

export default async function ShopPage({ searchParams }: ShopPageProps) {
  const { category, from } = await searchParams;
  return (
    <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <div className="mb-6 grid gap-2">
        <h1 className="font-display text-[40px] font-semibold leading-none">Shop</h1>
        <p className="text-sm text-surface-muted">
          {from === "remix"
            ? "Remix starts here: pick a proven piece and press “Modify with AI” to make it yours."
            : "Proven pieces, printed to order in the finish you choose. Every one can be modified."}
        </p>
      </div>
      <Suspense fallback={<BloomLoader size={112} className="py-16" />}>
        <Catalogue category={category} />
      </Suspense>
    </main>
  );
}

/**
 * Streams in after the shell so the heading paints while the API answers. Items are required; the shelves,
 * the template descriptors and the open families (for the "Make it yours" line) are niceties with a fallback.
 */
async function Catalogue({ category }: { category?: string }) {
  const [itemsResult, shelvesResult, templatesResult, familiesResult] = await Promise.allSettled([
    api.catalog.items(),
    api.catalog.shelves(),
    api.templates.list(),
    api.families.list(),
  ]);
  if (itemsResult.status === "rejected") {
    return <ProblemCard problem={toProblem(itemsResult.reason)} title="Couldn't reach the studio" action={{ href: "/shop", label: "Try again" }} />;
  }
  const items = shopItems(itemsResult.value);
  const shelves = shelvesResult.status === "fulfilled" && shelvesResult.value.length > 0 ? shelvesResult.value : shelvesFromItems(items);
  const templates = new Map((templatesResult.status === "fulfilled" ? templatesResult.value : []).map((t) => [t.id, t]));
  // Without the family list the line relies on the template alone; with it, a family that isn't open gets no link.
  const openFamilies =
    familiesResult.status === "fulfilled" ? new Set(familiesResult.value.filter((f) => f.available !== false && f.ready !== false).map((f) => f.id)) : undefined;
  const personalise: Record<string, MakeItYours> = {};
  for (const item of items) {
    const line = makeItYours(item, templates.get(item.template_id), openFamilies);
    if (line) personalise[item.slug] = line;
  }
  return <ShopGrid items={items} shelves={shelves} initialCategory={category} personalise={personalise} />;
}
