"use client";

import Link from "next/link";
import { formatPaise } from "@aakar/design-tokens";
import type { Family } from "@/lib/api/types";
import { envelopeLine, hardwareSentence, isRawFamily, priceFromLabel, sortFamilies } from "@/lib/families";
import { familyTakes, featurePhrase } from "@/lib/features";

export interface FamilyPickerProps {
  families: Family[];
  /** The customer's words from the prompt bar; they travel to the composer as the piece's working title. */
  prompt?: string;
  className?: string;
}

/**
 * "Give your idea an Avatar": one card per outcome family from `GET /api/families` — codename large with its
 * plain name, tagline, what comes in the box, how big it gets, what its live templates take today ("Takes
 * text (Naam) · photo relief (Chhavi)"; Buti only once motifs are live) — and a quiet "Swaroop · Print as it is"
 * card last. Cards open `/create/[family]`.
 */
export function FamilyPicker({ families, prompt, className }: FamilyPickerProps) {
  const sorted = sortFamilies(families.filter((f) => f.available !== false));
  const avatars = sorted.filter((f) => !isRawFamily(f));
  const raw = sorted.find((f) => isRawFamily(f));
  const href = (f: Family) => `/create/${encodeURIComponent(f.id)}${prompt ? `?prompt=${encodeURIComponent(prompt)}` : ""}`;

  if (avatars.length === 0 && !raw) {
    return <p className={["ak-card p-6 text-sm text-surface-muted", className].filter(Boolean).join(" ")}>Nothing is open to create just yet. New forms land every few weeks.</p>;
  }

  return (
    <div className={["grid gap-4", className].filter(Boolean).join(" ")}>
      {avatars.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="Avatars · forms your idea can take">
          {avatars.map((f) => (
            <li key={f.id} className="min-h-0">
              <AvatarCard family={f} href={href(f)} />
            </li>
          ))}
        </ul>
      )}
      {raw && <RawCard family={raw} href={href(raw)} />}
    </div>
  );
}

function AvatarCard({ family, href }: { family: Family; href: string }) {
  const ready = family.ready !== false && family.templates.length > 0;
  const lines = [hardwareSentence(family.hardware), envelopeLine(family)].filter((x): x is string => Boolean(x));
  // Only what the live templates take today, never the family's aspirational content_slot.accepts.
  const takes = familyTakes(family).map(featurePhrase).join(" · ");
  const from = priceFromLabel(family, formatPaise);
  const body = (
    <>
      <div className="flex items-baseline justify-between gap-3">
        {family.kind === "object" && <span className="ak-label">Object</span>}
        {from && <span className="ml-auto text-[11px] font-semibold text-surface-muted">{from}</span>}
      </div>
      <div className="grid gap-1">
        {/* The codename never travels alone: the plain name is part of the heading (and the card's accessible name). */}
        <h2 id={`avatar-${family.id}`} className="grid gap-1">
          <span className="font-display text-3xl font-semibold leading-none group-hover:text-surface-accent">{family.codename}</span>
          <span className="sr-only"> · </span>
          <span className="text-sm font-semibold">{family.name}</span>
        </h2>
        {family.tagline && <p className="text-sm text-surface-muted">{family.tagline}</p>}
      </div>
      <ul className="grid gap-1 text-[12px] text-surface-muted">
        {lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
        {takes && <li>Takes {takes}</li>}
      </ul>
    </>
  );
  const className = "ak-card group relative grid h-full content-start gap-3 overflow-hidden p-5 transition-all duration-base ease-ak";
  if (!ready) {
    return (
      <article className={`${className} opacity-80`} aria-labelledby={`avatar-${family.id}`}>
        <span className="ak-ribbon">Coming soon</span>
        {body}
      </article>
    );
  }
  return (
    <Link href={href} className={`${className} hover:-translate-y-0.5 hover:shadow-float`} aria-labelledby={`avatar-${family.id}`}>
      {body}
    </Link>
  );
}

function RawCard({ family, href }: { family: Family; href: string }) {
  const envelope = envelopeLine(family);
  return (
    <Link
      href={href}
      className="ak-card flex flex-wrap items-center justify-between gap-4 border-dashed p-5 opacity-90 transition-all duration-base ease-ak hover:opacity-100 hover:shadow-float"
      aria-labelledby={`avatar-${family.id}`}
    >
      <div className="grid gap-1">
        <span className="ak-label">Already have a model file?</span>
        <h2 id={`avatar-${family.id}`} className="font-display text-2xl font-semibold leading-none">
          {family.codename} <span className="text-surface-muted">· {family.name}</span>
        </h2>
        <p className="text-sm text-surface-muted">Upload the file from your 3D program (.stl, .obj, .3mf). We check it, size it and print it as it is.</p>
      </div>
      <div className="grid justify-items-end gap-1 text-right text-[12px] text-surface-muted">
        {envelope && <span>{envelope}</span>}
        <span className="ak-btn ak-btn-secondary ak-btn-pill min-h-9 px-4 py-1.5 text-xs">Upload a file</span>
      </div>
    </Link>
  );
}
