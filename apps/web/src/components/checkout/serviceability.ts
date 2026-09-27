import type { Serviceability } from "@/lib/api/types";

export const PINCODE_RE = /^[1-9][0-9]{5}$/;

/** "Delhivery · 4 days" or "Not serviceable", with a small (mock) tag for the mock carrier. */
export function serviceabilityLabel(s: Serviceability): { text: string; ok: boolean; mock: boolean } {
  const mock = s.carrier.startsWith("mock");
  const carrier = s.carrier.replace(/^mock-?/, "").replace(/^\w/, (c) => c.toUpperCase()) || "Courier";
  if (!s.serviceable) return { text: "Not serviceable", ok: false, mock };
  const eta = typeof s.eta_days === "number" ? ` · ${s.eta_days} ${s.eta_days === 1 ? "day" : "days"}` : "";
  return { text: `${carrier}${eta}`, ok: true, mock };
}
