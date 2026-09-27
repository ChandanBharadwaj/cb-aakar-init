// Browser-side identity (PLAN §6 `identity`, ADR-0013). Two values live in localStorage:
//   aakar_guest — a UUID created lazily; sent as `X-Aakar-Guest` on every request so guest
//                 designs and carts have an owner and can be attached on sign-in.
//   aakar_token — the access token from `POST /api/auth/otp/verify`; sent as `Authorization: Bearer`.
// Everything here is a no-op on the server (server components call the API anonymously).

export const GUEST_KEY = "aakar_guest";
export const TOKEN_KEY = "aakar_token";
/** Fired on `window` whenever the token is set or cleared, so stores can reload. */
export const IDENTITY_EVENT = "aakar:identity";

function storage(): Storage | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function uuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  // Non-secure contexts (a LAN IP over http) have no randomUUID; RFC 4122 v4 from getRandomValues.
  const b = new Uint8Array(16);
  crypto.getRandomValues(b);
  b[6] = ((b[6] ?? 0) & 0x0f) | 0x40;
  b[8] = ((b[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function getGuestId(): string | undefined {
  return storage()?.getItem(GUEST_KEY) ?? undefined;
}

/** The guest id, created on first use. */
export function ensureGuestId(): string | undefined {
  const s = storage();
  if (!s) return undefined;
  let id = s.getItem(GUEST_KEY);
  if (!id) {
    id = uuid();
    s.setItem(GUEST_KEY, id);
  }
  return id;
}

export function getToken(): string | undefined {
  return storage()?.getItem(TOKEN_KEY) ?? undefined;
}

export function setToken(token: string): void {
  storage()?.setItem(TOKEN_KEY, token);
  notify();
}

/** Forgets the token (sign-out, or a 401 that says it is no longer good). */
export function clearToken(): void {
  const s = storage();
  if (!s || s.getItem(TOKEN_KEY) === null) return;
  s.removeItem(TOKEN_KEY);
  notify();
}

function notify(): void {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(IDENTITY_EVENT));
}

export function onIdentityChange(listener: () => void): () => void {
  if (typeof window === "undefined") return () => undefined;
  window.addEventListener(IDENTITY_EVENT, listener);
  return () => window.removeEventListener(IDENTITY_EVENT, listener);
}

/** Request headers for the current identity: the guest id always, the bearer token when signed in. */
export function identityHeaders(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const headers: Record<string, string> = {};
  const guest = ensureGuestId();
  if (guest) headers["X-Aakar-Guest"] = guest;
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

/** "+919876543210" → "+91 98765 43210" for display. */
export function formatPhone(phone: string): string {
  const m = /^\+91(\d{5})(\d{5})$/.exec(phone);
  return m ? `+91 ${m[1]} ${m[2]}` : phone;
}

/** Keeps `?next=` on the same site: a path starting with a single slash, else "/". */
export function safeNext(next: string | undefined | null, fallback = "/"): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  return next;
}
