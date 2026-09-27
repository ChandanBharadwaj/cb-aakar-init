import { formatPaise } from "@aakar/design-tokens";
import type { PricingPolicy, PricingPolicyVersion } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { Stat } from "@/components/ui/Stat";
import { Pill } from "@/components/ui/StatusPill";

export function policyRows(policy: PricingPolicy): { label: string; value: string }[] {
  return [
    { label: "Machine rate", value: `${formatPaise(policy.machine_rate_paise_per_hour)} / hour` },
    ...Object.entries(policy.finishing_fee_paise).map(([cls, fee]) => ({ label: `Finishing · ${cls}`, value: formatPaise(fee) })),
    { label: "Packaging fee", value: formatPaise(policy.packaging_fee_paise) },
    { label: "Margin", value: `${policy.margin_pct}%` },
    { label: "Round to rupees ending in", value: String(policy.round_to_rupees_ending_in) },
    { label: "Shipping flat rate", value: formatPaise(policy.shipping_flat_paise) },
    { label: "Free shipping from", value: formatPaise(policy.free_shipping_above_paise) },
    { label: "Shipping label", value: policy.shipping_label },
  ];
}

/** Every field of a policy version; the active one gets the badge. */
export function PolicyCard({ version, headline, className }: { version: PricingPolicyVersion; headline?: string; className?: string }) {
  return (
    <section className={["ak-card grid gap-3 p-5", className].filter(Boolean).join(" ")} aria-label={`Policy ${version.version}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="grid gap-0.5">
          {headline && <span className="ak-label">{headline}</span>}
          <span className="font-display text-2xl font-semibold leading-tight">{version.version}</span>
        </div>
        {version.active && <Pill tone="success">Active</Pill>}
      </div>
      <div className="grid gap-1.5">
        {policyRows(version.policy).map((r) => (
          <Stat key={r.label} label={r.label} value={r.value} />
        ))}
      </div>
      <p className="border-t border-surface-border pt-2.5 text-[11px] text-surface-muted">
        {version.note ? <span className="block text-surface-text">{version.note}</span> : null}
        Published by {version.created_by} · {formatDateTime(version.created_at)}
      </p>
    </section>
  );
}
