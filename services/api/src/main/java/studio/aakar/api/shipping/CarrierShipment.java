package studio.aakar.api.shipping;

import java.time.LocalDate;

/** The carrier's answer to a booking. */
public record CarrierShipment(String carrier, String awb, LocalDate eta, String trackingUrl) {
}
