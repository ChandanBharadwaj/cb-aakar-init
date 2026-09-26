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
