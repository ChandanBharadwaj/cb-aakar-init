package studio.aakar.api.shared;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as {@code application/problem+json} with a stable {@code code} property
 * (RFC 9457). Framework exceptions (bad JSON, missing parameters, unknown routes) get a code
 * derived from their status; domain errors carry their own.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);
    private static final URI PROBLEM_TYPE_BASE = URI.create("https://aakar.studio/problems/");

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetail> handleApiProblem(ApiProblemException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(ex.title());
        ex.properties().forEach(problem::setProperty);
        return respond(problem, ex.code());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value");
        problem.setTitle("Validation failed");
        return respond(problem, ProblemCodes.VALIDATION_FAILED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", String.valueOf(v.getMessage())))
                .toList());
        return respond(problem, ProblemCodes.VALIDATION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again.");
        problem.setTitle("Internal error");
        return respond(problem, ProblemCodes.INTERNAL_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ProblemDetailsAdvice::fieldError)
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ex instanceof org.springframework.web.ErrorResponse er
                ? er.getBody() : ProblemDetail.forStatus(statusCode);
        if (problem.getDetail() == null || problem.getDetail().isBlank()) {
            problem.setDetail(HttpStatus.valueOf(statusCode.value()).getReasonPhrase());
        }
        String code = codeFor(statusCode);
        problem.setProperty("code", code);
        problem.setType(PROBLEM_TYPE_BASE.resolve(code));
        HttpHeaders out = headers == null ? new HttpHeaders() : new HttpHeaders(headers);
        out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, out, statusCode);
    }

    private static Map<String, String> fieldError(FieldError error) {
        // The API speaks snake_case; translate the Java property path so clients can match it to their payload.
        String field = error.getField().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        return Map.of("field", field, "message", String.valueOf(error.getDefaultMessage()));
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (HttpStatus.valueOf(status.value())) {
            case NOT_FOUND -> ProblemCodes.NOT_FOUND;
            case BAD_REQUEST, UNSUPPORTED_MEDIA_TYPE, NOT_ACCEPTABLE, PAYLOAD_TOO_LARGE -> ProblemCodes.VALIDATION_FAILED;
            case METHOD_NOT_ALLOWED -> "method_not_allowed";
            case UNPROCESSABLE_ENTITY -> ProblemCodes.VALIDATION_FAILED;
            case SERVICE_UNAVAILABLE -> "service_unavailable";
            default -> status.is5xxServerError() ? ProblemCodes.INTERNAL_ERROR : "request_error";
        };
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setType(PROBLEM_TYPE_BASE.resolve(code));
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
