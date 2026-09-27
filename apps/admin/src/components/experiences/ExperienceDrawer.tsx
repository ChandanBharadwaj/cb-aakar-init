"use client";

import { useRef, useState } from "react";
import { api, apiUrl, toProblem } from "@/lib/api/client";
import type { AdminExperience, AdminExperienceInput, AdminFamily, CatalogItem, Environment, ExperienceStyle, Motif, Problem } from "@/lib/api/types";
import {
  ACCENTS,
  HEX,
  MONTHS,
  STYLES,
  TINTS,
  experienceTitle,
  formatWindow,
  inSeason,
  monthDay,
  packEntry,
  paletteColour,
  parseMonthDay,
  windowProblem,
  type PaletteColour,
} from "@/lib/experiences";
import { familyTitle, sortFamilies } from "@/lib/families";
import { useCanWrite } from "@/store/session";
import { EnvironmentPicker } from "@/components/environments/Backdrops";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

const ID_RE = /^[a-z][a-z0-9_]*$/;
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const MAX_LIST = 24;
const MAX_SEASONS = 12;

interface SeasonDraft {
  key: number;
  label: string;
  /** Recurring every year (month-days) or a one-off (two dates). */
  yearly: boolean;
  starts_on: string;
  ends_on: string;
}

interface CollectionDraft {
  key: number;
  id: string;
  title: string;
  licence_ref: string;
}

/** Form state: blank-able text stays a string and is trimmed or dropped on submit. */
interface Draft {
  id: string;
  codename: string;
  slug: string;
  title: string;
  tagline: string;
  description: string;
  environment: string;
  accent: string;
  /** "" keeps plain cream. */
  paper_tint: string;
  /** "" until a hero is shot. */
  hero_media: string;
  style: ExperienceStyle;
  motif_pack: string[];
  avatars: string[];
  items: string[];
  collections: CollectionDraft[];
  season: SeasonDraft[];
  available: boolean;
  sort_order: string;
}

let rowKey = 0;
const nextKey = () => ++rowKey;

function fromExperience(e: AdminExperience | null, environments: Environment[]): Draft {
  if (!e) {
    return {
      id: "",
      codename: "",
      slug: "",
      title: "",
      tagline: "",
      description: "",
      environment: environments.find((x) => x.id === "studio")?.id ?? environments[0]?.id ?? "studio",
      accent: ACCENTS.find((c) => c.token === "terracotta")?.hex ?? "#B56E52",
      paper_tint: "",
      hero_media: "",
      style: "none",
      motif_pack: [],
      avatars: [],
      items: [],
      collections: [],
      season: [],
      available: false,
      sort_order: "100",
    };
  }
  return {
    id: e.id,
    codename: e.codename,
    slug: e.slug,
    title: e.title,
    tagline: e.tagline ?? "",
    description: e.description ?? "",
    environment: e.environment,
    accent: e.surface.accent,
    paper_tint: e.surface.paper_tint ?? "",
    hero_media: e.surface.hero_media ?? "",
    style: e.style ?? "none",
    motif_pack: [...(e.motif_pack ?? [])],
    avatars: [...e.avatars],
    items: [...(e.items ?? [])],
    collections: (e.collections ?? []).map((c) => ({ key: nextKey(), id: c.id, title: c.title, licence_ref: c.licence_ref ?? "" })),
    season: (e.season ?? []).map((w) => ({ key: nextKey(), label: w.label, yearly: w.starts_on.startsWith("--"), starts_on: w.starts_on, ends_on: w.ends_on })),
    available: e.available,
    sort_order: String(e.sort_order),
  };
}

const int = (s: string): number | undefined => {
  const n = Number.parseInt(s, 10);
  return Number.isFinite(n) ? n : undefined;
};

/** Draft → request body. Empty optional copy is dropped; a missing hero or tint is sent as null. */
function toInput(d: Draft): AdminExperienceInput {
  const body: AdminExperienceInput = {
    id: d.id.trim(),
    codename: d.codename.trim(),
    slug: d.slug.trim(),
    title: d.title.trim(),
    environment: d.environment,
    surface: { accent: d.accent, paper_tint: d.paper_tint || null, hero_media: d.hero_media.trim() || null },
    style: d.style,
    motif_pack: [...d.motif_pack],
    avatars: [...d.avatars],
    items: [...d.items],
    collections: d.collections.map((c) => ({ id: c.id.trim(), title: c.title.trim(), licence_ref: c.licence_ref.trim() || null })),
    season: d.season.map((w) => ({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label.trim() })),
    available: d.available,
    sort_order: int(d.sort_order) ?? 100,
  };
  if (d.tagline.trim()) body.tagline = d.tagline.trim();
  if (d.description.trim()) body.description = d.description.trim();
  return body;
}

const PROBLEM_TITLES: Record<string, string> = {
  experience_exists: "That id is taken",
  slug_exists: "That slug is taken",
  unknown_family: "An Avatar isn't a family the API knows",
  unknown_experience: "This Duniya no longer exists on the API",
  validation_failed: "The API rejected a field",
  forbidden: "Owner only",
};

export interface ExperienceDrawerProps {
  /** Existing experience to edit, `null` for "New Duniya", `undefined` when closed. */
  experience: AdminExperience | null | undefined;
  /** Every experience, to catch a taken id or slug before the API does. */
  experiences: AdminExperience[];
  environments: Environment[];
  families: AdminFamily[];
  catalog: CatalogItem[];
  motifs?: Motif[];
  motifsProblem?: Problem;
  onClose(): void;
  onSaved(experience: AdminExperience): void;
}

export function ExperienceDrawer({ experience, ...rest }: ExperienceDrawerProps) {
  const open = experience !== undefined;
  return (
    <Drawer open={open} onClose={rest.onClose} title={experience ? experienceTitle(experience) : "New Duniya"} eyebrow={experience ? `Duniya · ${experience.id}` : "New experience"}>
      {open && <ExperienceForm key={experience?.id ?? "new"} experience={experience} {...rest} />}
    </Drawer>
  );
}

function ExperienceForm({ experience, experiences, environments, families, catalog, motifs, motifsProblem, onClose, onSaved }: { experience: AdminExperience | null } & Omit<ExperienceDrawerProps, "experience">) {
  const canWrite = useCanWrite();
  const editing = experience !== null;
  const [draft, setDraft] = useState<Draft>(() => fromExperience(experience, environments));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const [packId, setPackId] = useState("");
  const dis = busy || !canWrite;

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }

  // Client-side checks mirroring the contract's structural rules; the API has the last word.
  const others = experiences.filter((x) => x.id !== experience?.id);
  const idError = editing
    ? undefined
    : draft.id && !ID_RE.test(draft.id)
      ? "snake_case: a lowercase letter, then letters, digits or underscores"
      : others.some((x) => x.id === draft.id.trim())
        ? "Another Duniya has this id"
        : undefined;
  const slugOwner = others.find((x) => x.slug === draft.slug.trim());
  const slugError =
    draft.slug && !SLUG_RE.test(draft.slug)
      ? "Lowercase letters and digits in words joined by single dashes"
      : draft.slug && draft.slug.length < 2
        ? "At least 2 characters"
        : slugOwner
          ? `/duniya/${draft.slug} is taken by ${experienceTitle(slugOwner)}`
          : undefined;
  const accentError = HEX.test(draft.accent) ? undefined : "Pick an accent";
  const seasonErrors = draft.season.map((w) => windowProblem({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label }));
  const collectionErrors = draft.collections.map((c, i) =>
    !ID_RE.test(c.id.trim()) || c.id.trim().length > 40
      ? "Id: snake_case, up to 40"
      : draft.collections.some((o, j) => j < i && o.id.trim() === c.id.trim())
        ? "Ids must be unique"
        : !c.title.trim()
          ? "Give it a title"
          : undefined,
  );
  const blocked = Boolean(
    idError ||
      slugError ||
      accentError ||
      !draft.id.trim() ||
      !draft.codename.trim() ||
      !draft.title.trim() ||
      !draft.slug.trim() ||
      !draft.environment ||
      seasonErrors.some(Boolean) ||
      collectionErrors.some(Boolean),
  );

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || blocked) return;
    setBusy(true);
    setProblem(undefined);
    const body = toInput(draft);
    try {
      const saved = editing ? await api.experiences.update(experience.id, body) : await api.experiences.create(body);
      onSaved(saved);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  const familyOf = (id: string) => families.find((f) => f.id === id);
  const itemOf = (slug: string) => catalog.find((c) => c.slug === slug);
  const addableFamilies = sortFamilies(families.filter((f) => f.kind !== "raw" && !draft.avatars.includes(f.id)));
  const addableItems = catalog.filter((c) => !draft.items.includes(c.slug));
  const libraryLeft = (motifs ?? []).filter((m) => !draft.motif_pack.includes(m.id));
  const packIdError = packId && (!ID_RE.test(packId) ? "snake_case" : draft.motif_pack.includes(packId) ? "Already in the pack" : undefined);
  const serverField = problem?.code;

  function addPackEntry(id: string) {
    if (!id || draft.motif_pack.includes(id) || draft.motif_pack.length >= MAX_LIST) return;
    set("motif_pack", [...draft.motif_pack, id]);
  }

  return (
    <form onSubmit={submit} className="grid gap-6" aria-busy={busy}>
      <OwnerOnlyHint what="Duniya changes" />

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Naming and copy</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Id" hint="snake_case English, e.g. festive; what designs remember; fixed once created" error={idError ?? (serverField === "experience_exists" ? "Taken on the API" : undefined)}>
            {(id) => <input id={id} className="ak-input ak-input-sm font-mono" maxLength={40} value={draft.id} onChange={(e) => set("id", e.target.value)} disabled={dis || editing} required />}
          </Field>
          <Field label="Codename" hint="Brand name customers see, e.g. Utsav (up to 40)">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={40} value={draft.codename} onChange={(e) => set("codename", e.target.value)} disabled={dis} required />}
          </Field>
          <Field
            label="Slug"
            hint={`The page URL: /duniya/${draft.slug || "<slug>"}${editing && draft.slug !== experience.slug ? " · links to the old URL stop working" : ""}`}
            error={slugError ?? (serverField === "slug_exists" ? "Taken on the API" : undefined)}
          >
            {(id) => <input id={id} className="ak-input ak-input-sm font-mono" maxLength={60} value={draft.slug} onChange={(e) => set("slug", e.target.value.toLowerCase())} disabled={dis} required />}
          </Field>
          <Field label="Title" hint="Plain descriptor always shown with the codename, e.g. Festive & gifting">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={80} value={draft.title} onChange={(e) => set("title", e.target.value)} disabled={dis} required />}
          </Field>
          <Field label="Tagline" hint="Up to 120 characters" className="sm:col-span-2">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={120} value={draft.tagline} onChange={(e) => set("tagline", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Description" hint="The page's lead paragraph, craft register (up to 500)" className="sm:col-span-2">
            {(id) => <textarea id={id} className="ak-input" rows={3} maxLength={500} value={draft.description} onChange={(e) => set("description", e.target.value)} disabled={dis} />}
          </Field>
        </div>
      </fieldset>

      <fieldset className="grid gap-3">
        <legend className="ak-label mb-1.5">Backdrop (Mahaul)</legend>
        <p className="text-[11px] text-surface-muted">The page&apos;s stage strip and the studio&apos;s preset. A backdrop whose preset is pending renders as the studio until the storefront builds it.</p>
        <EnvironmentPicker label="Backdrop" value={draft.environment} onChange={(v) => set("environment", v)} environments={environments} disabled={dis} />
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Page surface</legend>
        <PaletteRadio label="Accent" hint="Links, chips and the page's rules; the cream paper stays" colours={ACCENTS} value={draft.accent} onChange={(hex) => set("accent", hex)} disabled={dis} error={accentError} />
        <PaletteRadio label="Paper tint" hint="A light wash over the cream, or none" colours={TINTS} value={draft.paper_tint} onChange={(hex) => set("paper_tint", hex)} disabled={dis} allowNone="Plain cream" />
        <Field label="Hero media" hint="URL or site path of the hero image or loop; empty leads with the stage strip (up to 500)">
          {(id) => <input id={id} className="ak-input ak-input-sm font-mono" maxLength={500} placeholder="https://… or /media/duniya/utsav.jpg" value={draft.hero_media} onChange={(e) => set("hero_media", e.target.value)} disabled={dis} />}
        </Field>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Look</legend>
        <Field label="Style" hint={STYLES.find((s) => s.id === draft.style)?.hint ?? "The studio presets it on templates that offer it"}>
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.style} onChange={(e) => set("style", e.target.value as ExperienceStyle)} disabled={dis}>
              {STYLES.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label} · {s.id}
                </option>
              ))}
            </select>
          )}
        </Field>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Motif pack (Buti offered first)</legend>
        <p className="text-[11px] text-surface-muted">
          In order. An entry is a motif id or a pack id (every motif tagged with it); ids whose artwork hasn&apos;t landed stay here as pending.
        </p>
        <OrderedList
          ids={draft.motif_pack}
          onChange={(next) => set("motif_pack", next)}
          disabled={dis}
          empty="No motifs: Buti shows the library in its own order."
          name={(id) => {
            const state = packEntry(id, motifs);
            return state.kind === "motif" ? state.motif.label : id;
          }}
          render={(id) => <PackEntry id={id} motifs={motifs} />}
        />
        {motifsProblem && !motifs ? (
          <span className="text-xs text-danger">The motif library didn&apos;t load ({motifsProblem.code}); ids can still be added by hand.</span>
        ) : (
          libraryLeft.length > 0 && (
            <div className="grid gap-1.5">
              <span className="text-[11px] text-surface-muted">Add from the library</span>
              <div className="flex flex-wrap gap-1.5">
                {libraryLeft.map((m) => (
                  <button key={m.id} type="button" className="ak-chip min-h-8 px-2 py-1 text-[12px]" onClick={() => addPackEntry(m.id)} disabled={dis || draft.motif_pack.length >= MAX_LIST} title={`Add ${m.label} (${m.id})`}>
                    <MotifThumb motif={m} size={20} />
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
          )
        )}
        <div className="flex flex-wrap items-start gap-2">
          <input
            aria-label="Pack or motif id to add"
            className="ak-input ak-input-sm max-w-[220px] font-mono"
            placeholder="pack id, e.g. comic_bursts"
            value={packId}
            onChange={(e) => setPackId(e.target.value.trim())}
            disabled={dis}
          />
          <button
            type="button"
            className="ak-btn ak-btn-secondary ak-btn-sm"
            onClick={() => {
              addPackEntry(packId);
              setPackId("");
            }}
            disabled={dis || !packId || Boolean(packIdError) || draft.motif_pack.length >= MAX_LIST}
          >
            Add id
          </button>
          {packIdError && <span className="self-center text-xs text-danger">{packIdError}</span>}
        </div>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Avatars (in display order)</legend>
        <p className="text-[11px] text-surface-muted">Drag or use the arrows to order. The Shop shows only the ones that are open (available, with a live template).</p>
        <OrderedList
          ids={draft.avatars}
          onChange={(next) => set("avatars", next)}
          disabled={dis}
          empty="No Avatars yet."
          name={(id) => {
            const f = familyOf(id);
            return f ? familyTitle(f) : id;
          }}
          render={(id) => {
            const f = familyOf(id);
            if (!f) {
              return (
                <span className="flex items-center gap-2 text-sm">
                  <span className="font-mono">{id}</span> <Pill tone="danger">Unknown family</Pill>
                </span>
              );
            }
            const open = f.available && f.ready;
            return (
              <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                <span className="font-medium">{familyTitle(f)}</span>
                <span className="font-mono text-[11px] text-surface-muted">{f.id}</span>
                {open ? <Pill tone="success">Open</Pill> : <Pill tone="warning">{!f.available ? "Hidden" : "No template"}</Pill>}
              </span>
            );
          }}
        />
        {serverField === "unknown_family" && <span className="text-xs text-danger">{problem?.detail}</span>}
        <AddSelect
          label="Add an Avatar"
          disabled={dis || draft.avatars.length >= MAX_LIST}
          options={addableFamilies.map((f) => ({ value: f.id, label: `${familyTitle(f)} · ${f.id}${f.available && f.ready ? "" : !f.available ? " (hidden)" : " (no template)"}` }))}
          onAdd={(id) => set("avatars", [...draft.avatars, id])}
        />
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Curated Shop items (in display order)</legend>
        <OrderedList
          ids={draft.items}
          onChange={(next) => set("items", next)}
          disabled={dis}
          empty="No curated items."
          name={(slug) => itemOf(slug)?.name ?? slug}
          render={(slug) => {
            const item = itemOf(slug);
            return (
              <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                <span className="font-medium">{item?.name ?? slug}</span>
                <span className="font-mono text-[11px] text-surface-muted">{slug}</span>
                {!item ? <Pill tone="danger">Unknown item</Pill> : item.available ? null : <Pill>Coming soon</Pill>}
              </span>
            );
          }}
        />
        <AddSelect
          label="Add an item"
          disabled={dis || draft.items.length >= MAX_LIST}
          options={addableItems.map((c) => ({ value: c.slug, label: `${c.name} · ${c.slug}${c.available ? "" : " (coming soon)"}` }))}
          onAdd={(slug) => set("items", [...draft.items, slug])}
        />
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Collections</legend>
        <p className="text-[11px] text-surface-muted">Sub-collections such as a licensed universe; none until a licence exists (licence reference empty for original work).</p>
        {draft.collections.map((c, i) => (
          <div key={c.key} className="ak-well grid gap-2 p-2.5">
            <div className="grid gap-2 sm:grid-cols-[140px_minmax(0,1fr)]">
              <input
                aria-label={`Collection ${i + 1} id`}
                className="ak-input ak-input-sm font-mono"
                placeholder="id"
                maxLength={40}
                value={c.id}
                onChange={(e) => set("collections", draft.collections.map((x) => (x.key === c.key ? { ...x, id: e.target.value } : x)))}
                disabled={dis}
              />
              <input
                aria-label={`Collection ${i + 1} title`}
                className="ak-input ak-input-sm"
                placeholder="Title"
                maxLength={80}
                value={c.title}
                onChange={(e) => set("collections", draft.collections.map((x) => (x.key === c.key ? { ...x, title: e.target.value } : x)))}
                disabled={dis}
              />
            </div>
            <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-2">
              <input
                aria-label={`Collection ${i + 1} licence reference`}
                className="ak-input ak-input-sm"
                placeholder="Licence reference (empty for original work)"
                maxLength={120}
                value={c.licence_ref}
                onChange={(e) => set("collections", draft.collections.map((x) => (x.key === c.key ? { ...x, licence_ref: e.target.value } : x)))}
                disabled={dis}
              />
              <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => set("collections", draft.collections.filter((x) => x.key !== c.key))} disabled={dis}>
                Remove
              </button>
            </div>
            {collectionErrors[i] && <span className="text-xs text-danger">{collectionErrors[i]}</span>}
          </div>
        ))}
        <button
          type="button"
          className="ak-btn ak-btn-secondary ak-btn-sm justify-self-start"
          onClick={() => set("collections", [...draft.collections, { key: nextKey(), id: "", title: "", licence_ref: "" }])}
          disabled={dis || draft.collections.length >= MAX_LIST}
        >
          Add collection
        </button>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Season windows</legend>
        <p className="text-[11px] text-surface-muted">A badge on the Shop while today is inside a window. Every-year windows may wrap the new year (20 Dec – 5 Jan); one-off windows are two dates.</p>
        {draft.season.map((w, i) => (
          <SeasonRow
            key={w.key}
            index={i}
            window={w}
            error={seasonErrors[i]}
            disabled={dis}
            onChange={(next) => set("season", draft.season.map((x) => (x.key === w.key ? next : x)))}
            onRemove={() => set("season", draft.season.filter((x) => x.key !== w.key))}
          />
        ))}
        <button
          type="button"
          className="ak-btn ak-btn-secondary ak-btn-sm justify-self-start"
          onClick={() => set("season", [...draft.season, { key: nextKey(), label: "", yearly: true, starts_on: "--01-01", ends_on: "--01-31" }])}
          disabled={dis || draft.season.length >= MAX_SEASONS}
        >
          Add window
        </button>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">On the Shop</legend>
        <Field label="Available" hint="Off hides it from the Shop's Duniya; its page says coming soon" inline>
          {(id) => <Switch id={id} label="Available" checked={draft.available} onChange={(v) => set("available", v)} disabled={dis} />}
        </Field>
        <Field label="Sort order" hint="Lower comes first on the Shop">
          {(id) => <input id={id} type="number" step="1" className="ak-input ak-input-sm max-w-[140px]" value={draft.sort_order} onChange={(e) => set("sort_order", e.target.value)} disabled={dis} required />}
        </Field>
      </fieldset>

      {problem && <ProblemCard compact problem={problem} title={PROBLEM_TITLES[problem.code ?? ""]} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || blocked} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save Duniya" : "Create Duniya"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}

/** A motif's artwork on a cream tile (the SVGs are black on transparent). */
function MotifThumb({ motif, size = 28 }: { motif: Motif; size?: number }) {
  const src = /^[a-z][a-z0-9+.-]*:/i.test(motif.svg_url) ? motif.svg_url : apiUrl(motif.svg_url.startsWith("/") ? motif.svg_url : `/${motif.svg_url}`);
  return (
    <span className="grid flex-none place-items-center rounded-[6px] border border-black/10 bg-cream p-0.5" style={{ width: size, height: size }} aria-hidden="true">
      {/* Library artwork served by the API; a plain <img> keeps any host working. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt="" className="h-full w-full object-contain" />
    </span>
  );
}

function PackEntry({ id, motifs }: { id: string; motifs?: Motif[] }) {
  const state = packEntry(id, motifs);
  if (state.kind === "motif") {
    return (
      <span className="flex items-center gap-2 text-sm">
        <MotifThumb motif={state.motif} />
        <span className="font-medium">{state.motif.label}</span>
        <span className="font-mono text-[11px] text-surface-muted">{id}</span>
      </span>
    );
  }
  if (state.kind === "pack") {
    return (
      <span className="flex flex-wrap items-center gap-2 text-sm">
        <span className="flex">
          {state.motifs.slice(0, 4).map((m) => (
            <MotifThumb key={m.id} motif={m} size={22} />
          ))}
        </span>
        <span className="font-medium">Pack</span>
        <span className="font-mono text-[11px] text-surface-muted">{id}</span>
        <span className="text-[11px] text-surface-muted">
          {state.motifs.length} {state.motifs.length === 1 ? "motif" : "motifs"}
        </span>
      </span>
    );
  }
  return (
    <span className="flex items-center gap-2 text-sm">
      <span className="font-mono">{id}</span>
      <Pill tone="warning" title={motifs ? "No motif in the library has this id or tag yet; the storefront skips it until its artwork lands" : "The library hasn't loaded"}>
        {motifs ? "Pending" : "Unchecked"}
      </Pill>
    </span>
  );
}

interface OrderedListProps {
  ids: string[];
  onChange(next: string[]): void;
  render(id: string): React.ReactNode;
  /** Plain name for the buttons' accessible labels. */
  name(id: string): string;
  empty: string;
  disabled?: boolean;
}

/** An ordered list with drag-and-drop and ↑ ↓ ✕ buttons (the buttons are the keyboard path). */
function OrderedList({ ids, onChange, render, name, empty, disabled }: OrderedListProps) {
  const dragFrom = useRef<number | null>(null);
  const [over, setOver] = useState<number | null>(null);

  function move(from: number, to: number) {
    if (to < 0 || to >= ids.length || from === to) return;
    const next = [...ids];
    const [moved] = next.splice(from, 1);
    if (moved === undefined) return;
    next.splice(to, 0, moved);
    onChange(next);
  }

  if (ids.length === 0) return <p className="ak-well p-2.5 text-xs text-surface-muted">{empty}</p>;
  return (
    <ol className="grid gap-1.5">
      {ids.map((id, i) => (
        <li
          key={id}
          draggable={!disabled}
          onDragStart={(e) => {
            dragFrom.current = i;
            e.dataTransfer.effectAllowed = "move";
          }}
          onDragOver={(e) => {
            if (dragFrom.current === null) return;
            e.preventDefault();
            setOver(i);
          }}
          onDragLeave={() => setOver((o) => (o === i ? null : o))}
          onDrop={(e) => {
            e.preventDefault();
            if (dragFrom.current !== null) move(dragFrom.current, i);
            dragFrom.current = null;
            setOver(null);
          }}
          onDragEnd={() => {
            dragFrom.current = null;
            setOver(null);
          }}
          className={["ak-well flex items-center gap-2 border p-2", over === i ? "border-surface-accent" : "border-transparent", disabled ? "" : "cursor-grab"].join(" ")}
        >
          <span aria-hidden="true" className="w-5 flex-none text-right font-mono text-[11px] text-surface-muted">
            {i + 1}
          </span>
          <div className="min-w-0 flex-1">{render(id)}</div>
          <div className="flex flex-none gap-1">
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm px-2" onClick={() => move(i, i - 1)} disabled={disabled || i === 0} aria-label={`Move ${name(id)} up`}>
              ↑
            </button>
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm px-2" onClick={() => move(i, i + 1)} disabled={disabled || i === ids.length - 1} aria-label={`Move ${name(id)} down`}>
              ↓
            </button>
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm px-2" onClick={() => onChange(ids.filter((x) => x !== id))} disabled={disabled} aria-label={`Remove ${name(id)}`}>
              ✕
            </button>
          </div>
        </li>
      ))}
    </ol>
  );
}

/** A select of what can still be added, and an Add button. */
function AddSelect({ label, options, onAdd, disabled }: { label: string; options: { value: string; label: string }[]; onAdd(value: string): void; disabled?: boolean }) {
  const [value, setValue] = useState("");
  const current = options.some((o) => o.value === value) ? value : "";
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-2">
      <select aria-label={label} className="ak-input ak-input-sm" value={current} onChange={(e) => setValue(e.target.value)} disabled={disabled || options.length === 0}>
        <option value="">{options.length === 0 ? "Nothing left to add" : `${label}…`}</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <button
        type="button"
        className="ak-btn ak-btn-secondary ak-btn-sm"
        onClick={() => {
          if (!current) return;
          onAdd(current);
          setValue("");
        }}
        disabled={disabled || !current}
      >
        Add
      </button>
    </div>
  );
}

interface PaletteRadioProps {
  label: string;
  hint: string;
  colours: readonly PaletteColour[];
  /** A hex, or "" for none. */
  value: string;
  onChange(hex: string): void;
  disabled?: boolean;
  /** Offer an empty choice with this label. */
  allowNone?: string;
  error?: string;
}

/** Brand palette tokens as swatch radios; a value outside the palette (set through the API) shows as "custom" and stays chosen. */
function PaletteRadio({ label, hint, colours, value, onChange, disabled, allowNone, error }: PaletteRadioProps) {
  const known = paletteColour(value);
  const custom = value && !known && HEX.test(value) ? value : undefined;
  const current = known ? `${known.label} · ${known.hex}` : custom ? `Custom · ${custom}` : allowNone && !value ? allowNone : "—";
  return (
    <div className="grid gap-1.5">
      <span className="ak-label">{label}</span>
      <div role="radiogroup" aria-label={label} className="flex flex-wrap items-center gap-1.5">
        {allowNone && (
          <button type="button" role="radio" aria-checked={!value} className="ak-chip min-h-8 px-3 py-1 text-[12px]" onClick={() => onChange("")} disabled={disabled}>
            {allowNone}
          </button>
        )}
        {colours.map((c) => (
          <button
            key={c.token}
            type="button"
            role="radio"
            aria-checked={value.toUpperCase() === c.hex}
            aria-label={`${c.label} ${c.hex}`}
            title={`${c.label} · ${c.hex} (${c.token})`}
            onClick={() => onChange(c.hex)}
            disabled={disabled}
            className="grid h-8 w-8 place-items-center rounded-full border border-surface-border transition-shadow disabled:cursor-not-allowed disabled:opacity-60 aria-checked:ring-2 aria-checked:ring-surface-accent aria-checked:ring-offset-2 aria-checked:ring-offset-[var(--ak-card)]"
          >
            <span aria-hidden="true" className="h-6 w-6 rounded-full border border-black/10" style={{ background: c.hex }} />
          </button>
        ))}
        {custom && (
          <span role="radio" aria-checked="true" aria-label={`Custom ${custom}`} className="grid h-8 w-8 place-items-center rounded-full ring-2 ring-surface-accent ring-offset-2 ring-offset-[var(--ak-card)]" title={`Custom · ${custom}`}>
            <span aria-hidden="true" className="h-6 w-6 rounded-full border border-black/10" style={{ background: custom }} />
          </span>
        )}
      </div>
      {error ? <span className="text-xs text-danger">{error}</span> : <span className="text-[11px] text-surface-muted">{current} · {hint}</span>}
    </div>
  );
}

interface SeasonRowProps {
  index: number;
  window: SeasonDraft;
  error?: string;
  disabled?: boolean;
  onChange(next: SeasonDraft): void;
  onRemove(): void;
}

/** One season window: a label, every year (month + day) or once (two dates), and whether today is inside it. */
function SeasonRow({ index, window: w, error, disabled, onChange, onRemove }: SeasonRowProps) {
  const n = index + 1;
  const year = new Date().getFullYear();

  function setKind(yearly: boolean) {
    if (yearly === w.yearly) return;
    if (yearly) {
      // A one-off window keeps its days when it turns into an every-year one.
      const toMonthDay = (date: string, fallback: string) => (/^\d{4}-\d{2}-\d{2}$/.test(date) ? `--${date.slice(5)}` : fallback);
      onChange({ ...w, yearly, starts_on: toMonthDay(w.starts_on, "--01-01"), ends_on: toMonthDay(w.ends_on, "--01-31") });
    } else {
      const toDate = (md: string) => {
        const p = parseMonthDay(md);
        return p ? `${year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}` : "";
      };
      onChange({ ...w, yearly, starts_on: toDate(w.starts_on), ends_on: toDate(w.ends_on) });
    }
  }

  const live = !error && inSeason({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label });
  return (
    <div className="ak-well grid gap-2 p-2.5">
      <div className="grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-2">
        <input aria-label={`Window ${n} label`} className="ak-input ak-input-sm" placeholder="Badge text, e.g. Diwali" maxLength={40} value={w.label} onChange={(e) => onChange({ ...w, label: e.target.value })} disabled={disabled} />
        <select aria-label={`Window ${n} repeats`} className="ak-input ak-input-sm" value={w.yearly ? "yearly" : "once"} onChange={(e) => setKind(e.target.value === "yearly")} disabled={disabled}>
          <option value="yearly">Every year</option>
          <option value="once">Once (dates)</option>
        </select>
        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={onRemove} disabled={disabled} aria-label={`Remove window ${w.label || n}`}>
          Remove
        </button>
      </div>
      {w.yearly ? (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <MonthDayInput label={`Window ${n} starts`} value={w.starts_on} onChange={(v) => onChange({ ...w, starts_on: v })} disabled={disabled} />
          <span className="text-surface-muted">to</span>
          <MonthDayInput label={`Window ${n} ends`} value={w.ends_on} onChange={(v) => onChange({ ...w, ends_on: v })} disabled={disabled} />
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <input type="date" aria-label={`Window ${n} starts`} className="ak-input ak-input-sm w-auto" value={w.starts_on} onChange={(e) => onChange({ ...w, starts_on: e.target.value })} disabled={disabled} />
          <span className="text-surface-muted">to</span>
          <input type="date" aria-label={`Window ${n} ends`} className="ak-input ak-input-sm w-auto" value={w.ends_on} onChange={(e) => onChange({ ...w, ends_on: e.target.value })} disabled={disabled} />
        </div>
      )}
      {error ? (
        <span className="text-xs text-danger">{error}</span>
      ) : (
        <span className="text-[11px] text-surface-muted">
          {formatWindow({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label.trim() || "Untitled" })}
          {live ? " · in season today" : ""}
        </span>
      )}
    </div>
  );
}

/** A recurring day as a month select and a day number ("--10-01"). */
function MonthDayInput({ label, value, onChange, disabled }: { label: string; value: string; onChange(value: string): void; disabled?: boolean }) {
  const parsed = parseMonthDay(value) ?? { month: 1, day: 1 };
  return (
    <span className="inline-flex items-center gap-1">
      <input
        type="number"
        min={1}
        max={31}
        aria-label={`${label}: day`}
        className="ak-input ak-input-sm w-16"
        value={parsed.day}
        onChange={(e) => onChange(monthDay(parsed.month, Math.min(31, Math.max(1, Number.parseInt(e.target.value, 10) || 1))))}
        disabled={disabled}
      />
      <select aria-label={`${label}: month`} className="ak-input ak-input-sm w-auto" value={parsed.month} onChange={(e) => onChange(monthDay(Number(e.target.value), parsed.day))} disabled={disabled}>
        {MONTHS.map((m, i) => (
          <option key={m} value={i + 1}>
            {m}
          </option>
        ))}
      </select>
    </span>
  );
}
