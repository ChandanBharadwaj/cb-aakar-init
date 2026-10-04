import type { Metadata } from "next";
import { Suspense } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Experience } from "@/lib/api/types";
import type { MakeItYours } from "@/lib/catalog";
import { makeItYours, shelvesFromItems, shopItems } from "@/lib/catalog";
import { shopHref, shopView, studioToday } from "@/lib/experiences";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { ExperienceCard } from "@/components/duniya/ExperienceCard";
import { ShopGrid } from "@/components/shop/ShopGrid";
import { ShopPersonaSwitch } from "@/components/shop/ShopPersonaSwitch";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const metadata: Metadata = { title: "Shop" };
// Always render at request time: the catalogue comes from the API, never from a build-time snapshot.
export const dynamic = "force-dynamic";

interface ShopPageProps {
  /** `view=duniya` opens the Duniya · Experiences persona; `category` preselects a shelf; `from=remix` changes the intro. */
  searchParams: Promise<{ category?: string; from?: string; view?: string }>;
}

export default async function ShopPage({ searchParams }: ShopPageProps) {
  const { category, from, view: viewParam } = await searchParams;
  const view = shopView(viewParam);
  return (
    <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 pb-16 pt-4 sm:px-8 lg:px-11">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div className="grid gap-2">
          <h1 className="font-display text-[40px] font-semibold leading-none">Shop</h1>
          <p className="max-w-prose text-sm text-surface-muted">
            {view === "duniya"
              ? "Browse by world: each Duniya brings its own backdrop, motifs and the Avatars that suit it."
              : from === "remix"
                ? "Remix starts here: pick a proven piece and press “Modify with AI” to make it yours."
                : "Proven pieces, printed to order in the finish you choose. Every one can be modified."}
          </p>
        </div>
        <ShopPersonaSwitch view={view} />
      </div>
      <Suspense key={view} fallback={<BloomLoader size={112} className="py-16" />}>
        {view === "duniya" ? <Worlds /> : <Catalogue category={category} />}
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

/** Duniya · Experiences: one card per open experience, in the API's order (`sort_order`). */
async function Worlds() {
  let experiences: Experience[];
  try {
    experiences = await api.experiences.list();
  } catch (err) {
    return <ProblemCard problem={toProblem(err)} title="Couldn't reach the studio" action={{ href: shopHref("duniya"), label: "Try again" }} />;
  }
  if (experiences.length === 0) {
    return <p className="ak-card p-6 text-sm text-surface-muted">No worlds are open right now. The shelves are, and new Duniya land every few weeks.</p>;
  }
  const today = studioToday();
  return (
    <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3" aria-label="Duniya · Experiences">
      {experiences.map((experience) => (
        <li key={experience.id} className="min-h-0">
          <ExperienceCard experience={experience} today={today} />
        </li>
      ))}
    </ul>
  );
}
