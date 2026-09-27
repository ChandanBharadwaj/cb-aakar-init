package studio.aakar.api.design;

/**
 * How a design started. Lowercase constants: contract enum values and DB values. {@code upload} is the Swaroop path
 * (ADR-0014): the customer's own model file printed as it is, family {@code raw_print}.
 */
public enum DesignSource {
    shop, create, remix, upload
}
