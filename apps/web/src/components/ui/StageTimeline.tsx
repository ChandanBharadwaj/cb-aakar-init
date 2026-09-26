import { stageCopy } from "@aakar/design-tokens";
import type { Stage } from "@/lib/api/types";

type ShownStage = Exclude<Stage, "queued" | "failed">;
const ORDER: ShownStage[] = ["understanding", "sculpting", "checking", "pricing", "ready"];

export interface StageTimelineProps {
  stage?: Stage;
  className?: string;
}

/** Generation stages as dots and a line, current dot in the accent with a soft ring (order-tracking board idiom). */
export function StageTimeline({ stage, className }: StageTimelineProps) {
  const failed = stage === "failed";
  const cur = stage && stage !== "queued" && stage !== "failed" ? ORDER.indexOf(stage) : -1;
  return (
    <ol className={["flex items-center gap-0 text-[11px] font-semibold", className].filter(Boolean).join(" ")} aria-label="Progress">
      {ORDER.map((s, i) => {
        const done = cur > i || stage === "ready";
        const active = cur === i && stage !== "ready";
        const color = failed ? "bg-danger" : done ? "bg-surface-accent" : active ? "bg-surface-accent" : "bg-surface-border";
        return (
          <li key={s} className="flex flex-1 items-center last:flex-none" aria-current={active ? "step" : undefined}>
            <div className="grid justify-items-center gap-1.5">
              <span
                className={`h-2.5 w-2.5 rounded-full ${color} ${active ? "ak-ring-accent animate-pulse-soft" : ""}`}
                aria-hidden="true"
              />
              <span className={done || active ? "text-surface-text" : "text-surface-muted"}>{stageCopy[s]}</span>
            </div>
            {i < ORDER.length - 1 && <span className={`mx-2 mb-5 h-px flex-1 ${done ? "bg-surface-accent" : "bg-surface-border"}`} aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}
