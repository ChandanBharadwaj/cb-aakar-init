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

    private ProblemCodes() {
    }
}
