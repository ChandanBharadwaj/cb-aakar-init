export { formatPaise, formatPrintTime } from "@aakar/design-tokens";

const IST = "Asia/Kolkata";

/** 2026-09-27T09:12:00Z → "27 Sep 2026, 14:42" (studio time, IST). */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-IN", { timeZone: IST, day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });
}

/** 2026-10-01 → "1 Oct". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-IN", { timeZone: iso.length === 10 ? undefined : IST, day: "numeric", month: "short" });
}

/** "3 h ago", "yesterday", "12 Sep". */
export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return "—";
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const s = Math.round((now - t) / 1000);
  if (s < 60) return "just now";
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  if (d === 1) return "yesterday";
  if (d < 7) return `${d} days ago`;
  return formatDate(iso);
}

/** 84.3 → "84 g". */
export function formatGrams(value: number): string {
  return `${Math.round(value)} g`;
}

/** 1234567 → "1,234,567" bytes → "1.2 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** snake_case → "Snake case". */
export function humanize(value: string): string {
  const s = value.replace(/[_.-]+/g, " ").trim();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Rupee input helpers: the API speaks paise, staff type rupees. */
export function paiseToRupees(paise: number): string {
  return (paise / 100).toString();
}
export function rupeesToPaise(rupees: string | number): number {
  const n = typeof rupees === "number" ? rupees : Number.parseFloat(rupees);
  return Number.isFinite(n) ? Math.round(n * 100) : 0;
}
