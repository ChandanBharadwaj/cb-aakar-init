"use client";

import Link from "next/link";
import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminUpload, Problem, ReviewDecision, UploadStatus } from "@/lib/api/types";
import { formatBytes, formatDateTime, formatRelative } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

const FILTERS: readonly { id: UploadStatus; label: string }[] = [
  { id: "pending_review", label: "Pending review" },
  { id: "ready", label: "Approved" },
  { id: "rejected", label: "Rejected" },
];

/** Reviewer copy for the common case (Katha guardrail): trademarked heroes are out, the customer's own hero is in. */
export const REJECTION_PRESET = "We can't print copyrighted heroes, but your own hero is welcome";

const STATUS_TONE: Record<UploadStatus, "success" | "warning" | "danger"> = { ready: "success", pending_review: "warning", rejected: "danger" };
const STATUS_LABEL: Record<UploadStatus, string> = { ready: "Ready", pending_review: "Pending review", rejected: "Rejected" };

function ownerLabel(u: AdminUpload): string {
  const o = u.owner;
  if (!o) return "Unknown owner";
  if (o.phone) return o.phone;
  if (o.user_id) return `Customer ${o.user_id.slice(0, 8)}`;
  if (o.guest_id) return `Guest ${o.guest_id.slice(0, 8)}`;
  return "Unknown owner";
}

export function ReviewsPage() {
  const [status, setStatus] = useState<UploadStatus>("pending_review");
  const { data, problem, loading, reload } = useQuery(() => api.uploads.list(status), `uploads:${status}`);
  const [notice, setNotice] = useState<string>();

  function decided(updated: AdminUpload, decision: ReviewDecision) {
    setNotice(`${decision === "approved" ? "Approved" : "Rejected"} · ${updated.kind === "image" ? "image" : "model file"} ${updated.id.slice(0, 8)} is now ${STATUS_LABEL[updated.status].toLowerCase()}.`);
    void reload();
  }

  return (
    <>
      <PageHeader
        eyebrow="Content"
        title="Reviews"
        description={
          <>
            Customer uploads the content scanner flagged: photos for a Chhavi relief and model files for Roop or Swaroop whose file name mentions one of the{" "}
            <Link href="/content-rules" className="text-surface-accent hover:underline">
              content rules
            </Link>
            . Approve to release the upload into its design; reject with a short note the customer sees. Studio and owner accounts may decide.
          </>
        }
        actions={
          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => void reload()} disabled={loading}>
            Refresh
          </button>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-2" role="group" aria-label="Filter uploads by status">
        {FILTERS.map((f) => (
          <button key={f.id} type="button" className="ak-chip" aria-pressed={status === f.id} onClick={() => setStatus(f.id)}>
            {f.label}
          </button>
        ))}
      </div>
      {notice && (
        <p className="ak-well mb-4 p-3 text-sm" role="status">
          {notice}
        </p>
      )}
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.length === 0 ? (
        <EmptyState title={status === "pending_review" ? "Nothing waiting for review" : `No ${STATUS_LABEL[status].toLowerCase()} uploads`}>
          {status === "pending_review" ? "Flagged uploads land here the moment the scanner holds them back." : "Switch the filter to see other uploads."}
        </EmptyState>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy={loading}>
          {data.map((u) => (
            <li key={u.id}>
              <UploadCard upload={u} onDecided={decided} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function UploadCard({ upload: u, onDecided }: { upload: AdminUpload; onDecided(updated: AdminUpload, decision: ReviewDecision): void }) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState<ReviewDecision>();
  const [problem, setProblem] = useState<Problem>();
  const review = u.review ?? null;
  const pending = review?.status === "pending" && Boolean(review.id);

  async function decide(decision: ReviewDecision) {
    if (!review?.id) return;
    setBusy(decision);
    setProblem(undefined);
    try {
      onDecided(await api.reviews.decide(review.id, decision, note), decision);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(undefined);
    }
  }

  return (
    <article className="ak-card grid gap-3 p-4" aria-label={`${u.kind === "image" ? "Image" : "Model file"} upload ${u.id.slice(0, 8)}`}>
      {u.kind === "image" && u.url ? (
        <div className="ak-well grid h-44 place-items-center overflow-hidden">
          {/* Uploads come from the API's object store (or a data URL in the mock); hosts aren't known at build time, so a plain <img> is deliberate. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={u.url} alt={`Uploaded ${u.format.toUpperCase()} image awaiting review`} className="h-full w-full object-contain" loading="lazy" />
        </div>
      ) : (
        <div className="ak-well grid h-44 place-items-center text-center">
          <div className="grid gap-1">
            <span className="font-display text-3xl font-semibold uppercase leading-none">{u.format}</span>
            <span className="text-xs text-surface-muted">{u.kind === "image" ? "Image · no preview yet" : "Model file"} · {formatBytes(u.bytes)}</span>
            {u.url && (
              <a href={u.url} target="_blank" rel="noreferrer" className="text-xs text-surface-accent hover:underline">
                Open file
              </a>
            )}
          </div>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Pill tone={STATUS_TONE[u.status]}>{STATUS_LABEL[u.status]}</Pill>
        <Pill>{u.kind === "image" ? "Image" : "Model file"}</Pill>
        {u.origin === "generated" && <Pill tone="info">Generated</Pill>}
        <span className="ml-auto text-[11px] text-surface-muted" title={formatDateTime(u.created_at)}>
          {formatRelative(u.created_at)}
        </span>
      </div>
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
        <dt className="text-surface-muted">Owner</dt>
        <dd className="font-mono">{ownerLabel(u)}</dd>
        <dt className="text-surface-muted">File</dt>
        <dd>
          {u.format.toUpperCase()} · {formatBytes(u.bytes)}
          {u.sha256 && (
            <span className="ml-1 font-mono text-[10px] text-surface-muted" title={u.sha256}>
              {u.sha256.slice(0, 10)}
            </span>
          )}
        </dd>
        <dt className="text-surface-muted">Upload</dt>
        <dd className="font-mono text-[11px]">{u.id}</dd>
        {review && (
          <>
            <dt className="text-surface-muted">Reason</dt>
            <dd className="font-mono text-[11.5px]">{review.reason ?? "—"}</dd>
            {review.status !== "pending" && (
              <>
                <dt className="text-surface-muted">Decision</dt>
                <dd>
                  <span className="capitalize">{review.status}</span>
                  {review.reviewer_email && <span className="text-surface-muted"> · {review.reviewer_email}</span>}
                  {review.decided_at && <span className="text-surface-muted"> · {formatDateTime(review.decided_at)}</span>}
                  {review.decision_note && <span className="block text-surface-muted">&ldquo;{review.decision_note}&rdquo;</span>}
                </dd>
              </>
            )}
          </>
        )}
      </dl>
      {pending && (
        <div className="grid gap-2 border-t border-surface-border pt-3">
          <label className="grid gap-1 text-xs">
            <span className="ak-label">Note to the customer (optional)</span>
            <textarea className="ak-input text-[13px]" rows={2} maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} disabled={Boolean(busy)} placeholder={REJECTION_PRESET} />
          </label>
          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill justify-self-start" onClick={() => setNote(REJECTION_PRESET)} disabled={Boolean(busy) || note === REJECTION_PRESET}>
            Use the standard note
          </button>
          {problem && <ProblemCard compact problem={problem} title={problem.code === "review_already_decided" ? "Already decided" : undefined} />}
          <div className="flex flex-wrap gap-2">
            <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => void decide("approved")} disabled={Boolean(busy)}>
              {busy === "approved" ? "Approving…" : "Approve"}
            </button>
            <button type="button" className="ak-btn ak-btn-danger ak-btn-sm" onClick={() => void decide("rejected")} disabled={Boolean(busy)}>
              {busy === "rejected" ? "Rejecting…" : "Reject"}
            </button>
          </div>
        </div>
      )}
    </article>
  );
}
