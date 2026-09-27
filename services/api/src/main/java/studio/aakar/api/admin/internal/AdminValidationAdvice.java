package studio.aakar.api.admin.internal;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.ProblemDetailsAdvice;

/**
 * The management contract answers schema violations with 422 {@code validation_failed} (the customer API uses
 * 400); this advice applies to admin controllers only and runs before the shared {@link ProblemDetailsAdvice}.
 */
@RestControllerAdvice(basePackageClasses = AdminValidationAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AdminValidationAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", snake(e.getField()), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return problem("Request validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", snake(v.getPropertyPath().toString()), "message", String.valueOf(v.getMessage())))
                .toList();
        return problem("Request validation failed", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String detail = cause == null || cause.getMessage() == null ? "The request body is not valid JSON for this endpoint"
                : "The request body does not match the schema: " + firstLine(cause.getMessage());
        return problem(detail, List.of());
    }

    private static ResponseEntity<ProblemDetail> problem(String detail, List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Validation failed");
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ProblemDetailsAdvice.respond(problem, ProblemCodes.VALIDATION_FAILED);
    }

    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
