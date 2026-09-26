// Typed access to the Aakar design tokens. The JSON files are the source of truth.
import tokensJson from "../tokens.json";
import materialsJson from "../materials.json";

export const tokens = tokensJson;
export const materials = materialsJson.materials;
export const pricingPolicy = materialsJson.pricing_policy;

export type MaterialId = (typeof materialsJson.materials)[number]["id"];
export type Material = (typeof materialsJson.materials)[number];
export type EnvironmentId = keyof typeof tokensJson.environments;
export type StageId = Exclude<keyof typeof tokensJson.stage_copy, "$comment">;

export const color = tokensJson.color;
export const font = tokensJson.font;
export const radius = tokensJson.radius;
export const motion = tokensJson.motion;
export const stageCopy = tokensJson.stage_copy;
export const environments = tokensJson.environments;

export function materialById(id: string): Material | undefined {
  return materialsJson.materials.find((m) => m.id === id);
}

/** Format integer paise as ₹1,249 (no decimals when whole). */
export function formatPaise(paise: number): string {
  const rupees = paise / 100;
  const whole = Number.isInteger(rupees);
  return "₹" + rupees.toLocaleString("en-IN", { minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 });
}

/** 13200 → "3 h 40 m". */
export function formatPrintTime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return h > 0 ? `${h} h ${m.toString().padStart(2, "0")} m` : `${m} m`;
}
