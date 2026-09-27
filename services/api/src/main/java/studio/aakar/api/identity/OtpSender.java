package studio.aakar.api.identity;

/**
 * Delivers a sign-in code to a phone (ADR-0013 adapter). {@code aakar.identity.otp.sender=mock} (default)
 * logs the code; a real SMS/WhatsApp sender is a drop-in implementation selected by that property.
 */
public interface OtpSender {

    /** Adapter name reported in logs and refused by the production guard when it is {@code mock}. */
    String name();

    void send(String phone, String code);
}
