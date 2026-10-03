// Friendly aliases over the generated OpenAPI types (src/lib/api/schema.d.ts, `pnpm gen:api`).
// Everything here is derived from packages/contracts/openapi/aakar-admin.v1.yaml; nothing is hand-written.
import type { components, paths } from "./schema";

type Schemas = components["schemas"];
type Json<T> = T extends { content: { "application/json": infer B } } ? B : never;
type Ok<P extends keyof paths, M extends keyof paths[P]> = paths[P][M] extends { responses: infer R }
  ? R extends { 200: infer S }
    ? Json<S>
    : R extends { 201: infer S }
      ? Json<S>
      : never
  : never;
type Body<P extends keyof paths, M extends keyof paths[P]> = paths[P][M] extends { requestBody: infer B } ? Json<B> : never;

export type Problem = components["responses"]["Problem"]["content"]["application/problem+json"];

// Auth
export type Staff = Schemas["Staff"];
export type StaffRole = Staff["role"];
export type StaffSession = Schemas["StaffSession"];
export type LoginRequest = Body<"/admin/api/auth/login", "post">;

// Dashboard
export type Dashboard = Ok<"/admin/api/dashboard", "get">;

// Orders
export type OrderStatus = Schemas["OrderStatus"];
export type OrderStage = Schemas["OrderStage"];
export type AdminOrderSummary = Schemas["AdminOrderSummary"];
export type AdminOrder = Schemas["AdminOrder"];
export type OrderItem = Schemas["OrderItem"];
export type OrderEvent = Schemas["OrderEvent"];
export type Payment = Schemas["Payment"];
export type Shipment = Schemas["Shipment"];
export type Address = Schemas["Address"];
export type MediaAsset = Schemas["MediaAsset"];
export type OrdersPage = Ok<"/admin/api/orders", "get">;
export type OrdersQuery = NonNullable<paths["/admin/api/orders"]["get"]["parameters"]["query"]>;
export type AdvanceRequest = Body<"/admin/api/orders/{orderId}/advance", "post">;

// Pricing
export type PricingPolicy = Schemas["PricingPolicy"];
export type PricingPolicyVersion = Schemas["PricingPolicyVersion"];
export type PublishPolicyRequest = Body<"/admin/api/pricing/policies", "post">;
export type PricingPreviewRequest = Body<"/admin/api/pricing/preview", "post">;
export type PriceBreakdown = Schemas["price-breakdown.v1"];
/** One entry of `PricingPolicy.family_rules`: the per-Avatar minimum, setup fee and (deferred) quantity breaks. */
export type FamilyRule = NonNullable<PricingPolicy["family_rules"]>[string];

// Materials
export type AdminMaterial = Schemas["AdminMaterial"];
export type AdminMaterialInput = Schemas["AdminMaterialInput"];
export type MaterialPbr = Schemas["pbr"];
export type FinishClass = AdminMaterialInput["finish_class"];

// Catalog
export type CatalogItem = Schemas["CatalogItem"];
export type CatalogItemInput = Schemas["CatalogItemInput"];
export type Shelf = Schemas["Shelf"];

// Families (Avatars)
export type AdminFamily = Schemas["AdminFamily"];
export type AdminFamilyInput = Schemas["AdminFamilyInput"];
export type FamilyKind = AdminFamilyInput["kind"];
export type FamilyTier = AdminFamilyInput["tier"];
export type ShapeTolerance = AdminFamilyInput["shape_tolerance"];
/** Content-slot (Chhaap) feature types: emboss_text · motif · relief_image · hero_mesh. */
export type FeatureType = AdminFamilyInput["content_slot"]["accepts"][number];
export type HardwareRef = NonNullable<AdminFamilyInput["hardware"]>[number];

// Hardware
export type AdminHardware = Schemas["AdminHardware"];
export type AdminHardwareInput = Schemas["AdminHardwareInput"];

// Duniya (experiences), Mahaul (viewer backdrops) and the Buti motif library
/** An experience row as stored: `avatars` are family ids and `items` Shop item slugs, in display order. */
export type AdminExperience = Schemas["AdminExperience"];
export type AdminExperienceInput = Schemas["AdminExperienceInput"];
export type ExperienceStyle = AdminExperienceInput["style"];
export type ExperienceSurface = Schemas["ExperienceSurface"];
export type ExperienceCollection = Schemas["ExperienceCollection"];
export type SeasonWindow = Schemas["SeasonWindow"];
/** A viewer backdrop: `preset_key` names the storefront viewer preset that renders it. */
export type Environment = Schemas["Environment"];
export type Motif = Schemas["Motif"];

// Uploads and content reviews
export type AdminUpload = Schemas["AdminUpload"];
export type UploadStatus = AdminUpload["status"];
export type UploadsQuery = NonNullable<paths["/admin/api/uploads"]["get"]["parameters"]["query"]>;
export type ContentReviewDecision = Schemas["ContentReviewDecision"];
export type ReviewDecision = ContentReviewDecision["decision"];

// Content rules: the names the studio won't print (the Katha trademark guardrail)
/** A rule as stored, with `normalised_term` (how the matcher compares it) and `whole_word` (short terms match only as words). */
export type ContentTerm = Schemas["ContentTerm"];
export type ContentTermInput = Schemas["ContentTermInput"];
export type ContentTermKind = ContentTermInput["kind"];

// Templates
/**
 * The contract lists the columns every template row must carry. The API merges the descriptor's
 * `features_supported` and `hardware` into the same row (they are open properties in the contract), so the
 * portal reads them as optional extras and shows chips when present.
 */
export type AdminTemplate = Ok<"/admin/api/templates", "get">[number] & { features_supported?: FeatureType[]; hardware?: HardwareRef[] };
export type TemplateLiveRequest = Body<"/admin/api/templates/{templateId}", "put">;

// Messages and audit
export type NotificationRecord = Schemas["NotificationRecord"];
export type NotificationsPage = Ok<"/admin/api/notifications", "get">;
export type NotificationsQuery = NonNullable<paths["/admin/api/notifications"]["get"]["parameters"]["query"]>;
export type AuditEntry = Schemas["AuditEntry"];
export type AuditPage = Ok<"/admin/api/audit", "get">;

/** `assets` on an order item is an open object in the contract; the geometry service fills `glb`, `3mf`, `stl`. */
export interface VersionAsset {
  key?: string;
  url: string;
  bytes?: number;
  content_type?: string;
}

export function isAsset(value: unknown): value is VersionAsset {
  return typeof value === "object" && value !== null && typeof (value as { url?: unknown }).url === "string";
}

/** Named assets of an order item (3MF, STL, GLB) as [key, asset] pairs, in a stable order. */
export function itemAssets(item: OrderItem): [string, VersionAsset][] {
  const order = ["3mf", "stl", "glb"];
  return Object.entries(item.assets ?? {})
    .filter((e): e is [string, VersionAsset] => isAsset(e[1]))
    .sort((a, b) => (order.indexOf(a[0]) + 100) % 100 - ((order.indexOf(b[0]) + 100) % 100));
}
