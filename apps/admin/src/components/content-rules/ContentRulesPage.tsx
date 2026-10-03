"use client";

import Link from "next/link";
import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { ContentTerm, ContentTermKind, Problem } from "@/lib/api/types";
import { KIND_TONE, REFUSAL_COPY, TERM_KINDS, WHOLE_WORD_MAX, contentTermInput, kindLabel, normaliseTerm, sortTerms } from "@/lib/contentTerms";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { ContentTermDrawer } from "./ContentTermDrawer";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

type KindFilter = ContentTermKind | "all";

/** Content rules: the names the studio won't print (the trademark guardrail behind Katha). */
export function ContentRulesPage() {
  const canWrite = useCanWrite();
  const { data, problem, reload, setData } = useQuery(() => api.contentTerms.list(), "content-terms");
  const [kind, setKind] = useState<KindFilter>("all");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<ContentTerm | null | undefined>(undefined);
  const [toggling, setToggling] = useState<string>();
  const [writeProblem, setWriteProblem] = useState<Problem>();

  function saved(t: ContentTerm) {
    setData((prev) => {
      const list = prev ?? [];
      return sortTerms(list.some((x) => x.id === t.id) ? list.map((x) => (x.id === t.id ? t : x)) : [...list, t]);
    });
    setEditing(undefined);
  }

  async function toggleActive(t: ContentTerm, active: boolean) {
    setToggling(t.id);
    setWriteProblem(undefined);
    try {
      saved(await api.contentTerms.update(t.id, { ...contentTermInput(t), active }));
    } catch (err) {
      setWriteProblem(toProblem(err));
    } finally {
      setToggling(undefined);
    }
  }

  const all = data ? sortTerms(data) : undefined;
  const needle = normaliseTerm(query);
  const words = query.trim().toLowerCase();
  const rows = all?.filter(
    (t) => (kind === "all" || t.kind === kind) && (!words || (needle !== "" && t.normalised_term.includes(needle)) || (t.reason ?? "").toLowerCase().includes(words)),
  );
  const count = (k: KindFilter) => all?.filter((t) => k === "all" || t.kind === k).length ?? 0;
  const off = all?.filter((t) => !t.active).length ?? 0;

  return (
    <>
      <PageHeader
        eyebrow="Content"
        title="Content rules"
        description="Names the studio won't print: licensed publishers, brands and characters. Katha (Comics & heroes) prints the customer's own hero; these rules keep everyone else's off our pieces. Customers never see this list."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite || !data} title={canWrite ? undefined : "Owner only"}>
            Add term
          </button>
        }
      />
      <RulesExplainer />
      <OwnerOnlyHint what="Content rule changes" className="mb-4" />
      {writeProblem && <ProblemCard compact problem={writeProblem} className="mb-4" title={writeProblem.code === "forbidden" ? "Owner only" : undefined} />}
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !all || !rows ? (
        <Loading />
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <div className="flex flex-wrap gap-2" role="group" aria-label="Filter rules by kind">
              <button type="button" className="ak-chip" aria-pressed={kind === "all"} onClick={() => setKind("all")}>
                All · {count("all")}
              </button>
              {TERM_KINDS.map((k) => (
                <button key={k.id} type="button" className="ak-chip" aria-pressed={kind === k.id} onClick={() => setKind(k.id)} title={k.hint}>
                  {k.plural} · {count(k.id)}
                </button>
              ))}
            </div>
            <input
              type="search"
              className="ak-input ak-input-sm max-w-xs"
              placeholder="Find a term or reason"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Find a content rule"
            />
            {off > 0 && <span className="text-xs text-surface-muted">{off} switched off</span>}
          </div>
          {all.length === 0 ? (
            <EmptyState title="No content rules yet">Add the publishers, brands and characters the studio won&apos;t print; uploads and names are checked against them at once.</EmptyState>
          ) : rows.length === 0 ? (
            <EmptyState title="No rule matches">Try another spelling (case, spaces and punctuation don&apos;t matter) or another kind.</EmptyState>
          ) : (
            <div className="ak-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="ak-table">
                  <thead>
                    <tr>
                      <th>Term</th>
                      <th>Kind</th>
                      <th>Matches</th>
                      <th>Reason</th>
                      <th>Active</th>
                      <th>Updated</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((t) => (
                      <tr key={t.id} className={t.active ? "" : "opacity-60"}>
                        <td className="font-medium">{t.term}</td>
                        <td>
                          <Pill tone={KIND_TONE[t.kind]}>{kindLabel(t.kind)}</Pill>
                        </td>
                        <td>
                          <div className="grid gap-0.5">
                            <span className="font-mono text-[11px]">{t.normalised_term}</span>
                            <span className="text-[11px] text-surface-muted">{t.whole_word ? "Whole word only" : "Anywhere in a name"}</span>
                          </div>
                        </td>
                        <td className="max-w-xs text-xs text-surface-muted">{t.reason || "—"}</td>
                        <td>
                          <div className="flex items-center gap-2">
                            <Switch label={`${t.term} active`} checked={t.active} onChange={(v) => void toggleActive(t, v)} disabled={!canWrite} busy={toggling === t.id} />
                            {t.active ? <Pill tone="success">On</Pill> : <Pill>Off</Pill>}
                          </div>
                        </td>
                        <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(t.updated_at)}</td>
                        <td>
                          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(t)}>
                            {canWrite ? "Edit" : "View"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
      <ContentTermDrawer term={editing} terms={data ?? []} onClose={() => setEditing(undefined)} onSaved={saved} />
    </>
  );
}

/** What the rules do, in four short paragraphs. */
function RulesExplainer() {
  return (
    <section className="ak-card mb-5 grid gap-4 p-5 text-sm sm:grid-cols-2" aria-label="What the rules do">
      <div className="grid gap-1">
        <h2 className="ak-label">Uploads wait for a reviewer</h2>
        <p className="text-surface-muted">
          A photo or model file whose file name mentions an active term is held in{" "}
          <Link href="/reviews" className="text-surface-accent hover:underline">
            Reviews
          </Link>
          , with the term in the reason. Approve the customer&apos;s own work; reject the rest.
        </p>
      </div>
      <div className="grid gap-1">
        <h2 className="ak-label">Names are refused</h2>
        <p className="text-surface-muted">A text (Naam) that mentions one can&apos;t be ordered. The customer reads: &ldquo;{REFUSAL_COPY}&rdquo;</p>
      </div>
      <div className="grid gap-1">
        <h2 className="ak-label">Spelling doesn&apos;t hide a term</h2>
        <p className="text-surface-muted">
          Case, spaces, punctuation and accents never count: Iron Man, Iron-Man and IRONMAN are one term. Terms of {WHOLE_WORD_MAX} letters or fewer must
          stand as a whole word, so DC doesn&apos;t catch Adcock and Thor doesn&apos;t catch Thorat.
        </p>
      </div>
      <div className="grid gap-1">
        <h2 className="ak-label">Switch off, never delete</h2>
        <p className="text-surface-muted">
          Switching a term off takes effect at once. Terms are never renamed or deleted, so the audit trail stays readable: add a new spelling as its own
          rule. Licensed collections come later, only with an agreement.
        </p>
      </div>
    </section>
  );
}
