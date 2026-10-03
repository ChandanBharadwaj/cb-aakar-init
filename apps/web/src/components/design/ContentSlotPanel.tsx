"use client";

import { useId, useRef, useState } from "react";
import type { Family, Motif, TemplateAnchor, TemplateDescriptor, Upload } from "@/lib/api/types";
import { sizeHint } from "@/lib/families";
import {
  addWords,
  anchorAccepts,
  anchorModes,
  CHHAAP,
  clampDepth,
  clampScale,
  clampText,
  clearFeature,
  COMIC_POP,
  contentAnchors,
  contentWords,
  crowdedBy,
  detectScript,
  featureCodename,
  featureDescriptor,
  featureLabel,
  featureOfType,
  featurePhrase,
  featuresOn,
  featureSummary,
  isRequired,
  isSolo,
  isThinPiece,
  longestMmRange,
  markDefaults,
  maxTextChars,
  MODE_OPTIONS,
  motifDepthRange,
  motifScaleRange,
  ORIENTATIONS,
  placeFeature,
  reliefRange,
  TEXT_MAX_CHARS,
  templateTakes,
  textDepthRange,
  textLength,
  uploadIdOf,
  type ContentMode,
  type ContentSource,
  type EmbossText,
  type Feature,
  type FeatureEdit,
  type FeatureType,
  type HeroMesh,
  type MotifFeature,
  type ReliefImage,
  type ReliefMode,
} from "@/lib/features";
import { capitalise, formatMm, joinList } from "@/lib/format";
import { formatScale, motifArtUrl, orderByPack } from "@/lib/motifs";
import { fileFormat } from "@/lib/uploads";
import { useDesignStore } from "@/store/design";
import { Dropzone } from "@/components/ui/Dropzone";
import { RangeField } from "@/components/ui/RangeField";
import { Segmented } from "@/components/ui/Segmented";
import { useMotifs, type MotifLibrary } from "./useMotifs";
import { useUploadReview, type UploadReview } from "./useUploadReview";

export interface ContentSlotPanelProps {
  template: Pick<TemplateDescriptor, "id" | "family" | "anchors" | "features_supported">;
  /** The Avatar, for the Naam character limit, the hero form's size envelope and thin-piece defaults. */
  family?: Family;
  /** Per spot: a photo or a form on its own, or a name and a motif side by side. */
  features: readonly Feature[];
  /** Applies an edit to the whole list; the parent owns it (the studio's store or the composer's state). */
  onChange(edit: FeatureEdit): void;
  /** Motif ids (or pack ids) the Buti picker offers first: a Duniya experience's motif pack. */
  motifPack?: readonly string[];
  /** Whose picks those are, e.g. "Utsav". */
  motifPackLabel?: string;
  /**
   * Swaroop (a raw print): the piece is its one form, so its card reads "Your form", carries the size slider and the
   * orientation toggle, and its file can be replaced but never removed.
   */
  raw?: boolean;
  /**
   * The piece's style variant (the design's, or a Duniya's preset while composing). Under `comic_pop` a new name or
   * motif starts raised and bolder wherever its spot allows raised content.
   */
  look?: string;
  disabled?: boolean;
  className?: string;
}

/** The raised / cut-in toggle's options for the modes a spot allows. */
function reliefOptions(modes: readonly ReliefMode[]) {
  return modes.map((m) => ({ ...MODE_OPTIONS[m], value: m }));
}

/** The modes a spot allows for a name or a motif (a photo's lithophane aside). */
function reliefModes(anchor: TemplateAnchor, type: "emboss_text" | "motif"): ReliefMode[] {
  return anchorModes(anchor, type).filter((m): m is ReliefMode => m !== "lithophane");
}

/** "Cut into the top rail.": what a spot with one mode does, instead of a toggle with one choice. */
function onlyModeLine(mode: ContentMode | undefined, spot: string): string {
  if (mode === "deboss") return `Cut into the ${spot}.`;
  if (mode === "lithophane") return `Becomes the ${spot}, lit from behind.`;
  return `Raised on the ${spot}.`;
}

const LITHOPHANE_COPY = "Darker parts of the photo become thicker plastic, so they glow less.";

const ACTION_LINK = "text-left text-[11px] font-semibold underline underline-offset-2 disabled:opacity-60";
const QUIET_LINK = "justify-self-start text-left text-[11px] text-surface-muted underline-offset-2 hover:underline";

/**
 * "Chhaap · Your imprint": one card per template anchor that takes content, with a tab per accepted type — Naam
 * (text), Buti (a motif from the library), Chhavi (photo relief) and Roop (your own 3D form, volume anchors only;
 * a spot that takes only a form reads "Your form").
 * A name and a motif may share a spot; a photo fills its spot on its own, so the tabs that would crowd a spot are
 * disabled with a hint (the API refuses a crowded spot). Everything follows the descriptor: a spot offers only the
 * modes its anchor lists (one mode: no toggle, that mode is sent), a spot marked `required` comes first, says so and
 * can't be left plain, and letters and depths stay inside the family's `max_text_chars` and the anchor's
 * `max_relief_mm`. Controlled: the parent owns the feature list. File names and review states live in the design
 * store, so they survive remounts; a file the studio is still checking is polled until it is cleared or turned down.
 */
export function ContentSlotPanel({ template, family, features, onChange, motifPack, motifPackLabel, raw, look, disabled, className }: ContentSlotPanelProps) {
  const headingId = useId();
  // Spots the piece can't be made without come first, so the customer is asked for them up front.
  const anchors = [...contentAnchors(template)].sort((a, b) => Number(isRequired(b)) - Number(isRequired(a)));
  const takesMotifs = anchors.some((a) => anchorAccepts(a, template).includes("motif"));
  const library = useMotifs(takesMotifs);
  if (anchors.length === 0) return null;
  const takes = templateTakes(template);
  const words = addWords(takes);
  const shares = anchors.some((a) => {
    const accepts = anchorAccepts(a, template);
    return accepts.includes("emboss_text") && accepts.includes("motif");
  });
  const sharing = shares ? ` A name and a motif can share a spot${takes.includes("relief_image") ? "; a photo takes one to itself" : ""}.` : "";
  const intro = raw ? "Your model file, checked, repaired and printed as it is." : words.length > 0 ? `${capitalise(joinList(words, "or"))}, set into the piece.${sharing}` : undefined;
  const thin = isThinPiece(template, family);
  return (
    <section className={["grid gap-3", className].filter(Boolean).join(" ")} aria-labelledby={headingId}>
      <div className="grid gap-0.5">
        <h2 id={headingId} className="ak-label">
          {CHHAAP.label}
        </h2>
        {intro && <p className="text-[11px] leading-snug text-surface-muted">{intro}</p>}
      </div>
      {anchors.map((anchor) => (
        <AnchorCard
          key={anchor.id}
          anchor={anchor}
          template={template}
          family={family}
          onSpot={featuresOn(features, anchor.id)}
          onChange={onChange}
          lockForm={Boolean(raw) && (anchor.kind ?? "surface") === "volume"}
          disabled={disabled}
          library={library}
          motifPack={motifPack}
          motifPackLabel={motifPackLabel}
          thin={thin}
          look={look}
        />
      ))}
    </section>
  );
}

interface AnchorCardProps {
  anchor: TemplateAnchor;
  template: Pick<TemplateDescriptor, "features_supported">;
  family?: Family;
  /** What sits on this spot now. */
  onSpot: Feature[];
  onChange(edit: FeatureEdit): void;
  /** Swaroop's one form: titled "Your form", with orientation, never left plain. */
  lockForm: boolean;
  disabled?: boolean;
  library: MotifLibrary;
  motifPack?: readonly string[];
  motifPackLabel?: string;
  thin: boolean;
  look?: string;
}

function areaHint(anchor: TemplateAnchor): string | undefined {
  if (anchor.kind === "volume" && anchor.bounds_mm && anchor.bounds_mm.length >= 3) {
    return `Room for ${anchor.bounds_mm.slice(0, 3).map((n) => Math.round(n)).join(" × ")} mm`;
  }
  if (anchor.size_mm && anchor.size_mm.length >= 2) return `Print area ${anchor.size_mm.slice(0, 2).map((n) => Math.round(n)).join(" × ")} mm`;
  return undefined;
}

/** Why a tab is closed on this spot, in the customer's words (the spot is named in lower case). */
function crowdingHint(accepts: readonly FeatureType[], onSpot: readonly Feature[], spot: string): string | undefined {
  const photo = onSpot.some((f) => f.type === "relief_image");
  if (photo && (accepts.includes("emboss_text") || accepts.includes("motif"))) {
    const others = accepts.includes("emboss_text") && accepts.includes("motif") ? "a name or a motif" : accepts.includes("motif") ? "a motif" : "a name";
    return `A photo fills the ${spot} on its own. Leave it plain to put ${others} here instead.`;
  }
  const name = onSpot.some((f) => f.type === "emboss_text");
  const motif = onSpot.some((f) => f.type === "motif");
  if ((name || motif) && accepts.includes("relief_image")) {
    const what = name && motif ? "your name and motif" : name ? "your name" : "your motif";
    return `A photo needs the ${spot} to itself; clear ${what} to add one here.`;
  }
  return undefined;
}

function AnchorCard({ anchor, template, family, onSpot, onChange, lockForm, disabled, library, motifPack, motifPackLabel, thin, look }: AnchorCardProps) {
  const accepts = anchorAccepts(anchor, template);
  const required = isRequired(anchor);
  // A volume spot that takes only a customer's own form (Pratima's top) is "Your form", like Swaroop's one form.
  const formSpot = (anchor.kind ?? "surface") === "volume" && accepts.length > 0 && accepts.every((t) => t === "hero_mesh");
  const [chosen, setChosen] = useState<FeatureType>();
  const [replacing, setReplacing] = useState(false);
  const tabsId = useId();
  const solo = onSpot.find((f) => isSolo(f.type));
  const review = useUploadReview(solo ? uploadIdOf(solo) : undefined);
  const blocked = (type: FeatureType) => crowdedBy(onSpot, type).length > 0;
  const active: FeatureType | undefined =
    (chosen && accepts.includes(chosen) && !blocked(chosen) ? chosen : undefined) ?? onSpot[0]?.type ?? accepts.find((t) => !blocked(t)) ?? accepts[0];
  const title = lockForm || formSpot ? "Your form" : anchor.label;
  const spot = anchor.label.toLowerCase();
  const area = areaHint(anchor);
  const hint = formSpot && !lockForm ? [anchor.label, area].filter(Boolean).join(" · ") : area;
  const crowded = accepts.length > 1 ? crowdingHint(accepts, onSpot, spot) : undefined;
  const others = onSpot.filter((f) => f.type !== active);
  const has = (type: FeatureType) => onSpot.some((f) => f.type === type);

  function place(next: Feature) {
    setReplacing(false);
    onChange((list) => placeFeature(list, next));
  }

  function clear(type?: FeatureType) {
    setReplacing(false);
    onChange((list) => clearFeature(list, anchor.id, type));
  }

  function reupload() {
    if (solo) setChosen(solo.type);
    setReplacing(true);
  }

  return (
    <article className="ak-well grid gap-3 p-3.5">
      <header className="flex items-start justify-between gap-3">
        <div className="grid gap-0.5">
          <h3 className="text-sm font-semibold leading-tight">{title}</h3>
          {hint && <span className="text-[11px] text-surface-muted">{hint}</span>}
        </div>
        {lockForm ? null : required ? (
          onSpot.length > 0 ? null : <span className="text-[11px] font-semibold text-warning">Needed</span>
        ) : onSpot.length > 0 ? (
          <button type="button" className="text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => clear()} disabled={disabled}>
            Leave plain
          </button>
        ) : (
          <span className="text-[11px] text-surface-muted">Plain</span>
        )}
      </header>
      {required && !lockForm && onSpot.length === 0 && (
        <p className="text-[11px] leading-snug text-surface-muted">
          Needed to make this piece: add {contentWords(anchor, template)} here.
        </p>
      )}

      {accepts.length > 1 && (
        <div role="tablist" aria-label={`Content for ${title}`} className="flex flex-wrap gap-1.5">
          {accepts.map((type) => {
            const closed = blocked(type);
            const placed = has(type);
            return (
              <button
                key={type}
                type="button"
                role="tab"
                id={`${tabsId}-${type}`}
                aria-selected={active === type}
                aria-controls={`${tabsId}-panel`}
                aria-disabled={closed || undefined}
                disabled={disabled || closed}
                title={closed ? (type === "relief_image" ? `A photo needs the ${spot} to itself` : `A photo fills the ${spot} on its own`) : featureLabel(type)}
                className="ak-chip min-h-8 px-3 py-1 text-[11.5px]"
                onClick={() => setChosen(type)}
              >
                {featureCodename(type)}
                <span className="text-[10px] font-medium opacity-70">{featureDescriptor(type)}</span>
                {placed && (
                  <>
                    <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-surface-accent" />
                    <span className="sr-only">, added</span>
                  </>
                )}
              </button>
            );
          })}
        </div>
      )}
      {crowded && <p className="text-[11px] leading-snug text-surface-muted">{crowded}</p>}

      <div id={`${tabsId}-panel`} role={accepts.length > 1 ? "tabpanel" : undefined} aria-labelledby={accepts.length > 1 && active ? `${tabsId}-${active}` : undefined}>
        {active === "emboss_text" && (
          <NaamPanel
            anchor={anchor}
            family={family}
            current={featureOfType(onSpot, anchor.id, "emboss_text")}
            beside={has("motif")}
            look={look}
            onPlace={place}
            onClear={() => clear("emboss_text")}
            disabled={disabled}
          />
        )}
        {active === "motif" && (
          <ButiPanel
            anchor={anchor}
            current={featureOfType(onSpot, anchor.id, "motif")}
            beside={has("emboss_text")}
            library={library}
            motifPack={motifPack}
            motifPackLabel={motifPackLabel}
            thin={thin}
            look={look}
            onPlace={place}
            onClear={() => clear("motif")}
            disabled={disabled}
          />
        )}
        {active === "relief_image" && (
          <ChhaviPanel
            anchor={anchor}
            feature={featureOfType(onSpot, anchor.id, "relief_image")}
            onPlace={place}
            disabled={disabled}
            review={review}
            replacing={replacing}
            onReplace={setReplacing}
          />
        )}
        {active === "hero_mesh" && (
          <RoopPanel
            anchor={anchor}
            family={family}
            feature={featureOfType(onSpot, anchor.id, "hero_mesh")}
            onPlace={place}
            disabled={disabled}
            review={review}
            replacing={replacing}
            onReplace={setReplacing}
            lockForm={lockForm}
          />
        )}
      </div>

      {others.length > 0 && (
        <p className="text-[11px] text-surface-muted">Also on this spot: {others.map((f) => featureSummary(f, library.motifs)).join(" · ")}.</p>
      )}
      <ReviewNote review={review} what={solo?.type === "relief_image" ? "photo" : "model file"} onReupload={reupload} disabled={disabled} />
    </article>
  );
}

/** Where a file the studio is checking stands: checking, still checking after two minutes, or turned down. */
function ReviewNote({ review, what, onReupload, disabled }: { review: UploadReview; what: string; onReupload(): void; disabled?: boolean }) {
  if (review.checking) {
    return (
      <p role="status" className="text-[11px] leading-snug text-warning">
        The studio is checking this file. You can sculpt as soon as it&apos;s cleared.
      </p>
    );
  }
  if (review.stalled) {
    return (
      <div role="status" className="grid gap-1.5 text-[11px] leading-snug text-warning">
        <p>The studio is still checking this file; it can take a little longer.</p>
        <div className="flex flex-wrap gap-x-3 gap-y-1">
          <button type="button" className={ACTION_LINK} onClick={review.recheck} disabled={disabled}>
            Check again
          </button>
          <button type="button" className={ACTION_LINK} onClick={onReupload} disabled={disabled}>
            Try a different {what}
          </button>
        </div>
      </div>
    );
  }
  if (review.rejected) {
    return (
      <div role="status" className="grid gap-1.5 text-[11px] leading-snug text-warning">
        <p>{review.message}</p>
        <button type="button" className={`${ACTION_LINK} justify-self-start`} onClick={onReupload} disabled={disabled}>
          Choose a different {what}
        </button>
      </div>
    );
  }
  return null;
}

interface NaamPanelProps {
  anchor: TemplateAnchor;
  family?: Family;
  current?: EmbossText;
  /** A motif shares this spot. */
  beside: boolean;
  look?: string;
  onPlace(feature: EmbossText): void;
  onClear(): void;
  disabled?: boolean;
}

/**
 * Naam: up to the family's `max_text_chars` letters (counted as a reader counts them: an Indic vowel sign is part of
 * its letter), raised or cut in where the spot allows both (one mode: no toggle, that mode sent), at a depth within
 * the spot's `max_relief_mm` (Kunji: three letters, at most 0.6 mm).
 */
function NaamPanel({ anchor, family, current, beside, look, onPlace, onClear, disabled }: NaamPanelProps) {
  const id = useId();
  const max = maxTextChars(family);
  const range = textDepthRange(anchor);
  const modes = reliefModes(anchor, "emboss_text");
  const start = markDefaults(anchor, "emboss_text", { look });
  const text = current?.text ?? "";
  const mode = current?.mode ?? start.mode;
  const length = textLength(text);

  function update(patch: Partial<Pick<EmbossText, "text" | "mode" | "depth_mm">>) {
    const nextText = clampText(patch.text ?? text, max);
    // Only an empty box takes the name off; spaces stay while typing and are trimmed when the piece is sent.
    if (nextText.length === 0) {
      onClear();
      return;
    }
    const base: EmbossText = current ?? { type: "emboss_text", text: "", anchor: anchor.id, depth_mm: start.depth_mm, mode: start.mode };
    const next: EmbossText = { ...base, ...patch, text: nextText };
    const script = detectScript(next.text);
    if (script) next.script = script;
    else delete next.script;
    onPlace(next);
  }

  return (
    <div className="grid gap-2.5">
      <label htmlFor={id} className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
        <span>Your {featurePhrase("emboss_text")}</span>
        <span aria-live="polite" className={length >= max ? "font-semibold text-warning" : ""}>
          {length} / {max}
        </span>
      </label>
      <input
        id={id}
        type="text"
        className="ak-input min-h-10 py-1.5 text-sm"
        value={text}
        maxLength={TEXT_MAX_CHARS}
        placeholder={max < 4 ? "AK" : "Asha"}
        autoComplete="off"
        disabled={disabled}
        onChange={(e) => update({ text: e.target.value })}
      />
      {modes.length > 1 ? (
        <Segmented label="Letters" options={reliefOptions(modes)} value={mode} onChange={(m) => update({ mode: m })} disabled={disabled || !current} />
      ) : (
        <p className="text-[11px] text-surface-muted">{onlyModeLine(modes[0], anchor.label.toLowerCase())}</p>
      )}
      {current && (
        <RangeField
          label="Depth"
          min={range.min}
          max={range.max}
          step={0.1}
          value={clampDepth(current.depth_mm ?? range.default, range)}
          format={(v) => formatMm(v, 1)}
          disabled={disabled}
          onChange={(v) => update({ depth_mm: clampDepth(v, range) })}
        />
      )}
      <p className="text-[11px] leading-snug text-surface-muted">
        Latin or any Indian script.
        {anchor.max_text_height_mm ? ` Letters up to ${formatMm(anchor.max_text_height_mm)} tall.` : ""}
        {beside ? ` Shares the ${anchor.label.toLowerCase()} with your motif: the motif first, your name beside it.` : ""}
      </p>
    </div>
  );
}

interface ButiPanelProps {
  anchor: TemplateAnchor;
  current?: MotifFeature;
  /** A name shares this spot. */
  beside: boolean;
  library: MotifLibrary;
  motifPack?: readonly string[];
  motifPackLabel?: string;
  /** A thin piece: raised by default, and a cut-in motif gets a warning. */
  thin: boolean;
  look?: string;
  onPlace(feature: MotifFeature): void;
  onClear(): void;
  disabled?: boolean;
}

/**
 * Buti: a motif from the library (the Duniya experience's pack first), its size from the motif's smallest printable
 * scale up to filling the spot, raised or cut in where the spot allows both (cut in by default; raised on thin pieces
 * and, bolder, in the comic look; one mode: no toggle, that mode sent) and a depth within the spot's `max_relief_mm`.
 */
function ButiPanel({ anchor, current, beside, library, motifPack, motifPackLabel, thin, look, onPlace, onClear, disabled }: ButiPanelProps) {
  const depth = motifDepthRange(anchor);
  const chosenMotif = current ? library.motifs?.find((m) => m.id === current.motif_id) : undefined;
  const scale = motifScaleRange(chosenMotif);
  const modes = reliefModes(anchor, "motif");
  const start = markDefaults(anchor, "motif", { thin, look });
  const mode = current?.mode ?? start.mode;
  const spot = anchor.label.toLowerCase();
  const comic = look === COMIC_POP;

  function choose(motif: Motif) {
    const range = motifScaleRange(motif);
    // A new motif keeps the size, relief and depth already chosen, inside its own printable range.
    onPlace({
      type: "motif",
      motif_id: motif.id,
      anchor: anchor.id,
      scale: clampScale(current?.scale ?? range.default, range),
      depth_mm: clampDepth(current?.depth_mm ?? start.depth_mm, depth),
      mode: current?.mode ?? start.mode,
    });
  }

  return (
    <div className="grid gap-3">
      {library.motifs ? (
        <MotifPicker motifs={library.motifs} pack={motifPack} packLabel={motifPackLabel} value={current?.motif_id} onPick={choose} disabled={disabled} />
      ) : library.problem ? (
        <div role="status" className="grid gap-1.5 text-[11px] leading-snug text-warning">
          <p>The motif library isn&apos;t answering right now.</p>
          <button type="button" className={`${ACTION_LINK} justify-self-start`} onClick={library.retry} disabled={disabled}>
            Try again
          </button>
        </div>
      ) : (
        <p role="status" className="text-[11px] text-surface-muted">
          Opening the motif library…
        </p>
      )}

      {current ? (
        <>
          <RangeField
            label="Size"
            min={scale.min}
            max={scale.max}
            step={0.05}
            value={clampScale(current.scale ?? scale.default, scale)}
            format={formatScale}
            hint="100% fills the spot"
            disabled={disabled}
            onChange={(v) => onPlace({ ...current, scale: clampScale(v, scale) })}
          />
          {modes.length > 1 ? (
            <Segmented label="Motif" options={reliefOptions(modes)} value={mode} onChange={(m) => onPlace({ ...current, mode: m })} disabled={disabled} />
          ) : (
            <p className="text-[11px] text-surface-muted">{onlyModeLine(modes[0], spot)}</p>
          )}
          <RangeField
            label="Depth"
            min={depth.min}
            max={depth.max}
            step={0.1}
            value={clampDepth(current.depth_mm ?? depth.default, depth)}
            format={(v) => formatMm(v, 1)}
            disabled={disabled}
            onChange={(v) => onPlace({ ...current, depth_mm: clampDepth(v, depth) })}
          />
          {thin && mode === "deboss" && modes.includes("emboss") && (
            <p role="status" className="text-[11px] leading-snug text-warning">
              This piece is thin: a cut-in motif can leave too little wall behind it. Raised is the safer choice.
            </p>
          )}
          <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
            <p className="min-w-0 flex-1 text-[11px] leading-snug text-surface-muted">
              {beside ? `Sits beside your name on the ${spot}: the motif first, then the name.` : `Centred on the ${spot}.`}
            </p>
            <button type="button" className="flex-none text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={onClear} disabled={disabled}>
              Remove motif
            </button>
          </div>
        </>
      ) : (
        library.motifs && (
          <p className="text-[11px] leading-snug text-surface-muted">
            {modes.length < 2
              ? onlyModeLine(modes[0], spot)
              : start.mode === "emboss"
                ? comic
                  ? "Raised and bold by default in the comic look; you can cut it in instead."
                  : "Raised by default on this thin piece."
                : "Cut into the surface by default; you can raise it instead."}
            {beside ? ` It sits beside your name on the ${spot}.` : ""}
          </p>
        )
      )}
    </div>
  );
}

interface MotifPickerProps {
  motifs: readonly Motif[];
  pack?: readonly string[];
  packLabel?: string;
  value?: string;
  onPick(motif: Motif): void;
  disabled?: boolean;
}

/** The library as a keyboard-operable radio group of artwork tiles (arrows move, Space/Enter select); the pack comes first. */
function MotifPicker({ motifs, pack, packLabel, value, onPick, disabled }: MotifPickerProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const labelId = useId();
  const { picks, rest } = orderByPack(motifs, pack);
  const ordered = [...picks, ...rest];
  const picked = new Set(picks.map((m) => m.id));
  const checkedIdx = ordered.findIndex((m) => m.id === value);

  function move(from: number, dir: 1 | -1) {
    if (ordered.length === 0) return;
    const next = (from + dir + ordered.length) % ordered.length;
    const target = ordered[next];
    if (!target) return;
    refs.current[next]?.focus();
    onPick(target);
  }

  if (ordered.length === 0) return <p className="text-[11px] text-surface-muted">No motifs in the library yet.</p>;

  return (
    <div className="grid gap-1.5">
      <span id={labelId} className="text-xs text-surface-muted">
        {picks.length > 0 ? `Your motif · ${packLabel ? `${packLabel} picks` : "picks for this piece"} first` : "Your motif"}
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="grid grid-cols-[repeat(auto-fill,minmax(64px,1fr))] gap-1.5">
        {ordered.map((m, i) => {
          const checked = m.id === value;
          const isPick = picked.has(m.id);
          return (
            <button
              key={m.id}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={checkedIdx >= 0 ? (checked ? 0 : -1) : i === 0 ? 0 : -1}
              disabled={disabled}
              title={isPick && packLabel ? `${m.label} · a ${packLabel} pick` : m.label}
              className="grid content-start gap-1 rounded-control border border-surface-border p-1 text-center transition-colors duration-base ease-ak hover:border-surface-accent disabled:cursor-not-allowed disabled:opacity-50 aria-checked:border-surface-accent aria-checked:shadow-[inset_0_0_0_1px_var(--ak-accent)]"
              onClick={() => onPick(m)}
              onKeyDown={(e) => {
                if (e.key === "ArrowRight" || e.key === "ArrowDown") {
                  e.preventDefault();
                  move(i, 1);
                } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
                  e.preventDefault();
                  move(i, -1);
                } else if (e.key === " " || e.key === "Enter") {
                  e.preventDefault();
                  onPick(m);
                }
              }}
            >
              <span className="relative grid aspect-square place-items-center rounded-[8px] bg-cream p-2">
                {/* Library artwork served by the API; its host isn't known at build time, so a plain <img> is deliberate. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={motifArtUrl(m)} alt="" className="h-full w-full object-contain" loading="lazy" />
                {isPick && <span aria-hidden="true" className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-surface-accent" />}
              </span>
              <span className="truncate text-[10.5px] font-semibold leading-tight">{m.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

interface UploadPanelProps<T extends Feature> {
  anchor: TemplateAnchor;
  feature?: T;
  onPlace(feature: T): void;
  disabled?: boolean;
  review: UploadReview;
  /** Showing the dropzone over a file already on this spot; the old settings carry over to the new file. */
  replacing: boolean;
  onReplace(replacing: boolean): void;
}

function sourceOf(upload: Upload, file: File): ContentSource {
  return { upload_id: upload.id, url: upload.url ?? undefined, format: fileFormat(file.name) };
}

/** A photo in another mode: the plate itself drops its relief depth; raised or cut in gets one back. */
function withMode(relief: ReliefImage, mode: ContentMode, range: { default: number }): ReliefImage {
  if (mode === "lithophane") {
    const plate: ReliefImage = { ...relief, mode };
    delete plate.relief_mm;
    return plate;
  }
  return { ...relief, mode, relief_mm: relief.relief_mm ?? range.default };
}

/**
 * Chhavi: a photo raised or cut in where the spot allows both, at a depth within its `max_relief_mm`; on a spot that
 * takes it only as the plate itself (Roshni's night light) the photo is sent in `lithophane` mode, with no toggle and
 * no depth (the plate's own thickness range makes the picture).
 */
function ChhaviPanel({ anchor, feature: relief, onPlace, disabled, review, replacing, onReplace }: UploadPanelProps<ReliefImage>) {
  const range = reliefRange(anchor);
  const modes = anchorModes(anchor, "relief_image");
  const startMode: ContentMode = modes.includes("emboss") ? "emboss" : (modes[0] ?? "emboss");
  const spot = anchor.label.toLowerCase();
  if (!relief || replacing) {
    return (
      <div className="grid gap-2">
        <Dropzone
          kind="image"
          label={`Photo for ${anchor.label}`}
          title="Drop a photo here"
          compact
          disabled={disabled}
          onUploaded={(upload, file) => {
            useDesignStore.getState().rememberUpload(upload, file.name);
            const source = sourceOf(upload, file);
            const fresh: ReliefImage =
              startMode === "lithophane"
                ? { type: "relief_image", source, anchor: anchor.id, mode: "lithophane", fit: "contain" }
                : { type: "relief_image", source, anchor: anchor.id, mode: startMode, relief_mm: range.default, fit: "contain" };
            // A new photo keeps the relief already chosen for this spot.
            onPlace(relief ? { ...relief, source } : fresh);
          }}
        />
        {startMode === "lithophane" && <p className="text-[11px] leading-snug text-surface-muted">{LITHOPHANE_COPY}</p>}
        {relief && !review.rejected && (
          <button type="button" className={QUIET_LINK} onClick={() => onReplace(false)} disabled={disabled}>
            Keep the current photo
          </button>
        )}
      </div>
    );
  }
  const url = relief.source.url ?? review.url;
  const depth = clampDepth(relief.relief_mm ?? range.default, range);
  const mode: ContentMode = relief.mode ?? "emboss";
  return (
    <div className="grid gap-3">
      <div className="flex items-center gap-3">
        {url ? (
          // The preview is the customer's own upload, served by the API; no build-time host to configure.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={url} alt="" className="h-16 w-16 flex-none rounded-control border border-surface-border object-cover" />
        ) : (
          <div className="grid h-16 w-16 flex-none place-items-center rounded-control bg-surface-border text-xs text-surface-muted" aria-hidden="true">
            photo
          </div>
        )}
        <div className="grid min-w-0 gap-0.5 text-xs">
          <span className="truncate font-semibold">{review.name ?? "Your photo"}</span>
          <span className="text-surface-muted">{mode === "lithophane" ? `Becomes the ${spot}, lit from behind` : `Set in relief on the ${spot}`}</span>
          <button type="button" className={QUIET_LINK} onClick={() => onReplace(true)} disabled={disabled}>
            Replace photo
          </button>
        </div>
      </div>
      {modes.length > 1 ? (
        <Segmented label="Relief" options={modes.map((m) => MODE_OPTIONS[m])} value={mode} onChange={(m) => onPlace(withMode(relief, m, range))} disabled={disabled} />
      ) : mode !== "lithophane" ? (
        <p className="text-[11px] text-surface-muted">{onlyModeLine(modes[0], spot)}</p>
      ) : null}
      {mode === "lithophane" ? (
        <p className="text-[11px] leading-snug text-surface-muted">{LITHOPHANE_COPY}</p>
      ) : (
        <RangeField
          label="Depth"
          min={range.min}
          max={range.max}
          step={0.1}
          value={depth}
          format={(v) => formatMm(v, 1)}
          disabled={disabled}
          onChange={(v) => onPlace({ ...relief, relief_mm: clampDepth(v, range) })}
        />
      )}
    </div>
  );
}

function RoopPanel({ anchor, family, feature: hero, onPlace, disabled, review, replacing, onReplace, lockForm }: UploadPanelProps<HeroMesh> & { family?: Family; lockForm: boolean }) {
  const range = longestMmRange(family, anchor);
  if (!hero || replacing) {
    return (
      <div className="grid gap-2">
        <Dropzone
          kind="model"
          label={lockForm ? "Your model file" : `Model file for ${anchor.label}`}
          title="Drop your model file here"
          compact
          disabled={disabled}
          onUploaded={(upload, file) => {
            useDesignStore.getState().rememberUpload(upload, file.name);
            const source = sourceOf(upload, file);
            // A new file keeps the size and orientation already chosen.
            onPlace(
              hero
                ? { ...hero, source }
                : { type: "hero_mesh", source, anchor: anchor.id, fit: "longest", longest_mm: range.default, orientation: "as_uploaded", yaw_deg: 0 },
            );
          }}
        />
        {hero && !review.rejected && (
          <button type="button" className={QUIET_LINK} onClick={() => onReplace(false)} disabled={disabled}>
            Keep the current file
          </button>
        )}
      </div>
    );
  }
  const longest = Math.min(range.max, Math.max(range.min, hero.longest_mm ?? range.default));
  return (
    <div className="grid gap-3">
      <div className="flex items-center justify-between gap-3 text-xs">
        <span className="min-w-0 truncate font-semibold">{review.name ?? "Your model file"}</span>
        <button type="button" className="flex-none text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => onReplace(true)} disabled={disabled}>
          Replace file
        </button>
      </div>
      <RangeField
        label="Longest side"
        min={range.min}
        max={range.max}
        step={1}
        value={longest}
        format={(v) => formatMm(v)}
        hint={sizeHint(longest)}
        disabled={disabled}
        onChange={(v) => onPlace({ ...hero, fit: "longest", longest_mm: v })}
      />
      {lockForm && (
        <Segmented label="Orientation" options={ORIENTATIONS} value={hero.orientation ?? "as_uploaded"} onChange={(o) => onPlace({ ...hero, orientation: o })} disabled={disabled} />
      )}
      <p className="text-[11px] leading-snug text-surface-muted">
        {lockForm ? "We check and repair the file, then print it at this size." : `We check and repair the file, then set it on the ${anchor.label.toLowerCase()} at this size.`}
      </p>
    </div>
  );
}
