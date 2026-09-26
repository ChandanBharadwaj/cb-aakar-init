"use client";

import { useMemo, useState } from "react";
import type { CatalogItem } from "@/lib/api/types";
import { CATEGORIES, type CategoryPill } from "@/lib/catalog";
import { SkuCard } from "@/components/ui/SkuCard";

export interface ShopGridProps {
  items: CatalogItem[];
  initialCategory?: string;
}

/** Category pills + card grid. Filtering is client-side over the full catalogue so switching is instant. */
export function ShopGrid({ items, initialCategory = "all" }: ShopGridProps) {
  const start = CATEGORIES.some((c) => c.id === initialCategory) ? (initialCategory as CategoryPill["id"]) : "all";
  const [category, setCategory] = useState<CategoryPill["id"]>(start);
  const visible = useMemo(() => (category === "all" ? items : items.filter((i) => i.category === category)), [items, category]);
  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const i of items) m.set(i.category, (m.get(i.category) ?? 0) + 1);
    return m;
  }, [items]);

  return (
    <div className="grid gap-6">
      <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="Categories">
        {CATEGORIES.map((c) => {
          const n = c.id === "all" ? items.length : (counts.get(c.id) ?? 0);
          return (
            <button key={c.id} type="button" className="ak-chip" aria-pressed={category === c.id} onClick={() => setCategory(c.id)}>
              {c.label}
              <span className="text-[10px] font-medium opacity-70">{n}</span>
            </button>
          );
        })}
      </div>
      {visible.length === 0 ? (
        <p className="ak-card p-6 text-sm text-surface-muted">Nothing in this shelf yet. New pieces land every few weeks.</p>
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((item) => (
            <li key={item.slug}>
              <SkuCard item={item} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
