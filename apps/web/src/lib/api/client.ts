// Small typed fetch wrapper over the Aakar API (packages/contracts/openapi/aakar-api.v1.yaml).
// The API speaks snake_case JSON and RFC 9457 Problem Details with a stable `code`.
import type { Feature } from "@/lib/features";
import { clearToken, identityHeaders } from "@/lib/identity";
import type {
  Address,
  AddressInput,
  Cart,
  CartAddRequest,
  CartItemPatch,
  CatalogItem,
  CheckoutRequest,
  CheckoutResult,
  CreateDesignRequest,
  Design,
  DesignAccepted,
  DesignVersion,
  EditParamsRequest,
  Family,
  FamilyKind,
  Job,
  Material,
  MockCompleteRequest,
  Order,
  OrderSummary,
  OtpRequestResult,
  Payment,
  PriceBreakdown,
  PrintabilityReport,
  Problem,
  ProfilePatch,
  Serviceability,
  Session,
  SharedPiece,
  Shelf,
  TemplateDescriptor,
  Upload,
  UploadKind,
  User,
} from "./types";

/** `CreateDesignRequest` with typed Chhaap features (the contract types them as open objects). */
export type CreateDesignBody = Omit<CreateDesignRequest, "features"> & { features?: Feature[] };
/** `POST /api/versions/{id}/params` body: `features` replaces the parent's when present; omitted or null keeps them. */
export type EditParamsBody = Omit<EditParamsRequest, "features"> & { features?: Feature[] | null };

const PUBLIC_API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** Base URL for API calls. Server components may point at an internal hostname via API_URL. */
export function apiBase(): string {
  if (typeof window === "undefined" && process.env.API_URL) return process.env.API_URL;
  return PUBLIC_API_URL;
}

export function apiUrl(path: string): string {
  return `${apiBase().replace(/\/$/, "")}${path}`;
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
    return this.problem.title ?? (this.status === 0 ? "Couldn't reach the studio" : "Something went wrong");
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/** Turn any thrown value into a Problem we can render in a paper card. */
export function toProblem(err: unknown): Problem & { code: string } {
  if (isApiError(err)) return { ...err.problem, code: err.code, title: err.title };
  const detail = err instanceof Error ? err.message : String(err);
  return { title: "Something went wrong", detail, code: "unknown" };
}

const UNREACHABLE: Problem = {
  title: "Couldn't reach the studio",
  detail: "The Aakar API isn't answering right now. Make sure services/api is running, then try again.",
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
  /** JSON-encoded, or sent as-is when it is a `FormData` (the browser sets the multipart boundary). */
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
}

function isFormData(body: unknown): body is FormData {
  return typeof FormData !== "undefined" && body instanceof FormData;
}

/**
 * Fetches `path` on the API. In the browser every call carries the identity headers
 * (`X-Aakar-Guest`, and `Authorization: Bearer` once signed in). A 401 while holding a
 * token means the token is no longer good, so it is forgotten; the OTP endpoints are
 * exempt because they answer 401 for a wrong code.
 */
export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { body, query, headers, ...init } = opts;
  const url = new URL(apiUrl(path));
  if (query) {
    for (const [k, v] of Object.entries(query)) if (v !== undefined) url.searchParams.set(k, String(v));
  }
  const identity = identityHeaders();
  const multipart = isFormData(body);
  let res: Response;
  try {
    res = await fetch(url, {
      cache: "no-store",
      ...init,
      method: init.method ?? (body === undefined ? "GET" : "POST"),
      headers: {
        Accept: "application/json, application/problem+json",
        ...identity,
        ...(body !== undefined && !multipart ? { "Content-Type": "application/json" } : {}),
        ...(headers as Record<string, string> | undefined),
      },
      body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
    });
  } catch (err) {
    throw new ApiError(0, { ...UNREACHABLE, detail: `${UNREACHABLE.detail} (${err instanceof Error ? err.message : "network error"})` });
  }
  if (!res.ok) {
    if (res.status === 401 && identity.Authorization && !path.startsWith("/api/auth/otp")) clearToken();
    throw new ApiError(res.status, await parseProblem(res));
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  catalog: {
    items: (query?: { category?: string; q?: string }) => request<CatalogItem[]>("/api/catalog/items", { query }),
    item: (slug: string) => request<CatalogItem>(`/api/catalog/items/${encodeURIComponent(slug)}`),
    materials: () => request<Material[]>("/api/catalog/materials"),
    /** Shop shelves (catalog categories) in display order; `CatalogItem.category` is a shelf id. */
    shelves: () => request<Shelf[]>("/api/catalog/shelves"),
  },
  families: {
    /** Outcome categories (Avatars) that are available with a live template; includes the raw family (Swaroop). */
    list: (kind?: FamilyKind) => request<Family[]>("/api/families", { query: { kind } }),
    get: (id: string) => request<Family>(`/api/families/${encodeURIComponent(id)}`),
  },
  uploads: {
    /**
     * Customer content for the Chhaap: an image for a photo relief or a model file for a hero form / Swaroop.
     * Multipart `file` + `kind`; the browser sets the boundary, the identity headers travel as usual.
     */
    create: (file: File, kind: UploadKind) => {
      const form = new FormData();
      form.append("file", file, file.name);
      form.append("kind", kind);
      return request<Upload>("/api/uploads", { method: "POST", body: form });
    },
    get: (id: string) => request<Upload>(`/api/uploads/${encodeURIComponent(id)}`),
  },
  templates: {
    list: () => request<TemplateDescriptor[]>("/api/templates"),
    get: (id: string) => request<TemplateDescriptor>(`/api/templates/${encodeURIComponent(id)}`),
  },
  designs: {
    /** Shop (`catalog_item_slug`), Remix (`template_id`), Avatar (`family_id` + `features`) or Swaroop (`source: upload`) path. */
    create: (body: CreateDesignBody) => request<DesignAccepted>("/api/designs", { method: "POST", body }),
    get: (id: string) => request<Design>(`/api/designs/${encodeURIComponent(id)}`),
    versions: (id: string) => request<DesignVersion[]>(`/api/designs/${encodeURIComponent(id)}/versions`),
  },
  versions: {
    get: (versionId: string) => request<DesignVersion>(`/api/versions/${encodeURIComponent(versionId)}`),
    /** Sliders, finish and the Chhaap in one call → a new version and job. */
    editParams: (versionId: string, body: EditParamsBody) =>
      request<DesignAccepted>(`/api/versions/${encodeURIComponent(versionId)}/params`, { method: "POST", body }),
    printability: (versionId: string) =>
      request<PrintabilityReport>(`/api/versions/${encodeURIComponent(versionId)}/printability`),
    price: (versionId: string, material: string) =>
      request<PriceBreakdown>(`/api/versions/${encodeURIComponent(versionId)}/price`, { query: { material } }),
  },
  jobs: {
    get: (jobId: string) => request<Job>(`/api/jobs/${encodeURIComponent(jobId)}`),
    /** SSE endpoint: `event: stage`, JSON data shaped like JobStageEvent. */
    eventsUrl: (jobId: string) => apiUrl(`/api/jobs/${encodeURIComponent(jobId)}/events`),
  },
  share: {
    /** Public: what the unboxing card's /k/{code} link points to. */
    get: (code: string) => request<SharedPiece>(`/api/share/${encodeURIComponent(code)}`),
  },
  auth: {
    /** `phone` is E.164, e.g. +919876543210. The local profile returns `dev_code`. */
    requestOtp: (phone: string) => request<OtpRequestResult>("/api/auth/otp/request", { method: "POST", body: { phone } }),
    /** Signs in and attaches the guest's designs and cart; the caller stores `access_token`. */
    verifyOtp: (request_id: string, code: string) => request<Session>("/api/auth/otp/verify", { method: "POST", body: { request_id, code } }),
    me: () => request<User>("/api/auth/me"),
    updateMe: (body: ProfilePatch) => request<User>("/api/auth/me", { method: "PATCH", body }),
    logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  },
  addresses: {
    list: () => request<Address[]>("/api/me/addresses"),
    create: (body: AddressInput) => request<Address>("/api/me/addresses", { method: "POST", body }),
    update: (addressId: string, body: AddressInput) => request<Address>(`/api/me/addresses/${encodeURIComponent(addressId)}`, { method: "PUT", body }),
    remove: (addressId: string) => request<void>(`/api/me/addresses/${encodeURIComponent(addressId)}`, { method: "DELETE" }),
  },
  cart: {
    get: () => request<Cart>("/api/cart"),
    clear: () => request<Cart>("/api/cart", { method: "DELETE" }),
    add: (body: CartAddRequest) => request<Cart>("/api/cart/items", { method: "POST", body }),
    update: (itemId: string, body: CartItemPatch) => request<Cart>(`/api/cart/items/${encodeURIComponent(itemId)}`, { method: "PATCH", body }),
    remove: (itemId: string) => request<Cart>(`/api/cart/items/${encodeURIComponent(itemId)}`, { method: "DELETE" }),
  },
  shipping: {
    serviceability: (pincode: string) => request<Serviceability>("/api/shipping/serviceability", { query: { pincode } }),
  },
  checkout: (body: CheckoutRequest) => request<CheckoutResult>("/api/checkout", { method: "POST", body }),
  orders: {
    list: () => request<OrderSummary[]>("/api/orders"),
    get: (orderId: string) => request<Order>(`/api/orders/${encodeURIComponent(orderId)}`),
    /** SSE endpoint: `event: stage`, JSON data shaped like OrderEvent; needs the bearer token, so it is read with fetch, not EventSource. */
    eventsUrl: (orderId: string) => apiUrl(`/api/orders/${encodeURIComponent(orderId)}/events`),
    /** A fresh payment attempt for an order still awaiting payment. */
    newPayment: (orderId: string) => request<Payment>(`/api/orders/${encodeURIComponent(orderId)}/payments`, { method: "POST" }),
  },
  payments: {
    get: (paymentId: string) => request<Payment>(`/api/payments/${encodeURIComponent(paymentId)}`),
    /** The placeholder gateway page reports the outcome (mock gateway only). */
    mockComplete: (paymentId: string, body: MockCompleteRequest) =>
      request<Payment>(`/api/payments/${encodeURIComponent(paymentId)}/mock/complete`, { method: "POST", body }),
  },
};

export type Api = typeof api;
