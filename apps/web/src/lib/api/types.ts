// Friendly aliases over the generated OpenAPI types (src/lib/api/schema.d.ts, `pnpm gen:api`).
// The JSON-Schema documents referenced by the contract carry a `$defs` block that
// openapi-typescript surfaces as a (required) property; we strip it here so the
// aliases describe what the API actually sends.
import type { components, paths } from "./schema";

type Schemas = components["schemas"];

export type Problem = components["responses"]["Problem"]["content"]["application/problem+json"];

export type CatalogItem = Schemas["CatalogItem"];
export type CatalogCategory = CatalogItem["category"];
export type Material = Schemas["Material"];
export type MaterialPbr = Material["pbr"];
export type CreateDesignRequest = Schemas["CreateDesignRequest"];
export type DesignAccepted = Schemas["DesignAccepted"];
export type Design = Schemas["Design"];
export type DesignVersion = Schemas["DesignVersion"];
export type Job = Schemas["Job"];
export type JobStageEvent = Schemas["JobStageEvent"];
export type Stage = JobStageEvent["stage"];

export type TemplateDescriptor = Omit<Schemas["template-descriptor.v1"], "$defs">;
export type TemplateParam = Schemas["param"];
export type DesignSpec = Omit<Schemas["design-spec.v1"], "$defs">;
export type PrintabilityReport = Omit<Schemas["printability-report.v1"], "$defs">;
export type PrintabilityCheck = Schemas["check"];
export type PrintabilityCheckId = keyof PrintabilityReport["checks"];
export type PrintEstimate = Schemas["print-estimate.v1"];
export type PriceBreakdown = Schemas["price-breakdown.v1"];

// Phase 1 (ADR-0013): identity, cart, checkout, orders, payments.
export type User = Schemas["User"];
export type Session = Schemas["Session"];
export type AddressInput = Schemas["AddressInput"];
export type Address = Schemas["Address"];
export type Cart = Schemas["Cart"];
export type CartItem = Schemas["CartItem"];
export type OrderStatus = Schemas["OrderStatus"];
export type OrderStage = Schemas["OrderStage"];
export type OrderItem = Schemas["OrderItem"];
export type OrderSummary = Schemas["OrderSummary"];
export type Order = Schemas["Order"];
export type OrderEvent = Schemas["OrderEvent"];
export type Payment = Schemas["Payment"];
export type PaymentStatus = Payment["status"];
export type Shipment = Schemas["Shipment"];
export type CheckoutResult = Schemas["CheckoutResult"];

export type OtpRequestResult = paths["/api/auth/otp/request"]["post"]["responses"][202]["content"]["application/json"];
export type OtpVerifyRequest = paths["/api/auth/otp/verify"]["post"]["requestBody"]["content"]["application/json"];
export type ProfilePatch = paths["/api/auth/me"]["patch"]["requestBody"]["content"]["application/json"];
export type CartAddRequest = paths["/api/cart/items"]["post"]["requestBody"]["content"]["application/json"];
export type CartItemPatch = paths["/api/cart/items/{itemId}"]["patch"]["requestBody"]["content"]["application/json"];
export type Serviceability = paths["/api/shipping/serviceability"]["get"]["responses"][200]["content"]["application/json"];
export type CheckoutRequest = paths["/api/checkout"]["post"]["requestBody"]["content"]["application/json"];
export type MockCompleteRequest = paths["/api/payments/{paymentId}/mock/complete"]["post"]["requestBody"]["content"]["application/json"];
export type PaymentMethod = NonNullable<MockCompleteRequest["method"]>;

export type ParamValue = number | boolean | string;
export type ParamValues = Record<string, ParamValue>;

/** `assets` is an open object in the contract; the geometry service fills `glb`, `3mf`, `stl`. */
export interface VersionAsset {
  key?: string;
  url: string;
  bytes?: number;
  content_type?: string;
}

function isAsset(value: unknown): value is VersionAsset {
  return typeof value === "object" && value !== null && typeof (value as { url?: unknown }).url === "string";
}

/** URL of the preview GLB for a version, if the geometry service has produced one. */
export function glbUrl(version: DesignVersion | undefined): string | undefined {
  const glb = version?.assets?.glb;
  return isAsset(glb) ? glb.url : undefined;
}

/** Width, depth, height in mm. Prefers `geometry.bounds_mm`, falls back to the printability report. */
export function boundsMm(version: DesignVersion | undefined): [number, number, number] | undefined {
  const raw = version?.geometry?.bounds_mm ?? version?.printability?.geometry.bounds_mm;
  if (Array.isArray(raw) && raw.length >= 3 && raw.every((n) => typeof n === "number")) {
    return [raw[0] as number, raw[1] as number, raw[2] as number];
  }
  return undefined;
}

/**
 * `available` is not (yet) part of the CatalogItem contract; the Shop board shows
 * "Coming soon" ribbons, so the UI reads an optional `available` flag and treats a
 * missing flag as available.
 */
export function isAvailable(item: CatalogItem): boolean {
  return (item as CatalogItem & { available?: boolean }).available !== false;
}
export type SharedPiece = paths["/api/share/{code}"]["get"]["responses"][200]["content"]["application/json"];
