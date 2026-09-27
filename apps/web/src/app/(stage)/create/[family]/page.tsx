import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { api, isApiError, toProblem } from "@/lib/api/client";
import type { CatalogItem, Family, Material } from "@/lib/api/types";
import { isRawFamily } from "@/lib/families";
import { familyLabel } from "@/lib/features";
import { ContentComposer } from "@/components/design/ContentComposer";
import { RawPrintComposer } from "@/components/design/RawPrintComposer";
import { StageNav } from "@/components/nav/StageNav";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const dynamic = "force-dynamic";

interface FamilyCreatePageProps {
  params: Promise<{ family: string }>;
  /** `item`: a Shop slug whose template and defaults seed the composer ("Make it yours"); `prompt`: the customer's words. */
  searchParams: Promise<{ item?: string; prompt?: string }>;
}

export async function generateMetadata({ params }: FamilyCreatePageProps): Promise<Metadata> {
  const { family } = await params;
  try {
    return { title: familyLabel(await api.families.get(family)) };
  } catch {
    return { title: "Create" };
  }
}

/** Finishes and the Shop item are niceties; the composer falls back to the token materials without them. */
async function extras(family: Family, itemSlug: string | undefined): Promise<{ materials: Material[]; item?: CatalogItem }> {
  const [materials, item] = await Promise.allSettled([api.catalog.materials(), itemSlug ? api.catalog.item(itemSlug) : Promise.resolve(undefined)]);
  const candidate = item.status === "fulfilled" ? item.value : undefined;
  return {
    materials: materials.status === "fulfilled" ? materials.value : [],
    // Only an item of this family, whose template the family lists, can seed the composer.
    item: candidate && (candidate.family_id === family.id || family.templates.some((t) => t.id === candidate.template_id)) ? candidate : undefined,
  };
}

export default async function FamilyCreatePage({ params, searchParams }: FamilyCreatePageProps) {
  const [{ family: id }, { item: itemSlug, prompt }] = await Promise.all([params, searchParams]);
  let family: Family;
  try {
    family = await api.families.get(id);
  } catch (err) {
    if (isApiError(err) && err.status === 404) notFound();
    return (
      <Shell>
        <div className="mx-auto w-full max-w-lg">
          <ProblemCard problem={toProblem(err)} title="Couldn't reach the studio" action={{ href: "/create", label: "Back to Create" }} />
        </div>
      </Shell>
    );
  }
  const { materials, item } = await extras(family, itemSlug);
  return (
    <Shell>
      {isRawFamily(family) ? (
        <RawPrintComposer family={family} materials={materials} prompt={prompt} />
      ) : (
        <ContentComposer family={family} materials={materials} item={item} prompt={prompt} />
      )}
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav section="Create" backHref="/create" backLabel="Back to Create" />
      <main className="mx-auto grid w-full max-w-[1100px] flex-1 content-start gap-6 px-4 py-8 sm:px-8">{children}</main>
    </div>
  );
}
