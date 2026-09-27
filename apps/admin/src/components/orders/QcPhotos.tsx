"use client";

import { useRef, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { MediaAsset, Problem } from "@/lib/api/types";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Field } from "@/components/ui/Field";
import { ProblemCard } from "@/components/ui/ProblemCard";

export interface QcPhotosProps {
  orderId: string;
  photos: MediaAsset[];
  onUploaded(asset: MediaAsset): void;
}

export function QcPhotos({ orderId, photos, onUploaded }: QcPhotosProps) {
  const [file, setFile] = useState<File>();
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const input = useRef<HTMLInputElement>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      setProblem({ title: "Too large", detail: "Photos must be 10 MB or smaller.", code: "payload_too_large" });
      return;
    }
    setBusy(true);
    setProblem(undefined);
    try {
      const asset = await api.orders.uploadQcPhoto(orderId, file, note.trim() || undefined);
      onUploaded(asset);
      setFile(undefined);
      setNote("");
      if (input.current) input.current.value = "";
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="ak-card grid gap-4 p-5" aria-labelledby="qc-heading">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="qc-heading" className="font-display text-2xl font-semibold">
          QC photos
        </h2>
        <span className="text-xs text-surface-muted">{photos.length} attached</span>
      </div>
      {photos.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {photos.map((p) => (
            <li key={p.id} className="ak-well grid gap-2 overflow-hidden p-2">
              <a href={p.url} target="_blank" rel="noreferrer" className="block aspect-[4/3] overflow-hidden rounded-[8px] bg-sand">
                {p.content_type.startsWith("image/") ? (
                  // Photos come from the API's object store; hosts aren't known at build time, so a plain <img> is deliberate.
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={p.url} alt={p.note ?? "QC photo"} className="h-full w-full object-cover" loading="lazy" />
                ) : (
                  <span className="grid h-full place-items-center text-xs text-surface-muted">{p.content_type}</span>
                )}
              </a>
              <div className="grid gap-0.5 px-1 text-xs">
                <span className="font-medium">{p.note ?? <span className="text-surface-muted">No note</span>}</span>
                <span className="text-surface-muted">
                  {formatBytes(p.bytes)} · {formatDateTime(p.created_at)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end" aria-busy={busy}>
        <Field label="Photo" hint="JPEG or PNG, up to 10 MB">
          {(id) => <input id={id} ref={input} type="file" accept="image/*" className="ak-input ak-input-sm file:mr-3 file:rounded-pill file:border-0 file:bg-sand file:px-3 file:py-1 file:text-xs file:font-semibold" onChange={(e) => setFile(e.target.files?.[0])} disabled={busy} />}
        </Field>
        <Field label="Note" hint="Optional, up to 200 characters">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} disabled={busy} placeholder="Front, under daylight" />}
        </Field>
        <button type="submit" className="ak-btn ak-btn-secondary ak-btn-sm mb-5" disabled={!file || busy}>
          {busy ? "Uploading…" : "Attach photo"}
        </button>
      </form>
      {problem && <ProblemCard compact problem={problem} />}
    </section>
  );
}
