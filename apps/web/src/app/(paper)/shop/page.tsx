import type { Metadata } from "next";
import { Suspense } from "react";
import { api, toProblem } from "@/lib/api/client";
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

/** Streams in after the shell so the heading paints while the API answers. */
async function Catalogue({ category }: { category?: string }) {
  try {
    const items = await api.catalog.items();
    return <ShopGrid items={items} initialCategory={category} />;
  } catch (err) {
    return <ProblemCard problem={toProblem(err)} title="Couldn't reach the studio" action={{ href: "/shop", label: "Try again" }} />;
  }
}
