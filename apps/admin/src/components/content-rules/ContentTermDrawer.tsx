"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { ContentTerm, ContentTermInput, ContentTermKind, Problem } from "@/lib/api/types";
import { TERM_KINDS, WHOLE_WORD_MAX, isWholeWord, normaliseTerm } from "@/lib/contentTerms";
import { useCanWrite } from "@/store/session";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Switch } from "@/components/ui/Switch";

interface Draft {
  term: string;
  kind: ContentTermKind;
  reason: string;
  active: boolean;
}

function fromTerm(t: ContentTerm | null): Draft {
  if (!t) return { term: "", kind: "character", reason: "", active: true };
  return { term: t.term, kind: t.kind, reason: t.reason ?? "", active: t.active };
}

function fromDraft(d: Draft): ContentTermInput {
  return { term: d.term.trim(), kind: d.kind, reason: d.reason.trim() || null, active: d.active };
}

const PROBLEM_TITLES: Record<string, string> = {
  content_term_exists: "Already a rule",
  validation_failed: "The API rejected a field",
  forbidden: "Owner only",
};

export interface ContentTermDrawerProps {
  /** Existing rule to edit, `null` for "Add term", `undefined` when closed. */
  term: ContentTerm | null | undefined;
  /** Every rule, so a second spelling of an existing one is caught before it is sent. */
  terms: readonly ContentTerm[];
  onClose(): void;
  onSaved(term: ContentTerm): void;
}

export function ContentTermDrawer({ term, terms, onClose, onSaved }: ContentTermDrawerProps) {
  const open = term !== undefined;
  return (
    <Drawer open={open} onClose={onClose} title={term ? term.term : "Add a content rule"} eyebrow={term ? `Content rule · ${term.normalised_term}` : "New content rule"}>
      {open && <ContentTermForm key={term?.id ?? "new"} term={term} terms={terms} onClose={onClose} onSaved={onSaved} />}
    </Drawer>
  );
}

function ContentTermForm({ term, terms, onClose, onSaved }: { term: ContentTerm | null; terms: readonly ContentTerm[]; onClose(): void; onSaved(t: ContentTerm): void }) {
  const canWrite = useCanWrite();
  const editing = term !== null;
  const [draft, setDraft] = useState<Draft>(() => fromTerm(term));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const dis = busy || !canWrite;

  // The API's own values for a stored rule; a preview of its rule while a new one is typed
  const normalised = editing ? term.normalised_term : normaliseTerm(draft.term);
  const wholeWord = editing ? term.whole_word : isWholeWord(normalised);
  const twin = editing || !normalised ? undefined : terms.find((t) => t.normalised_term === normalised);
  const tooShort = !editing && draft.term.trim() !== "" && [...normalised].length < 2;
  const termError = twin
    ? `Same letters and digits as “${twin.term}”${twin.active ? "" : ", which is switched off"}: edit that one instead`
    : tooShort
      ? "A rule needs at least two letters or digits"
      : undefined;
  const termHint = !normalised
    ? "As people write it, e.g. Spider-Man. Case, spaces, punctuation and accents never count"
    : `Matches as “${normalised}” · ${wholeWord ? `only as a whole word (${WHOLE_WORD_MAX} letters or fewer)` : "anywhere, even run into other words"}${editing ? ". Fixed once created: add a new spelling as its own rule" : ""}`;
  const kindInfo = TERM_KINDS.find((k) => k.id === draft.kind);

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || termError) return;
    setBusy(true);
    setProblem(undefined);
    const body = fromDraft(draft);
    try {
      onSaved(editing ? await api.contentTerms.update(term.id, body) : await api.contentTerms.create(body));
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-5" aria-busy={busy}>
      <OwnerOnlyHint what="Content rule changes" />
      <div className="grid gap-4">
        <Field label="Term" hint={termHint} error={termError}>
          {(id) => (
            <input
              id={id}
              className="ak-input ak-input-sm"
              maxLength={80}
              value={draft.term}
              onChange={(e) => set("term", e.target.value)}
              disabled={dis || editing}
              required
              placeholder="Spider-Man"
              autoComplete="off"
            />
          )}
        </Field>
        <Field label="Kind" hint={kindInfo?.hint}>
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.kind} onChange={(e) => set("kind", e.target.value as ContentTermKind)} disabled={dis}>
              {TERM_KINDS.map((k) => (
                <option key={k.id} value={k.id}>
                  {k.label}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Reason" hint="Why it is protected, e.g. Marvel character (Disney). Reviewers see it beside a held upload; customers never do (up to 200)">
          {(id) => <textarea id={id} className="ak-input" rows={2} maxLength={200} value={draft.reason} onChange={(e) => set("reason", e.target.value)} disabled={dis} />}
        </Field>
        <Field label="Active" hint="Off keeps the rule but stops it holding uploads and refusing names, at once" inline>
          {(id) => <Switch id={id} label="Active" checked={draft.active} onChange={(v) => set("active", v)} disabled={dis} />}
        </Field>
      </div>
      {problem && <ProblemCard compact problem={problem} title={PROBLEM_TITLES[problem.code ?? ""]} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || Boolean(termError)} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save rule" : "Add rule"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}
