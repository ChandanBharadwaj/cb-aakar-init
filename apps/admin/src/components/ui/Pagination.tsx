"use client";

export interface PaginationProps {
  page: number;
  size: number;
  total: number;
  onPage(next: number): void;
  className?: string;
}

/** "1–25 of 132 · Prev · Next". Zero-based page like the API. */
export function Pagination({ page, size, total, onPage, className }: PaginationProps) {
  const pages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <nav className={["flex items-center justify-between gap-3 text-xs text-surface-muted", className].filter(Boolean).join(" ")} aria-label="Pagination">
      <span>
        {from}–{to} of {total}
      </span>
      <div className="flex items-center gap-2">
        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <span>
          Page {page + 1} of {pages}
        </span>
        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </div>
    </nav>
  );
}
