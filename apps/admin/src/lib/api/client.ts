// Typed fetch wrapper over the Aakar Management API (packages/contracts/openapi/aakar-admin.v1.yaml).
// Staff token lives in localStorage (`aakar_staff_token`) and travels as `Authorization: Bearer`.
// A 401 on any call clears the token and sends the browser back to /signin.
import type {
  AdminFamily,
  AdminFamilyInput,
  AdminHardware,
  AdminHardwareInput,
  AdminMaterial,
  AdminMaterialInput,
  AdminOrder,
  AdminTemplate,
  AdminUpload,
  AdvanceRequest,
  AuditPage,
  CatalogItem,
  CatalogItemInput,
  Dashboard,
  LoginRequest,
  MediaAsset,
  NotificationsPage,
  NotificationsQuery,
  OrdersPage,
  OrdersQuery,
  PriceBreakdown,
  PricingPolicyVersion,
  PricingPreviewRequest,
  Problem,
  PublishPolicyRequest,
  ReviewDecision,
  Shelf,
  Staff,
  StaffSession,
  TemplateLiveRequest,
  UploadStatus,
} from "./types";

export const TOKEN_KEY = "aakar_staff_token";
const PUBLIC_API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** Base URL for API calls. Server code may point at an internal hostname via API_URL. */
export function apiBase(): string {
  if (typeof window === "undefined" && process.env.API_URL) return process.env.API_URL;
  return PUBLIC_API_URL;
}

export function apiUrl(path: string): string {
  return `${apiBase().replace(/\/$/, "")}${path}`;
}

export function readToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function writeToken(token: string | null): void {
  if (typeof window === "undefined") return;
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* private mode; the session lives only in memory */
  }
}

/** Where to go when a token is missing or rejected: the sign-in page, remembering the current page. */
export function signInHref(): string {
  if (typeof window === "undefined") return "/signin";
  const here = window.location.pathname + window.location.search;
  return here === "/" || here.startsWith("/signin") ? "/signin" : `/signin?next=${encodeURIComponent(here)}`;
}

function bounceToSignIn(): void {
  writeToken(null);
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/signin")) {
    window.location.assign(signInHref());
  }
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;
  readonly code: string;

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.code = problem.code ?? (status === 0 ? "unreachable" : `http_${status}`);
  }

  get title(): string {
    return this.problem.title ?? (this.status === 0 ? "Couldn't reach the management API" : "Something went wrong");
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/** Turn any thrown value into a Problem we can render in a card. */
export function toProblem(err: unknown): Problem & { code: string } {
  if (isApiError(err)) return { ...err.problem, code: err.code, title: err.title };
  const detail = err instanceof Error ? err.message : String(err);
  return { title: "Something went wrong", detail, code: "unknown" };
}

const UNREACHABLE: Problem = {
  title: "Couldn't reach the management API",
  detail: "Nothing is answering on the API URL. Start services/api, or the mock: pnpm --filter @aakar/admin mock:api.",
  code: "unreachable",
};

async function parseProblem(res: Response): Promise<Problem> {
  const type = res.headers.get("content-type") ?? "";
  if (type.includes("json")) {
    try {
      const body = (await res.json()) as Problem;
      return { status: res.status, ...body };
    } catch {
      /* fall through */
    }
  }
  return { status: res.status, title: res.statusText || "Request failed", code: `http_${res.status}` };
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  /** Multipart body; sent as-is (the browser sets the boundary). */
  form?: FormData;
  query?: Record<string, string | number | boolean | undefined>;
  /** Skip the bearer header and the 401 bounce (sign-in). */
  anonymous?: boolean;
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const { body, form, query, headers, anonymous, ...init } = opts;
  const url = new URL(apiUrl(path));
  if (query) for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== "") url.searchParams.set(k, String(v));
  const token = anonymous ? null : readToken();
  let res: Response;
  try {
    res = await fetch(url, {
      cache: "no-store",
      ...init,
      method: init.method ?? (body === undefined && form === undefined ? "GET" : "POST"),
      headers: {
        Accept: "application/json, application/problem+json, */*",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(headers as Record<string, string> | undefined),
      },
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
    });
  } catch (err) {
    throw new ApiError(0, { ...UNREACHABLE, detail: `${UNREACHABLE.detail} (${err instanceof Error ? err.message : "network error"})` });
  }
  if (res.status === 401 && !anonymous) {
    const problem = await parseProblem(res);
    bounceToSignIn();
    throw new ApiError(401, { ...problem, code: problem.code ?? "unauthenticated" });
  }
  if (!res.ok) throw new ApiError(res.status, await parseProblem(res));
  return res;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(path, opts);
  if (res.status === 204) return undefined as T;
  const type = res.headers.get("content-type") ?? "";
  if (!type.includes("json")) return undefined as T;
  return (await res.json()) as T;
}

/** GET a binary document (zip, pdf) and hand it back with the filename the server suggested. */
export async function download(path: string, fallbackName: string): Promise<{ blob: Blob; filename: string }> {
  const res = await send(path, {});
  const disposition = res.headers.get("content-disposition") ?? "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await res.blob(), filename: m?.[1] ? decodeURIComponent(m[1]) : fallbackName };
}

/** Trigger a browser download for a blob. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

const enc = encodeURIComponent;

export const api = {
  auth: {
    login: (body: LoginRequest) => request<StaffSession>("/admin/api/auth/login", { method: "POST", body, anonymous: true }),
    me: () => request<Staff>("/admin/api/auth/me"),
  },
  dashboard: () => request<Dashboard>("/admin/api/dashboard"),
  orders: {
    list: (query?: OrdersQuery) => request<OrdersPage>("/admin/api/orders", { query }),
    get: (id: string) => request<AdminOrder>(`/admin/api/orders/${enc(id)}`),
    advance: (id: string, body: AdvanceRequest) => request<AdminOrder>(`/admin/api/orders/${enc(id)}/advance`, { method: "POST", body }),
    printPack: (id: string, number: string) => download(`/admin/api/orders/${enc(id)}/print-pack`, `${number}-print-pack.zip`),
    packagingCard: (id: string, number: string) => download(`/admin/api/orders/${enc(id)}/packaging-card.pdf`, `${number}-packaging-card.pdf`),
    packagingCardUrl: (id: string) => apiUrl(`/admin/api/orders/${enc(id)}/packaging-card.pdf`),
    uploadQcPhoto: (id: string, file: File, note?: string) => {
      const form = new FormData();
      form.append("file", file, file.name);
      if (note) form.append("note", note);
      return request<MediaAsset>(`/admin/api/orders/${enc(id)}/qc-photos`, { method: "POST", form });
    },
  },
  pricing: {
    policies: () => request<PricingPolicyVersion[]>("/admin/api/pricing/policies"),
    active: () => request<PricingPolicyVersion>("/admin/api/pricing/policies/active"),
    publish: (body: PublishPolicyRequest) => request<PricingPolicyVersion>("/admin/api/pricing/policies", { method: "POST", body }),
    preview: (body: PricingPreviewRequest) => request<PriceBreakdown>("/admin/api/pricing/preview", { method: "POST", body }),
  },
  materials: {
    list: () => request<AdminMaterial[]>("/admin/api/materials"),
    create: (body: AdminMaterialInput) => request<AdminMaterial>("/admin/api/materials", { method: "POST", body }),
    update: (id: string, body: AdminMaterialInput) => request<AdminMaterial>(`/admin/api/materials/${enc(id)}`, { method: "PUT", body }),
  },
  catalog: {
    list: () => request<CatalogItem[]>("/admin/api/catalog/items"),
    create: (body: CatalogItemInput) => request<CatalogItem>("/admin/api/catalog/items", { method: "POST", body }),
    update: (slug: string, body: CatalogItemInput) => request<CatalogItem>(`/admin/api/catalog/items/${enc(slug)}`, { method: "PUT", body }),
    /** Shop shelves (catalog categories) in display order; `CatalogItemInput.category` must be one of these ids. */
    shelves: () => request<Shelf[]>("/admin/api/catalog/shelves"),
  },
  /** Outcome families (Avatars): owner-only writes, audited as family.create / family.update. */
  families: {
    list: () => request<AdminFamily[]>("/admin/api/families"),
    create: (body: AdminFamilyInput) => request<AdminFamily>("/admin/api/families", { method: "POST", body }),
    update: (id: string, body: AdminFamilyInput) => request<AdminFamily>(`/admin/api/families/${enc(id)}`, { method: "PUT", body }),
  },
  /** Bought-in hardware (split rings, magnets, LED bases): owner-only writes, audited as hardware.create / hardware.update. */
  hardware: {
    list: () => request<AdminHardware[]>("/admin/api/hardware"),
    create: (body: AdminHardwareInput) => request<AdminHardware>("/admin/api/hardware", { method: "POST", body }),
    update: (sku: string, body: AdminHardwareInput) => request<AdminHardware>(`/admin/api/hardware/${enc(sku)}`, { method: "PUT", body }),
  },
  /** Customer uploads (images for reliefs, model files for Swaroop); `pending_review` is the review queue. */
  uploads: {
    list: (status?: UploadStatus, limit?: number) => request<AdminUpload[]>("/admin/api/uploads", { query: { status, limit } }),
  },
  /** Content review decisions: studio or owner, audited as review.decide. */
  reviews: {
    decide: (reviewId: string, decision: ReviewDecision, note?: string) =>
      request<AdminUpload>(`/admin/api/content-reviews/${enc(reviewId)}`, { method: "POST", body: { decision, ...(note?.trim() ? { note: note.trim() } : {}) } }),
  },
  templates: {
    list: () => request<AdminTemplate[]>("/admin/api/templates"),
    setLive: (id: string, body: TemplateLiveRequest) => request<void>(`/admin/api/templates/${enc(id)}`, { method: "PUT", body }),
  },
  notifications: {
    list: (query?: NotificationsQuery) => request<NotificationsPage>("/admin/api/notifications", { query }),
  },
  audit: {
    list: (query?: { page?: number; size?: number }) => request<AuditPage>("/admin/api/audit", { query }),
  },
};

export type Api = typeof api;
