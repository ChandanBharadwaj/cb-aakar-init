export { formatPaise, formatPrintTime } from "@aakar/design-tokens";

/** 120 → "120 mm", 3.2 → "3.2 mm". */
export function formatMm(value: number, digits = 0): string {
  return `${value.toFixed(digits).replace(/\.0+$/, "")} mm`;
}

/** "photo relief" → "Photo relief". */
export function capitalise(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** ["a photo", "your name"] → "a photo or your name"; three or more take commas: "a, b and c". */
export function joinList(parts: readonly string[], conjunction: "and" | "or" = "and"): string {
  if (parts.length <= 1) return parts.join("");
  return `${parts.slice(0, -1).join(", ")} ${conjunction} ${parts[parts.length - 1]}`;
}

/** 84.3 → "84 g". */
export function formatGrams(value: number): string {
  return `${Math.round(value)} g`;
}

/** Template param value + unit for the slider readout. */
export function formatParam(value: number | boolean | string, unit?: string): string {
  if (typeof value === "boolean") return value ? "On" : "Off";
  if (typeof value === "string") return value.replace(/_/g, " ");
  const text = Number.isInteger(value) ? String(value) : value.toFixed(1);
  switch (unit) {
    case "mm":
      return `${text} mm`;
    case "deg":
      return `${text}°`;
    default:
      return text;
  }
}
