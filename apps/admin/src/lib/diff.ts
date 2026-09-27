export interface DiffRow {
  key: string;
  before?: string;
  after?: string;
  changed: boolean;
}

function show(v: unknown): string | undefined {
  if (v === undefined) return undefined;
  return typeof v === "string" ? v : JSON.stringify(v);
}

/** Flat key-by-key comparison of two audit snapshots; nested objects are compared as JSON. */
export function diffEntries(before: Record<string, unknown> | null | undefined, after: Record<string, unknown> | null | undefined): DiffRow[] {
  const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])].sort();
  return keys.map((key) => {
    const b = show(before?.[key]);
    const a = show(after?.[key]);
    return { key, before: b, after: a, changed: b !== a };
  });
}
