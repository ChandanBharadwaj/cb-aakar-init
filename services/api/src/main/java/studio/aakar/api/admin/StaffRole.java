package studio.aakar.api.admin;

/** Staff roles (ADR-0012). Lowercase constants: contract enum values and DB values. */
public enum StaffRole {
    /** Everything, including pricing, materials, catalog and template configuration. */
    owner,
    /** Fulfilment operations and read-only configuration. */
    studio
}
