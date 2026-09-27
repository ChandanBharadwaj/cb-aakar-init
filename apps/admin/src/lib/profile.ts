/** `local` shows mock-only hints (seeded credentials, mock sender notes). Set NEXT_PUBLIC_AAKAR_PROFILE=production to hide them. */
export const PROFILE = process.env.NEXT_PUBLIC_AAKAR_PROFILE ?? "local";
export const IS_LOCAL = PROFILE !== "production";

/** Seeded staff accounts of the mock management API (scripts/mock-admin-api.mjs) and the API's local profile. */
export const SEEDED_ACCOUNTS = [
  { email: "studio@aakar.local", password: "aakar-studio", role: "owner" },
  { email: "karigar@aakar.local", password: "aakar-karigar", role: "studio" },
] as const;
