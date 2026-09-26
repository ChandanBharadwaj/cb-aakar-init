// Small typed fetch wrapper over the Aakar API (packages/contracts/openapi/aakar-api.v1.yaml).
// The API speaks snake_case JSON and RFC 9457 Problem Details with a stable `code`.
import type {
  CatalogItem,
  CreateDesignRequest,
  Design,
  DesignAccepted,
  DesignVersion,
  Job,
  Material,
  ParamValues,
  PriceBreakdown,
  PrintabilityReport,
  Problem,
  TemplateDescriptor,
} from "./types";

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
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { body, query, headers, ...init } = opts;
  const url = new URL(apiUrl(path));
  if (query) {
    for (const [k, v] of Object.entries(query)) if (v !== undefined) url.searchParams.set(k, String(v));
  }
  let res: Response;
  try {
    res = await fetch(url, {
      cache: "no-store",
      ...init,
      method: init.method ?? (body === undefined ? "GET" : "POST"),
      headers: {
        Accept: "application/json, application/problem+json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(headers as Record<string, string> | undefined),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (err) {
    throw new ApiError(0, { ...UNREACHABLE, detail: `${UNREACHABLE.detail} (${err instanceof Error ? err.message : "network error"})` });
  }
  if (!res.ok) throw new ApiError(res.status, await parseProblem(res));
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  catalog: {
    items: (query?: { category?: string; q?: string }) => request<CatalogItem[]>("/api/catalog/items", { query }),
    item: (slug: string) => request<CatalogItem>(`/api/catalog/items/${encodeURIComponent(slug)}`),
    materials: () => request<Material[]>("/api/catalog/materials"),
  },
  templates: {
    list: () => request<TemplateDescriptor[]>("/api/templates"),
    get: (id: string) => request<TemplateDescriptor>(`/api/templates/${encodeURIComponent(id)}`),
  },
  designs: {
    create: (body: CreateDesignRequest) => request<DesignAccepted>("/api/designs", { method: "POST", body }),
    get: (id: string) => request<Design>(`/api/designs/${encodeURIComponent(id)}`),
    versions: (id: string) => request<DesignVersion[]>(`/api/designs/${encodeURIComponent(id)}/versions`),
  },
  versions: {
    get: (versionId: string) => request<DesignVersion>(`/api/versions/${encodeURIComponent(versionId)}`),
    editParams: (versionId: string, body: { params: ParamValues; material?: string }) =>
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
};

export type Api = typeof api;
