"use client";

import { useId, useRef, useState, type DragEvent } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Upload, UploadKind } from "@/lib/api/types";
import { acceptFor, fileProblem, formatBytes, formatHint } from "@/lib/uploads";

export interface DropzoneProps {
  kind: UploadKind;
  /** Accessible name, e.g. "Photo for the face". */
  label: string;
  /** Heading copy, e.g. "Drop a photo here". */
  title: string;
  /** Second line; defaults to the formats and size cap for the kind. */
  hint?: string;
  onUploaded(upload: Upload, file: File): void;
  disabled?: boolean;
  compact?: boolean;
  className?: string;
}

type State = { status: "idle" } | { status: "uploading"; name: string; bytes: number } | { status: "error"; message: string };

/**
 * A drag-and-drop file target that uploads straight to `POST /api/uploads` (multipart `file` + `kind`).
 * Keyboard users get a real button; the hidden input carries the `accept` list. Files the API would
 * refuse (format, size) are stopped in the browser with a plain-language reason.
 */
export function Dropzone({ kind, label, title, hint, onUploaded, disabled, compact, className }: DropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<State>({ status: "idle" });
  const [over, setOver] = useState(false);
  const hintId = useId();
  const busy = state.status === "uploading";

  async function send(file: File | undefined) {
    if (!file || disabled || busy) return;
    const reason = fileProblem(file, kind);
    if (reason) {
      setState({ status: "error", message: reason });
      return;
    }
    setState({ status: "uploading", name: file.name, bytes: file.size });
    try {
      const upload = await api.uploads.create(file, kind);
      if (upload.status === "rejected") {
        setState({ status: "error", message: kind === "image" ? "We can't use this photo. Try another one." : "We can't print this file. Try another export from your 3D program." });
        return;
      }
      setState({ status: "idle" });
      onUploaded(upload, file);
    } catch (err) {
      const p = toProblem(err);
      setState({
        status: "error",
        message:
          p.code === "payload_too_large"
            ? "That file is too large for the studio."
            : p.code === "unsupported_format"
              ? kind === "image"
                ? "That format isn't one we can read. Try a PNG, JPG, WEBP or HEIC."
                : "That format isn't one we can print. Save it as .stl, .obj or .3mf."
              : (p.detail ?? p.title ?? "The upload didn't go through."),
      });
    }
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setOver(false);
    void send(e.dataTransfer.files?.[0]);
  }

  return (
    <div
      className={[
        "ak-well grid place-items-center gap-2 border border-dashed text-center transition-colors duration-base ease-ak",
        compact ? "p-4" : "p-6",
        over ? "border-surface-accent ak-ring-accent" : "border-surface-border",
        disabled ? "opacity-60" : "",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      onDragOver={(e) => {
        if (disabled || busy) return;
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
      aria-busy={busy}
    >
      <input
        ref={inputRef}
        type="file"
        accept={acceptFor(kind)}
        className="sr-only"
        aria-label={label}
        aria-describedby={hintId}
        disabled={disabled || busy}
        onChange={(e) => {
          void send(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
      {busy ? (
        <>
          <span className="font-semibold">Sending {state.name}…</span>
          <span id={hintId} className="text-[11px] text-surface-muted">
            {formatBytes(state.bytes)}
          </span>
        </>
      ) : (
        <>
          <span className="font-semibold">{title}</span>
          <span id={hintId} className="text-[11px] leading-snug text-surface-muted">
            {hint ?? formatHint(kind)}
          </span>
          <button type="button" className="ak-btn ak-btn-secondary min-h-9 px-4 py-1.5 text-xs" onClick={() => inputRef.current?.click()} disabled={disabled}>
            {kind === "image" ? "Choose a photo" : "Choose a model file"}
          </button>
        </>
      )}
      {state.status === "error" && (
        <p role="alert" className="text-xs text-danger">
          {state.message}
        </p>
      )}
    </div>
  );
}
