/**
 * Shop catalog: items (the six launch SKUs) on their shelves, digital materials with their filament mapping,
 * density and pricing rate, the outcome families (Avatars: carriers, parametric object families and the single
 * raw-print family) with their content slots, and the bought-in hardware they pack. Read-mostly; seeded by Flyway
 * from {@code packages/design-tokens}; edited in the management portal. Readiness of a family comes from the live
 * template descriptors of the templates module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Catalog")
package studio.aakar.api.catalog;
