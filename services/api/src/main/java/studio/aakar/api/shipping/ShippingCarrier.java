package studio.aakar.api.shipping;

/** Carrier adapter (Delhivery in production, {@code mock-delhivery} locally). */
public interface ShippingCarrier {

    /** Adapter name as reported in serviceability responses and shipments, e.g. {@code delhivery} or {@code mock-delhivery}. */
    String name();

    Serviceability serviceability(String pincode);

    /** Books a shipment (AWB) with the carrier; called when an order is packed. */
    CarrierShipment createShipment(ShipmentRequest request);
}
