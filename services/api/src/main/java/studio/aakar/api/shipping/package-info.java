/**
 * Shipping (PLAN §12, ADR-0013): serviceability by pincode and shipments (AWB, ETA, tracking) through the
 * {@link studio.aakar.api.shipping.ShippingCarrier} adapter. {@code aakar.shipping.carrier=mock} (default)
 * serves every 6-digit pincode except those starting with 9 and mints {@code MOCK…} AWBs.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shipping")
package studio.aakar.api.shipping;
