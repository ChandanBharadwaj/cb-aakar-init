package studio.aakar.api.shared;

/** Stable machine-readable {@code code} values carried on every Problem Details response. */
public final class ProblemCodes {

    public static final String NOT_FOUND = "not_found";
    public static final String VALIDATION_FAILED = "validation_failed";
    public static final String NOT_YET_AVAILABLE = "not_yet_available";
    public static final String PARAM_OUT_OF_RANGE = "param_out_of_range";
    public static final String TEMPLATE_NOT_AVAILABLE = "template_not_available";
    public static final String VERSION_NOT_READY = "version_not_ready";
    public static final String UNKNOWN_MATERIAL = "unknown_material";
    public static final String GEOMETRY_UNAVAILABLE = "geometry_unavailable";
    public static final String INTERNAL_ERROR = "internal_error";

    // Phase 1 · identity
    public static final String UNAUTHENTICATED = "unauthenticated";
    public static final String FORBIDDEN = "forbidden";
    public static final String OTP_INVALID = "otp_invalid";
    public static final String OTP_EXPIRED = "otp_expired";
    public static final String OTP_RATE_LIMITED = "otp_rate_limited";

    // Phase 1 · cart, checkout, orders, payments
    public static final String NOT_PRINTABLE = "not_printable";
    public static final String CART_EMPTY = "cart_empty";
    public static final String NOT_SERVICEABLE = "not_serviceable";
    public static final String PAYMENT_FINAL = "payment_final";
    public static final String ORDER_NOT_PAYABLE = "order_not_payable";
    public static final String INVALID_TRANSITION = "invalid_transition";
    public static final String POLICY_VERSION_EXISTS = "policy_version_exists";

    // Phase 1 · management API (ADR-0012)
    public static final String ORDER_NOT_PACKED = "order_not_packed";
    public static final String MATERIAL_EXISTS = "material_exists";
    public static final String SLUG_EXISTS = "slug_exists";
    public static final String PAYLOAD_TOO_LARGE = "payload_too_large";
    public static final String RATE_LIMITED = "rate_limited";

    private ProblemCodes() {
    }
}
