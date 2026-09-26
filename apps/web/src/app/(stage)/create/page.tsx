import type { Metadata } from "next";
import Link from "next/link";
import { api, toProblem } from "@/lib/api/client";
import type { Problem, TemplateDescriptor } from "@/lib/api/types";
import { environmentLabel } from "@/lib/viewer/environments";
import { BloomMark } from "@/components/brand/BloomMark";
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
  let templates: TemplateDescriptor[] = [];
  let problem: Problem | undefined;
  try {
    templates = await api.templates.list();
  } catch (err) {
    problem = toProblem(err);
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav section="Create" backHref="/" backLabel="Home" />
      <main className="mx-auto grid w-full max-w-[980px] flex-1 content-start gap-8 px-4 py-8 sm:px-8">
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
            <h1 className="font-display text-3xl font-semibold leading-tight">Describing a piece in words arrives in Phase 2.</h1>
            <p className="text-surface-muted">Until then, start from a template: pick one below and shape it with sliders and finishes in the studio.</p>
          </div>
        </div>

        {problem ? (
          <ProblemCard problem={problem} title="Couldn't reach the studio" action={{ href: "/shop", label: "Browse the Shop instead" }} />
        ) : templates.length === 0 ? (
          <p className="ak-card p-6 text-sm text-surface-muted">No templates are published yet.</p>
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2" aria-label="Templates">
            {templates.map((t) => {
              const params = Object.keys(t.params).length;
              return (
                <li key={t.id} className="ak-card grid gap-3 p-5">
                  <div className="grid gap-1">
                    <span className="ak-label">{t.family.replace(/_/g, " ")}</span>
                    <h2 className="font-display text-2xl font-semibold leading-tight">{t.name}</h2>
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
        )}
      </main>
    </div>
  );
}
