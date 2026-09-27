import type { OrderEvent } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { STAGE_LABEL } from "@/lib/orders";
import { StatusPill } from "@/components/ui/StatusPill";

export function EventsLog({ events }: { events: OrderEvent[] }) {
  const sorted = [...events].sort((a, b) => b.sequence - a.sequence);
  return (
    <section className="ak-card grid gap-3 p-5" aria-labelledby="events-heading">
      <h2 id="events-heading" className="font-display text-2xl font-semibold">
        Events
      </h2>
      <ol className="grid gap-3">
        {sorted.map((ev) => {
          const detail = Object.entries(ev.detail ?? {}).filter(([, v]) => v !== undefined && v !== null && v !== "");
          return (
            <li key={ev.sequence} className="grid gap-1.5 border-l-2 border-surface-border pl-3">
              <div className="flex flex-wrap items-center gap-2 text-xs text-surface-muted">
                <span className="font-mono">#{ev.sequence}</span>
                <StatusPill status={ev.status} />
                <span>customer stage: {STAGE_LABEL[ev.stage]}</span>
                <span className="ml-auto">{formatDateTime(ev.at)}</span>
              </div>
              <p className="text-sm">{ev.message}</p>
              {detail.length > 0 && (
                <dl className="flex flex-wrap gap-1.5">
                  {detail.map(([k, v]) => (
                    <div key={k} className="ak-pill" data-tone="neutral">
                      <dt className="font-normal opacity-80">{k.replace(/_/g, " ")}</dt>
                      <dd>{String(v)}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
