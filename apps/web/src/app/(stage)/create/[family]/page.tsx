import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { api, isApiError, toProblem } from "@/lib/api/client";
import type { CatalogItem, Family, Material } from "@/lib/api/types";
import { duniyaPreset, experienceLabel, type DuniyaPreset } from "@/lib/experiences";
import { isRawFamily } from "@/lib/families";
import { familyLabel } from "@/lib/features";
import { ContentComposer } from "@/components/design/ContentComposer";
import { RawPrintComposer } from "@/components/design/RawPrintComposer";
import { StageNav } from "@/components/nav/StageNav";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const dynamic = "force-dynamic";

interface FamilyCreatePageProps {
  params: Promise<{ family: string }>;
  /**
   * `item`: a Shop slug whose template and defaults seed the composer ("Make it yours"); `prompt`: the customer's words;
   * `duniya`: the slug of the Duniya experience the customer came from (its backdrop, style and motif pack preset).
   */
  searchParams: Promise<{ item?: string; prompt?: string; duniya?: string }>;
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

/**
 * `?duniya=<slug>` as presets. A slug the API doesn't know, an experience that isn't open, or an API that doesn't
 * answer all leave the composer as it is: a theme is a nicety, never a reason to fail.
 */
async function duniyaFor(slug: string | undefined): Promise<DuniyaPreset | undefined> {
  if (!slug) return undefined;
  const [experience, environments] = await Promise.allSettled([api.experiences.get(slug), api.environments.list()]);
  if (experience.status !== "fulfilled") return undefined;
  return duniyaPreset(experience.value, environments.status === "fulfilled" ? environments.value : undefined);
}

export default async function FamilyCreatePage({ params, searchParams }: FamilyCreatePageProps) {
  const [{ family: id }, { item: itemSlug, prompt, duniya: duniyaSlug }] = await Promise.all([params, searchParams]);
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
  const [{ materials, item }, duniya] = await Promise.all([extras(family, itemSlug), isRawFamily(family) ? undefined : duniyaFor(duniyaSlug)]);
  return (
    <Shell duniya={duniya}>
      {isRawFamily(family) ? (
        <RawPrintComposer family={family} materials={materials} prompt={prompt} />
      ) : (
        <ContentComposer family={family} materials={materials} item={item} prompt={prompt} duniya={duniya} />
      )}
    </Shell>
  );
}

/** Back goes to the Duniya page the customer came from, else to the Create picker. */
function Shell({ children, duniya }: { children: React.ReactNode; duniya?: DuniyaPreset }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav
        section="Create"
        backHref={duniya ? `/duniya/${encodeURIComponent(duniya.slug)}` : "/create"}
        backLabel={duniya ? `Back to ${experienceLabel(duniya)}` : "Back to Create"}
      />
      <main className="mx-auto grid w-full max-w-[1100px] flex-1 content-start gap-6 px-4 py-8 sm:px-8">{children}</main>
    </div>
  );
}
