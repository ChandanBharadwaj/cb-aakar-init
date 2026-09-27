import { formatPaise } from "@aakar/design-tokens";
import { itemAssets, type OrderItem } from "@/lib/api/types";
import { formatGrams, formatPrintTime } from "@/lib/format";

export function OrderItemsTable({ items }: { items: OrderItem[] }) {
  return (
    <section className="ak-card overflow-hidden" aria-labelledby="items-heading">
      <h2 id="items-heading" className="px-5 pt-5 font-display text-2xl font-semibold">
        Items
      </h2>
      <div className="overflow-x-auto pt-3">
        <table className="ak-table">
          <thead>
            <tr>
              <th>Piece</th>
              <th>Material</th>
              <th className="num">Qty</th>
              <th className="num">Unit</th>
              <th className="num">Line</th>
              <th>Files</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it) => (
              <tr key={it.id}>
                <td>
                  <div className="grid gap-0.5">
                    <span className="font-medium">
                      {it.title} {it.version_no ? <span className="text-surface-muted">· v{it.version_no}</span> : null}
                    </span>
                    {it.specs_line && <span className="text-xs text-surface-muted">{it.specs_line}</span>}
                    <span className="text-[11px] text-surface-muted">
                      {formatGrams(it.unit_price.mass_g)} · {formatPrintTime(it.unit_price.print_seconds)} · policy {it.unit_price.policy_version}
                    </span>
                  </div>
                </td>
                <td>{it.material_name ?? it.material_id}</td>
                <td className="num">{it.qty}</td>
                <td className="num">{formatPaise(it.unit_price.subtotal_paise)}</td>
                <td className="num font-semibold">{formatPaise(it.line_total_paise)}</td>
                <td>
                  <div className="flex flex-wrap gap-1.5">
                    {itemAssets(it).map(([key, a]) => (
                      <a key={key} href={a.url} className="ak-chip min-h-7 px-2.5 py-0.5 text-[11px] uppercase" download target="_blank" rel="noreferrer" title={a.bytes ? `${(a.bytes / 1024).toFixed(0)} KB` : undefined}>
                        {key}
                      </a>
                    ))}
                    {itemAssets(it).length === 0 && <span className="text-xs text-surface-muted">No files snapshotted</span>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
