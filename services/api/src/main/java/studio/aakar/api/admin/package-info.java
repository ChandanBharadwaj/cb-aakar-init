/**
 * Management API behind the portal ({@code apps/admin}, ADR-0012), served under {@code /admin/api/*} with its own
 * staff authentication: email + password against {@code staff_accounts}, HS256 tokens signed with the identity
 * secret but typed {@code staff} so customer and staff tokens never pass each other's filter. Owns the studio
 * dashboard, the orders queue and stage advancement, print packs, QC photos, packaging cards and share codes,
 * pricing-policy publishing, materials, catalog items, template live switches, the content rules of the trademark
 * guardrail (through {@link studio.aakar.api.media.ContentTerms}), the messages log and the audit log. Roles: {@code owner} (everything) and {@code studio} (fulfilment plus read-only configuration). Nothing
 * depends on this module; it drives the other modules through their public APIs only.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Admin")
package studio.aakar.api.admin;
