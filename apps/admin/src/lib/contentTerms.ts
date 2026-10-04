// Vocabulary for the content rules: the names the studio won't print, the trademark guardrail behind Katha
// (docs/research/outcome-categories/implementation-plan.md §8, open decision 16). The matching preview mirrors the API's
// TermMatcher (services/api …/media/internal/TermMatcher.java); the API's `normalised_term` and `whole_word` always win.
import type { ContentTerm, ContentTermInput, ContentTermKind } from "@/lib/api/types";
import type { Tone } from "@/lib/orders";

export const TERM_KINDS: readonly { id: ContentTermKind; label: string; plural: string; hint: string }[] = [
  { id: "trademark", label: "Trademark", plural: "Trademarks", hint: "A publisher or brand: Marvel, DC Comics" },
  { id: "character", label: "Character", plural: "Characters", hint: "A licensed hero or villain: Spider-Man, Batman, Nagraj" },
  { id: "other", label: "Other", plural: "Other", hint: "Anything else the studio won't print: a logo's name, a slogan" },
];

export const KIND_TONE: Record<ContentTermKind, Tone> = { trademark: "accent", character: "info", other: "neutral" };

export function kindLabel(kind: ContentTermKind): string {
  return TERM_KINDS.find((k) => k.id === kind)?.label ?? kind;
}

/** Terms of this many letters and digits or fewer only match as a whole word (the API's TermMatcher.WHOLE_WORD_MAX). */
export const WHOLE_WORD_MAX = 6;

/** What a customer reads when a name is refused (422 `protected_term`); the term itself is never shown to them. */
export const REFUSAL_COPY = "We can't print copyrighted heroes or their names, but your own hero is welcome. Try your own hero's name.";

/** How the API compares a term: accents and full-width letters folded, lowercase, letters and digits only ("Spider-Man" → "spiderman"). */
export function normaliseTerm(term: string): string {
  return term.normalize("NFKD").toLowerCase().replace(/[^\p{L}\p{Nd}]/gu, "");
}

/** Whether a normalised term only matches as a whole word of a name or file name. */
export function isWholeWord(normalised: string): boolean {
  return [...normalised].length <= WHOLE_WORD_MAX;
}

/** Row → PUT body: the read-only fields never travel back; the term is sent as stored. */
export function contentTermInput(t: ContentTerm): ContentTermInput {
  return { term: t.term, kind: t.kind, reason: t.reason ?? null, active: t.active };
}

/** By normalised term, as the API lists them. */
export function sortTerms<T extends Pick<ContentTerm, "normalised_term">>(list: readonly T[]): T[] {
  return [...list].sort((a, b) => (a.normalised_term < b.normalised_term ? -1 : a.normalised_term > b.normalised_term ? 1 : 0));
}
