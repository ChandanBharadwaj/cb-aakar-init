import type { Metadata } from "next";
import Link from "next/link";
import { api, toProblem } from "@/lib/api/client";
import type { Family, Problem, TemplateDescriptor } from "@/lib/api/types";
import { environmentLabel } from "@/lib/viewer/environments";
import { BloomMark } from "@/components/brand/BloomMark";
import { FamilyPicker } from "@/components/design/FamilyPicker";
import { StageNav } from "@/components/nav/StageNav";
import { PromptBar } from "@/components/ui/PromptBar";
import { ProblemCard } from "@/components/ui/ProblemCard";

export const metadata: Metadata = { title: "Create" };
export const dynamic = "force-dynamic";

interface CreatePageProps {
  searchParams: Promise<{ prompt?: string }>;
}

export default async function CreatePage({ searchParams }: CreatePageProps) {
  const { prompt } = await searchParams;
  const [familiesResult, templatesResult] = await Promise.allSettled([api.families.list(), api.templates.list()]);
  const families: Family[] = familiesResult.status === "fulfilled" ? familiesResult.value : [];
  const problem: Problem | undefined = familiesResult.status === "rejected" ? toProblem(familiesResult.reason) : undefined;
  const templates: TemplateDescriptor[] = templatesResult.status === "fulfilled" ? templatesResult.value : [];
  // Templates no listed Avatar covers keep the direct "start from a template" path.
  const covered = new Set(families.flatMap((f) => f.templates.map((t) => t.id)));
  const extraTemplates = templates.filter((t) => !covered.has(t.id));

  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav section="Create" backHref="/" backLabel="Home" />
      <main className="mx-auto grid w-full max-w-[1100px] flex-1 content-start gap-8 px-4 py-8 sm:px-8">
        <div className="ak-card grid gap-5 p-6 sm:p-8">
          <div className="flex items-center gap-3">
            <BloomMark size={36} />
            <div className="ak-eyebrow">Create</div>
          </div>
          {prompt ? (
            <blockquote className="ak-well border-l-2 border-surface-accent p-4 font-display text-xl italic">“{prompt}”</blockquote>
          ) : (
            <PromptBar placeholder="What do you want to create today?" buttonLabel="Keep this idea" />
          )}
          <div className="grid gap-1">
            <h1 className="font-display text-3xl font-semibold leading-tight">Give your idea an Avatar.</h1>
            <p className="text-surface-muted">
              Pick the form it takes — a keychain, a magnet, a nameplate — then add your Chhaap: a name, a photo or your own 3D form. Describing a piece in words
              arrives with the co-designer in Phase 2{prompt ? "; until then your words travel with the Avatar you pick" : ""}.
            </p>
          </div>
        </div>

        {problem ? (
          <ProblemCard problem={problem} title="Couldn't reach the studio" action={{ href: "/shop", label: "Browse the Shop instead" }} />
        ) : (
          <FamilyPicker families={families} prompt={prompt} />
        )}

        {extraTemplates.length > 0 && (
          <section className="grid gap-4" aria-labelledby="templates-heading">
            <h2 id="templates-heading" className="ak-label">
              Or start from a template
            </h2>
            <ul className="grid gap-4 sm:grid-cols-2" aria-label="Templates">
              {extraTemplates.map((t) => {
                const params = Object.keys(t.params).length;
                return (
                  <li key={t.id} className="ak-card grid gap-3 p-5">
                    <div className="grid gap-1">
                      <span className="ak-label">{t.family.replace(/_/g, " ")}</span>
                      <h3 className="font-display text-2xl font-semibold leading-tight">{t.name}</h3>
                      {t.description && <p className="text-sm text-surface-muted">{t.description}</p>}
                    </div>
                    <p className="text-[11px] text-surface-muted">
                      {params} {params === 1 ? "control" : "controls"} · {t.materials.length} finishes · {environmentLabel(t.environment)}
                    </p>
                    <div>
                      <Link href={`/design/new?template=${encodeURIComponent(t.id)}`} className="ak-btn ak-btn-primary ak-btn-pill px-6">
                        Start
                      </Link>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </main>
    </div>
  );
}
