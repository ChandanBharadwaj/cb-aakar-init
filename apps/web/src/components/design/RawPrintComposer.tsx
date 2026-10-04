"use client";

import { useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Family, Material, Problem } from "@/lib/api/types";
import { allowedMaterialIds, defaultTemplate, envelopeLine, sizeHint } from "@/lib/families";
import { familyLabel, longestMmRange, ORIENTATIONS, type HeroMesh, type Orientation } from "@/lib/features";
import { formatMm } from "@/lib/format";
import { fileFormat, fileTitle, formatBytes, REJECTED_COPY } from "@/lib/uploads";
import { FALLBACK_MATERIALS } from "@/lib/viewer/materials";
import { useDesignStore } from "@/store/design";
import { Dropzone } from "@/components/ui/Dropzone";
import { FinishChips } from "@/components/ui/FinishChips";
import { RangeField } from "@/components/ui/RangeField";
import { Segmented } from "@/components/ui/Segmented";
import { useUploadReview } from "./useUploadReview";

export interface RawPrintComposerProps {
  family: Family;
  materials: Material[];
  prompt?: string;
}

interface ChosenFile {
  uploadId: string;
  name: string;
  bytes: number;
  format: string;
}

/**
 * Swaroop, "Print as it is": a model-file dropzone, a size slider inside the family's envelope, an
 * orientation toggle and finish chips. `raw_print@1` has no template params, so the size and orientation travel
 * on its one hero form: "Check and price" → `POST /api/designs {source: "upload", family_id: "raw_print",
 * params: {}, features: [{type: "hero_mesh", anchor, fit: "longest", longest_mm, orientation, yaw_deg: 0}],
 * material}` → the studio, where the stability check and the price come back like any other piece. A file the
 * studio is still checking is polled until it is cleared (the button opens) or turned down (choose another).
 */
export function RawPrintComposer({ family, materials: materialsProp, prompt }: RawPrintComposerProps) {
  const router = useRouter();
  const titleId = useId();
  const materials = materialsProp.length > 0 ? materialsProp : FALLBACK_MATERIALS;
  const template = defaultTemplate(family);
  const anchor = template?.anchors.find((a) => a.kind === "volume") ?? template?.anchors[0];
  const range = useMemo(() => longestMmRange(family, anchor), [family, anchor]);
  const allowed = useMemo(() => allowedMaterialIds(materials, family, template), [materials, family, template]);

  const [file, setFile] = useState<ChosenFile>();
  const review = useUploadReview(file?.uploadId);
  const [longest, setLongest] = useState(range.default);
  const [orientation, setOrientation] = useState<Orientation>("as_uploaded");
  const [materialId, setMaterialId] = useState<string | undefined>(allowed[0]);
  const [title, setTitle] = useState(() => (prompt ?? "").slice(0, 80));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();

  const held = review.status === "pending_review" || review.status === "rejected";
  const canCheck = Boolean(file && materialId) && !held && !busy;
  const workingTitle = (file ? fileTitle(file.name) : "") || familyLabel(family);

  function chooseAgain() {
    setFile(undefined);
    setProblem(undefined);
  }

  async function check() {
    if (!file || !materialId || busy || held) return;
    setBusy(true);
    setProblem(undefined);
    // Size and orientation live on the feature; Swaroop's template takes no params.
    const hero: HeroMesh = {
      type: "hero_mesh",
      source: { upload_id: file.uploadId, url: review.url, format: fileFormat(file.name) },
      anchor: anchor?.id ?? "body",
      fit: "longest",
      longest_mm: longest,
      orientation,
      yaw_deg: 0,
    };
    try {
      const accepted = await api.designs.create({
        source: "upload",
        family_id: family.id,
        params: {},
        features: [hero],
        material: materialId,
        title: title.trim() || workingTitle,
      });
      router.push(`/design/${accepted.design_id}?job=${encodeURIComponent(accepted.job_id)}`);
    } catch (err) {
      setProblem(toProblem(err));
      setBusy(false);
    }
  }

  const fileState = review.checking || review.stalled ? " · being checked" : review.rejected ? " · can't be printed" : " · received";

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section className="ak-card grid content-start gap-6 p-6 sm:p-8" aria-labelledby="swaroop-heading">
        <header className="grid gap-2">
          <span className="ak-eyebrow">Your own 3D model</span>
          <h1 id="swaroop-heading" className="font-display text-4xl font-semibold leading-none">
            {family.codename} <span className="text-surface-muted">· {family.name}</span>
          </h1>
          <p className="max-w-prose text-sm leading-relaxed">{family.description ?? "Upload the file from your 3D program. We check it, size it and print it as it is."}</p>
        </header>

        {family.available === false || family.ready === false ? (
          <p className="ak-well p-5 text-sm text-surface-muted">Printing your own model file isn&apos;t open right now. Check back soon.</p>
        ) : (
          <>
            <div className="grid gap-2">
              <span className="ak-label">1 · Your model file</span>
              {file ? (
                <div className="ak-well flex flex-wrap items-center justify-between gap-3 p-3.5 text-sm">
                  <div className="grid min-w-0 gap-0.5">
                    <span className="truncate font-semibold">{file.name}</span>
                    <span className="text-[11px] text-surface-muted">
                      {formatBytes(file.bytes)} · {file.format.toUpperCase()}
                      {fileState}
                    </span>
                  </div>
                  <button type="button" className="ak-btn ak-btn-secondary min-h-9 px-4 py-1.5 text-xs" onClick={chooseAgain} disabled={busy}>
                    Replace file
                  </button>
                </div>
              ) : (
                <Dropzone
                  kind="model"
                  label="Your model file"
                  title="Drop your model file here"
                  hint="Upload the file from your 3D program (.stl, .obj, .3mf) · up to 50 MB"
                  onUploaded={(upload, f) => {
                    useDesignStore.getState().rememberUpload(upload, f.name);
                    setFile({ uploadId: upload.id, name: f.name, bytes: upload.bytes, format: upload.format });
                    setProblem(undefined);
                  }}
                  disabled={busy}
                />
              )}
              {review.checking && (
                <p role="status" className="text-[12px] text-warning">
                  The studio is checking this file. &ldquo;Check and price&rdquo; opens as soon as it&apos;s cleared.
                </p>
              )}
              {review.stalled && (
                <div role="status" className="grid gap-1.5 text-[12px] text-warning">
                  <p>The studio is still checking this file; it can take a little longer.</p>
                  <div className="flex flex-wrap gap-2">
                    <button type="button" className="ak-btn ak-btn-secondary min-h-9 px-4 py-1.5 text-xs" onClick={review.recheck} disabled={busy}>
                      Check again
                    </button>
                    <button type="button" className="ak-btn ak-btn-secondary min-h-9 px-4 py-1.5 text-xs" onClick={chooseAgain} disabled={busy}>
                      Try a different file
                    </button>
                  </div>
                </div>
              )}
              {review.rejected && (
                <div role="status" className="grid gap-1.5 text-[12px] text-warning">
                  <p>{review.message}</p>
                  <button type="button" className="ak-btn ak-btn-secondary min-h-9 justify-self-start px-4 py-1.5 text-xs" onClick={chooseAgain} disabled={busy}>
                    Upload a different file
                  </button>
                </div>
              )}
            </div>

            <div className="grid gap-3">
              <span className="ak-label">2 · Size</span>
              <RangeField
                label="Longest side"
                min={range.min}
                max={range.max}
                step={1}
                value={longest}
                format={(v) => formatMm(v)}
                hint={sizeHint(longest)}
                onChange={setLongest}
                disabled={busy}
              />
              <Segmented label="Orientation" options={ORIENTATIONS} value={orientation} onChange={(v) => setOrientation(v)} disabled={busy} />
            </div>

            <div className="grid gap-2">
              <span className="ak-label">3 · Finish</span>
              <FinishChips materials={materials} value={materialId} onChange={setMaterialId} allowed={allowed} disabled={busy} />
            </div>

            <div className="grid gap-1.5">
              <label htmlFor={titleId} className="text-xs text-surface-muted">
                Name your piece <span className="opacity-70">(optional)</span>
              </label>
              <input id={titleId} type="text" className="ak-input" value={title} maxLength={80} placeholder={workingTitle} onChange={(e) => setTitle(e.target.value)} disabled={busy} />
            </div>

            <div className="grid gap-2">
              <button type="button" className="ak-btn ak-btn-primary ak-btn-pill justify-self-start px-8" onClick={check} disabled={!canCheck} aria-busy={busy}>
                {busy ? "Sending to the studio…" : "Check and price"}
              </button>
              <p className="text-[12px] text-surface-muted">
                {!file
                  ? "Add your model file to check and price it."
                  : held
                    ? "Check and price opens once the studio has cleared your file."
                    : "We check the walls and balance first; the price follows in the studio."}
              </p>
              {problem && (
                <p role="alert" className="text-xs text-danger">
                  {problem.code === "upload_not_ready"
                    ? "The studio is still checking this file."
                    : problem.code === "upload_rejected"
                      ? REJECTED_COPY
                      : problem.code === "content_unusable"
                        ? "We couldn't repair this file. Try another export from your 3D program."
                        : (problem.detail ?? problem.title)}
                </p>
              )}
            </div>
          </>
        )}
      </section>

      <aside className="ak-card grid content-start gap-4 p-5" aria-label="How it works">
        <h2 className="ak-label">How it works</h2>
        <ol className="grid gap-3 text-sm">
          <li className="grid gap-0.5">
            <span className="font-semibold">We check it</span>
            <span className="text-surface-muted">Walls, balance and fit on the printer, the same check every piece gets.</span>
          </li>
          <li className="grid gap-0.5">
            <span className="font-semibold">You size it</span>
            <span className="text-surface-muted">{envelopeLine(family) ?? "Choose the longest side."} Layer height and infill are the studio&apos;s call.</span>
          </li>
          <li className="grid gap-0.5">
            <span className="font-semibold">We price it</span>
            <span className="text-surface-muted">Material, print time, finishing and a small set-up charge for checking your file.</span>
          </li>
        </ol>
        <p className="text-[11px] leading-snug text-surface-muted">Files stay yours. If a wall comes out thinner than 1.2 mm we&apos;ll say so before you pay, and a larger size usually fixes it.</p>
      </aside>
    </div>
  );
}
