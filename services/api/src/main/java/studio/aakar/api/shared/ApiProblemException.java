package studio.aakar.api.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error that maps 1:1 onto an RFC 9457 Problem Details response with a stable {@code code}.
 * Thrown from services and controllers; rendered by {@link ProblemDetailsAdvice}.
 */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final Map<String, Object> properties;

    public ApiProblemException(HttpStatus status, String code, String title, String detail) {
        this(status, code, title, detail, Map.of());
    }

    public ApiProblemException(HttpStatus status, String code, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.properties = new LinkedHashMap<>(properties);
    }

    public static ApiProblemException notFound(String what, Object id) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found", what + " " + id + " was not found");
    }

    /** 404 with a domain code, e.g. {@code unknown_family}. */
    public static ApiProblemException notFound(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, code, title, detail);
    }

    public static ApiProblemException validation(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Validation failed", detail);
    }

    public static ApiProblemException unprocessable(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, code, title, detail);
    }

    public static ApiProblemException unprocessable(String code, String title, String detail, Map<String, Object> properties) {
        return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, code, title, detail, properties);
    }

    public static ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }

    /** 401 {@code unauthenticated}: no usable bearer token (or guest header) on a request that needs one. */
    public static ApiProblemException unauthenticated(String detail) {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED, "Unauthenticated", detail);
    }

    /** 401 with a domain code, e.g. {@code otp_invalid}. */
    public static ApiProblemException unauthorized(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, code, title, detail);
    }

    public static ApiProblemException tooManyRequests(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, code, title, detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
