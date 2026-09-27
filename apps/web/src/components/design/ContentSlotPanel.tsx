"use client";

import { useId, useState } from "react";
import type { Family, TemplateAnchor, TemplateDescriptor, Upload } from "@/lib/api/types";
import { sizeHint } from "@/lib/families";
import {
  addWords,
  anchorAccepts,
  CHHAAP,
  clampDepth,
  contentAnchors,
  detectScript,
  featureCodename,
  featureDescriptor,
  featureLabel,
  featureOn,
  featurePhrase,
  featureSummary,
  isLiveFeature,
  longestMmRange,
  maxTextChars,
  ORIENTATIONS,
  reliefRange,
  templateTakes,
  textDepthRange,
  uploadIdOf,
  type ContentSource,
  type EmbossText,
  type Feature,
  type FeatureType,
} from "@/lib/features";
import { capitalise, formatMm, joinList } from "@/lib/format";
import { fileFormat } from "@/lib/uploads";
import { useDesignStore } from "@/store/design";
import { Dropzone } from "@/components/ui/Dropzone";
import { RangeField } from "@/components/ui/RangeField";
import { Segmented } from "@/components/ui/Segmented";
import { useUploadReview, type UploadReview } from "./useUploadReview";

export interface ContentSlotPanelProps {
  template: Pick<TemplateDescriptor, "anchors" | "features_supported">;
  /** The Avatar, for the Naam character limit and the hero form's size envelope. */
  family?: Family;
  /** At most one feature per anchor. */
  features: readonly Feature[];
  onChange(anchorId: string, feature: Feature | null): void;
  /**
   * Swaroop (a raw print): the piece is its one form, so its card reads "Your form", carries the size slider and the
   * orientation toggle, and its file can be replaced but never removed.
   */
  raw?: boolean;
  disabled?: boolean;
  className?: string;
}

const RELIEF_MODES = [
  { value: "emboss", label: "Raised", description: "Stands out from the surface" },
  { value: "deboss", label: "Cut in", description: "Sunk into the surface" },
] as const;

const ACTION_LINK = "text-left text-[11px] font-semibold underline underline-offset-2 disabled:opacity-60";
const QUIET_LINK = "justify-self-start text-left text-[11px] text-surface-muted underline-offset-2 hover:underline";

/**
 * "Chhaap · Your imprint": one card per template anchor that takes content today, with a tab per accepted type —
 * Naam (text), Chhavi (photo relief), Roop (your own 3D form, volume anchors only) and Buti (motifs, marked "soon"
 * until the motif library is live). Controlled: the parent owns the feature list (the studio's store or the
 * composer). File names and review states live in the design store, so they survive remounts; a file the studio
 * is still checking is polled until it is cleared or turned down.
 */
export function ContentSlotPanel({ template, family, features, onChange, raw, disabled, className }: ContentSlotPanelProps) {
  const headingId = useId();
  const anchors = contentAnchors(template);
  if (anchors.length === 0) return null;
  const words = addWords(templateTakes(template));
  const intro = raw
    ? "Your model file, checked, repaired and printed as it is."
    : words.length > 0
      ? `${capitalise(joinList(words, "or"))}, set into the piece.`
      : undefined;
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
          feature={featureOn(features, anchor.id)}
          onChange={onChange}
          lockForm={Boolean(raw) && (anchor.kind ?? "surface") === "volume"}
          disabled={disabled}
        />
      ))}
    </section>
  );
}

interface AnchorCardProps {
  anchor: TemplateAnchor;
  template: Pick<TemplateDescriptor, "features_supported">;
  family?: Family;
  feature?: Feature;
  onChange(anchorId: string, feature: Feature | null): void;
  /** Swaroop's one form: titled "Your form", with orientation, never left plain. */
  lockForm: boolean;
  disabled?: boolean;
}

function areaHint(anchor: TemplateAnchor): string | undefined {
  if (anchor.kind === "volume" && anchor.bounds_mm && anchor.bounds_mm.length >= 3) {
    return `Room for ${anchor.bounds_mm.slice(0, 3).map((n) => Math.round(n)).join(" × ")} mm`;
  }
  if (anchor.size_mm && anchor.size_mm.length >= 2) return `Print area ${anchor.size_mm.slice(0, 2).map((n) => Math.round(n)).join(" × ")} mm`;
  return undefined;
}

function AnchorCard({ anchor, template, family, feature, onChange, lockForm, disabled }: AnchorCardProps) {
  const accepts = anchorAccepts(anchor, template);
  const [chosen, setChosen] = useState<FeatureType>();
  const [replacing, setReplacing] = useState(false);
  const tabsId = useId();
  const review = useUploadReview(feature ? uploadIdOf(feature) : undefined);
  const active: FeatureType | undefined = chosen ?? feature?.type ?? accepts.find(isLiveFeature) ?? accepts[0];
  const title = lockForm ? "Your form" : anchor.label;
  const hint = areaHint(anchor);

  function update(next: Feature | null) {
    setReplacing(false);
    onChange(anchor.id, next);
  }

  function reupload() {
    if (feature) setChosen(feature.type);
    setReplacing(true);
  }

  return (
    <article className="ak-well grid gap-3 p-3.5">
      <header className="flex items-start justify-between gap-3">
        <div className="grid gap-0.5">
          <h3 className="text-sm font-semibold leading-tight">{title}</h3>
          {hint && <span className="text-[11px] text-surface-muted">{hint}</span>}
        </div>
        {lockForm ? null : feature ? (
          <button type="button" className="text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => update(null)} disabled={disabled}>
            Leave plain
          </button>
        ) : (
          <span className="text-[11px] text-surface-muted">Plain</span>
        )}
      </header>

      {accepts.length > 1 && (
        <div role="tablist" aria-label={`Content for ${title}`} className="flex flex-wrap gap-1.5">
          {accepts.map((type) => {
            const soon = !isLiveFeature(type);
            return (
              <button
                key={type}
                type="button"
                role="tab"
                id={`${tabsId}-${type}`}
                aria-selected={active === type}
                aria-controls={`${tabsId}-panel`}
                aria-disabled={soon || undefined}
                disabled={disabled || soon}
                title={soon ? `${featureLabel(type)} · arriving soon` : featureLabel(type)}
                className="ak-chip min-h-8 px-3 py-1 text-[11.5px]"
                onClick={() => setChosen(type)}
              >
                {featureCodename(type)}
                <span className="text-[10px] font-medium opacity-70">{soon ? "motifs arrive soon" : featureDescriptor(type)}</span>
              </button>
            );
          })}
        </div>
      )}

      <div id={`${tabsId}-panel`} role={accepts.length > 1 ? "tabpanel" : undefined} aria-labelledby={accepts.length > 1 && active ? `${tabsId}-${active}` : undefined}>
        {active === "emboss_text" && <NaamPanel anchor={anchor} family={family} feature={feature} onChange={update} disabled={disabled} />}
        {active === "relief_image" && (
          <ChhaviPanel anchor={anchor} feature={feature} onChange={update} disabled={disabled} review={review} replacing={replacing} onReplace={setReplacing} />
        )}
        {active === "hero_mesh" && (
          <RoopPanel
            anchor={anchor}
            family={family}
            feature={feature}
            onChange={update}
            disabled={disabled}
            review={review}
            replacing={replacing}
            onReplace={setReplacing}
            lockForm={lockForm}
          />
        )}
        {active === "motif" && <p className="text-xs text-surface-muted">{feature?.type === "motif" ? featureSummary(feature) : "Motifs (Buti) arrive soon."}</p>}
      </div>

      {feature && feature.type !== active && <p className="text-[11px] text-surface-muted">On this spot now: {featureSummary(feature)}. Adding something here replaces it.</p>}
      <ReviewNote review={review} what={feature?.type === "relief_image" ? "photo" : "model file"} onReupload={reupload} disabled={disabled} />
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

interface PanelProps {
  anchor: TemplateAnchor;
  feature?: Feature;
  /** Puts `feature` on this panel's anchor, or leaves the anchor plain with null. */
  onChange(feature: Feature | null): void;
  disabled?: boolean;
}

function NaamPanel({ anchor, family, feature, onChange, disabled }: PanelProps & { family?: Family }) {
  const id = useId();
  const max = maxTextChars(family);
  const range = textDepthRange(anchor);
  const current = feature?.type === "emboss_text" ? feature : undefined;
  const text = current?.text ?? "";
  const mode = current?.mode ?? "emboss";

  function update(patch: Partial<Pick<EmbossText, "text" | "mode" | "depth_mm">>) {
    const nextText = (patch.text ?? text).slice(0, max);
    // Only an empty box leaves the spot plain; spaces stay while typing and are trimmed when the piece is sent.
    if (nextText.length === 0) {
      onChange(null);
      return;
    }
    const base: EmbossText = current ?? { type: "emboss_text", text: "", anchor: anchor.id, depth_mm: range.default, mode: "emboss" };
    const next: EmbossText = { ...base, ...patch, text: nextText };
    const script = detectScript(next.text);
    if (script) next.script = script;
    else delete next.script;
    onChange(next);
  }

  return (
    <div className="grid gap-2.5">
      <label htmlFor={id} className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
        <span>Your {featurePhrase("emboss_text")}</span>
        <span aria-live="polite" className={text.length >= max ? "font-semibold text-warning" : ""}>
          {text.length} / {max}
        </span>
      </label>
      <input
        id={id}
        type="text"
        className="ak-input min-h-10 py-1.5 text-sm"
        value={text}
        maxLength={max}
        placeholder="Asha"
        autoComplete="off"
        disabled={disabled}
        onChange={(e) => update({ text: e.target.value })}
      />
      <Segmented label="Letters" options={RELIEF_MODES} value={mode} onChange={(m) => update({ mode: m })} disabled={disabled || !current} />
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
      </p>
    </div>
  );
}

interface UploadPanelProps extends PanelProps {
  review: UploadReview;
  /** Showing the dropzone over a file already on this spot; the old settings carry over to the new file. */
  replacing: boolean;
  onReplace(replacing: boolean): void;
}

function sourceOf(upload: Upload, file: File): ContentSource {
  return { upload_id: upload.id, url: upload.url ?? undefined, format: fileFormat(file.name) };
}

function ChhaviPanel({ anchor, feature, onChange, disabled, review, replacing, onReplace }: UploadPanelProps) {
  const relief = feature?.type === "relief_image" ? feature : undefined;
  const range = reliefRange(anchor);
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
            // A new photo keeps the relief already chosen for this spot.
            onChange(relief ? { ...relief, source } : { type: "relief_image", source, anchor: anchor.id, mode: "emboss", relief_mm: range.default, fit: "contain" });
          }}
        />
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
          <span className="text-surface-muted">Set in relief on the {anchor.label.toLowerCase()}</span>
          <button type="button" className={QUIET_LINK} onClick={() => onReplace(true)} disabled={disabled}>
            Replace photo
          </button>
        </div>
      </div>
      {relief.mode === "lithophane" ? (
        <p className="text-[11px] text-surface-muted">Lithophane: light through the plate draws the picture.</p>
      ) : (
        <Segmented label="Relief" options={RELIEF_MODES} value={relief.mode === "deboss" ? "deboss" : "emboss"} onChange={(m) => onChange({ ...relief, mode: m })} disabled={disabled} />
      )}
      <RangeField
        label="Depth"
        min={range.min}
        max={range.max}
        step={0.1}
        value={depth}
        format={(v) => formatMm(v, 1)}
        disabled={disabled}
        onChange={(v) => onChange({ ...relief, relief_mm: clampDepth(v, range) })}
      />
    </div>
  );
}

function RoopPanel({ anchor, family, feature, onChange, disabled, review, replacing, onReplace, lockForm }: UploadPanelProps & { family?: Family; lockForm: boolean }) {
  const hero = feature?.type === "hero_mesh" ? feature : undefined;
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
            onChange(
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
        onChange={(v) => onChange({ ...hero, fit: "longest", longest_mm: v })}
      />
      {lockForm && (
        <Segmented label="Orientation" options={ORIENTATIONS} value={hero.orientation ?? "as_uploaded"} onChange={(o) => onChange({ ...hero, orientation: o })} disabled={disabled} />
      )}
      <p className="text-[11px] leading-snug text-surface-muted">
        {lockForm ? "We check and repair the file, then print it at this size." : `We check and repair the file, then set it on the ${anchor.label.toLowerCase()} at this size.`}
      </p>
    </div>
  );
}
