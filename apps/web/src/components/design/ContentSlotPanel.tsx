"use client";

import { useId, useState } from "react";
import type { Family, TemplateAnchor, TemplateDescriptor, Upload } from "@/lib/api/types";
import { sizeHint } from "@/lib/families";
import {
  anchorAccepts,
  clampReliefMm,
  contentAnchors,
  defaultReliefMm,
  defaultTextDepthMm,
  detectScript,
  featureDescriptor,
  featureLabel,
  featureOn,
  featureSummary,
  featureTitle,
  longestMmRange,
  maxReliefMm,
  maxTextChars,
  RELIEF_MM,
  type EmbossText,
  type Feature,
  type FeatureType,
  type HeroMesh,
  type ReliefImage,
} from "@/lib/features";
import { formatMm } from "@/lib/format";
import { fileFormat } from "@/lib/uploads";
import { Dropzone } from "@/components/ui/Dropzone";
import { RangeField } from "@/components/ui/RangeField";
import { Segmented } from "@/components/ui/Segmented";

export interface ContentSlotPanelProps {
  template: Pick<TemplateDescriptor, "anchors" | "features_supported">;
  /** The Avatar, for the Naam character limit and the hero form's size envelope. */
  family?: Family;
  /** At most one feature per anchor. */
  features: readonly Feature[];
  onChange(anchorId: string, feature: Feature | null): void;
  disabled?: boolean;
  className?: string;
}

const RELIEF_MODES = [
  { value: "emboss", label: "Raised", description: "Stands out from the surface" },
  { value: "deboss", label: "Cut in", description: "Sunk into the surface" },
] as const;

/**
 * "Your Chhaap": one card per template anchor that takes content, with a tab per accepted type —
 * Naam (text), Chhavi (photo relief), Roop (your own 3D form, volume anchors only) and Buti (motifs,
 * arriving later). Controlled: the parent owns the feature list (the studio's store or the composer).
 */
export function ContentSlotPanel({ template, family, features, onChange, disabled, className }: ContentSlotPanelProps) {
  const headingId = useId();
  const anchors = contentAnchors(template);
  if (anchors.length === 0) return null;
  return (
    <section className={["grid gap-3", className].filter(Boolean).join(" ")} aria-labelledby={headingId}>
      <div className="grid gap-0.5">
        <h2 id={headingId} className="ak-label">
          Your Chhaap
        </h2>
        <p className="text-[11px] leading-snug text-surface-muted">Your name, a photo or your own form, set into the piece.</p>
      </div>
      {anchors.map((anchor) => (
        <AnchorCard key={anchor.id} anchor={anchor} template={template} family={family} feature={featureOn(features, anchor.id)} onChange={onChange} disabled={disabled} />
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
  disabled?: boolean;
}

function areaHint(anchor: TemplateAnchor): string | undefined {
  if (anchor.kind === "volume" && anchor.bounds_mm && anchor.bounds_mm.length >= 3) {
    return `Room for ${anchor.bounds_mm.slice(0, 3).map((n) => Math.round(n)).join(" × ")} mm`;
  }
  if (anchor.size_mm && anchor.size_mm.length >= 2) return `Print area ${anchor.size_mm.slice(0, 2).map((n) => Math.round(n)).join(" × ")} mm`;
  return undefined;
}

function AnchorCard({ anchor, template, family, feature, onChange, disabled }: AnchorCardProps) {
  const accepts = anchorAccepts(anchor, template);
  const [chosen, setChosen] = useState<FeatureType>();
  const [fileNames, setFileNames] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState<string>();
  const tabsId = useId();
  const active: FeatureType | undefined = chosen ?? feature?.type ?? accepts.find((t) => t !== "motif") ?? accepts[0];

  function uploaded(upload: Upload, file: File) {
    setFileNames((names) => ({ ...names, [upload.id]: file.name }));
    setNotice(upload.status === "pending_review" ? "This file is with a reviewer. You can sculpt once it's approved." : undefined);
  }
  const uploadName = feature && "source" in feature ? fileNames[feature.source.upload_id] : undefined;

  return (
    <article className="ak-well grid gap-3 p-3.5">
      <header className="flex items-start justify-between gap-3">
        <div className="grid gap-0.5">
          <h3 className="text-sm font-semibold leading-tight">{anchor.label}</h3>
          {areaHint(anchor) && <span className="text-[11px] text-surface-muted">{areaHint(anchor)}</span>}
        </div>
        {feature ? (
          <button type="button" className="text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => onChange(anchor.id, null)} disabled={disabled}>
            Leave plain
          </button>
        ) : (
          <span className="text-[11px] text-surface-muted">Plain</span>
        )}
      </header>

      {accepts.length > 1 && (
        <div role="tablist" aria-label={`Content for ${anchor.label}`} className="flex flex-wrap gap-1.5">
          {accepts.map((type) => {
            const soon = type === "motif";
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
                title={soon ? "Buti · motifs arrive soon" : featureTitle(type)}
                className="ak-chip min-h-8 px-3 py-1 text-[11.5px]"
                onClick={() => setChosen(type)}
              >
                {featureLabel(type)}
                <span className="text-[10px] font-medium opacity-70">{soon ? "motifs arrive soon" : featureDescriptor(type)}</span>
              </button>
            );
          })}
        </div>
      )}

      <div id={`${tabsId}-panel`} role={accepts.length > 1 ? "tabpanel" : undefined} aria-labelledby={accepts.length > 1 && active ? `${tabsId}-${active}` : undefined}>
        {active === "emboss_text" && <NaamPanel anchor={anchor} family={family} feature={feature} onChange={onChange} disabled={disabled} />}
        {active === "relief_image" && <ChhaviPanel anchor={anchor} feature={feature} onChange={onChange} disabled={disabled} fileName={uploadName} onUploaded={uploaded} />}
        {active === "hero_mesh" && <RoopPanel anchor={anchor} family={family} feature={feature} onChange={onChange} disabled={disabled} fileName={uploadName} onUploaded={uploaded} />}
        {active === "motif" && (
          <p className="text-xs text-surface-muted">
            {feature?.type === "motif" ? featureSummary(feature) : `${featureLabel("motif")} · motifs arrive soon.`}
          </p>
        )}
      </div>

      {feature && feature.type !== active && <p className="text-[11px] text-surface-muted">On this spot now: {featureSummary(feature)}. Adding something here replaces it.</p>}
      {notice && (
        <p role="status" className="text-[11px] leading-snug text-warning">
          {notice}
        </p>
      )}
    </article>
  );
}

interface PanelProps {
  anchor: TemplateAnchor;
  feature?: Feature;
  onChange(anchorId: string, feature: Feature | null): void;
  disabled?: boolean;
}

function NaamPanel({ anchor, family, feature, onChange, disabled }: PanelProps & { family?: Family }) {
  const id = useId();
  const max = maxTextChars(family);
  const current = feature?.type === "emboss_text" ? feature : undefined;
  const text = current?.text ?? "";
  const mode = current?.mode ?? "emboss";

  function update(nextText: string, nextMode: "emboss" | "deboss") {
    if (nextText.trim().length === 0) {
      onChange(anchor.id, null);
      return;
    }
    const base: EmbossText = current ?? { type: "emboss_text", text: "", anchor: anchor.id, depth_mm: defaultTextDepthMm(anchor) };
    const next: EmbossText = { ...base, text: nextText.slice(0, max), mode: nextMode };
    const script = detectScript(next.text);
    if (script) next.script = script;
    else delete next.script;
    onChange(anchor.id, next);
  }

  return (
    <div className="grid gap-2.5">
      <label htmlFor={id} className="flex items-baseline justify-between gap-3 text-xs text-surface-muted">
        <span>Your {featureLabel("emboss_text")}</span>
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
        onChange={(e) => update(e.target.value, mode)}
      />
      <Segmented label="Letters" options={RELIEF_MODES} value={mode} onChange={(m) => update(text, m)} disabled={disabled || text.length === 0} />
      <p className="text-[11px] leading-snug text-surface-muted">
        Latin or any Indian script.
        {anchor.max_text_height_mm ? ` Letters up to ${formatMm(anchor.max_text_height_mm)} tall.` : ""}
      </p>
    </div>
  );
}

interface UploadPanelProps extends PanelProps {
  fileName?: string;
  onUploaded(upload: Upload, file: File): void;
}

function ChhaviPanel({ anchor, feature, onChange, disabled, fileName, onUploaded }: UploadPanelProps) {
  const relief = feature?.type === "relief_image" ? feature : undefined;
  if (!relief) {
    return (
      <Dropzone
        kind="image"
        label={`Photo for ${anchor.label}`}
        title="Drop a photo here"
        compact
        disabled={disabled}
        onUploaded={(upload, file) => {
          onUploaded(upload, file);
          const next: ReliefImage = {
            type: "relief_image",
            source: { upload_id: upload.id, url: upload.url ?? undefined, format: fileFormat(file.name) },
            anchor: anchor.id,
            mode: "emboss",
            relief_mm: defaultReliefMm(anchor),
            fit: "contain",
          };
          onChange(anchor.id, next);
        }}
      />
    );
  }
  const url = relief.source.url;
  const depth = relief.relief_mm ?? defaultReliefMm(anchor);
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
        <div className="grid gap-0.5 text-xs">
          <span className="font-semibold">{fileName ?? "Your photo"}</span>
          <span className="text-surface-muted">Set in relief on the {anchor.label.toLowerCase()}</span>
          <button type="button" className="text-left text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => onChange(anchor.id, null)} disabled={disabled}>
            Replace photo
          </button>
        </div>
      </div>
      {relief.mode === "lithophane" ? (
        <p className="text-[11px] text-surface-muted">Lithophane: light through the plate draws the picture.</p>
      ) : (
        <Segmented label="Relief" options={RELIEF_MODES} value={relief.mode === "deboss" ? "deboss" : "emboss"} onChange={(m) => onChange(anchor.id, { ...relief, mode: m })} disabled={disabled} />
      )}
      <RangeField
        label="Depth"
        min={RELIEF_MM.min}
        max={maxReliefMm(anchor)}
        step={0.1}
        value={depth}
        format={(v) => formatMm(v, 1)}
        disabled={disabled}
        onChange={(v) => onChange(anchor.id, { ...relief, relief_mm: clampReliefMm(v, anchor) })}
      />
    </div>
  );
}

function RoopPanel({ anchor, family, feature, onChange, disabled, fileName, onUploaded }: UploadPanelProps & { family?: Family }) {
  const hero = feature?.type === "hero_mesh" ? feature : undefined;
  const range = longestMmRange(family, anchor);
  if (!hero) {
    return (
      <Dropzone
        kind="model"
        label={`Model file for ${anchor.label}`}
        title="Drop your model file here"
        compact
        disabled={disabled}
        onUploaded={(upload, file) => {
          onUploaded(upload, file);
          const next: HeroMesh = {
            type: "hero_mesh",
            source: { upload_id: upload.id, url: upload.url ?? undefined, format: fileFormat(file.name) },
            anchor: anchor.id,
            fit: "longest",
            longest_mm: range.default,
            orientation: "as_uploaded",
          };
          onChange(anchor.id, next);
        }}
      />
    );
  }
  const longest = hero.longest_mm ?? range.default;
  return (
    <div className="grid gap-3">
      <div className="flex items-center justify-between gap-3 text-xs">
        <span className="font-semibold">{fileName ?? "Your form"}</span>
        <button type="button" className="text-[11px] text-surface-muted underline-offset-2 hover:underline" onClick={() => onChange(anchor.id, null)} disabled={disabled}>
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
        onChange={(v) => onChange(anchor.id, { ...hero, fit: "longest", longest_mm: v })}
      />
      <p className="text-[11px] leading-snug text-surface-muted">We check and repair the file, then set it on the {anchor.label.toLowerCase()} at this size.</p>
    </div>
  );
}
