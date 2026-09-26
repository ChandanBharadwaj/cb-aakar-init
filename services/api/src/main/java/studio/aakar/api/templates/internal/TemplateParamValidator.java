package studio.aakar.api.templates.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * Checks parameter values against a descriptor: known keys, matching type, within [min, max] and,
 * for enums, one of the options. Out-of-range values are rejected, never clamped
 * ({@code packages/contracts/README.md}).
 */
@Component
class TemplateParamValidator {

    void validate(TemplateDescriptor descriptor, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        params.forEach((key, value) -> {
            String problem = check(descriptor.params().get(key), value);
            if (problem != null) {
                keys.add(key);
                reasons.add(key + " " + problem);
            }
        });
        if (!keys.isEmpty()) {
            throw ApiProblemException.unprocessable(ProblemCodes.PARAM_OUT_OF_RANGE, "Parameter out of range",
                    "Parameters out of range for template " + descriptor.id() + ": " + String.join("; ", reasons),
                    Map.of("template_id", descriptor.id(), "params", List.copyOf(keys)));
        }
    }

    private static String check(TemplateDescriptor.Param spec, Object value) {
        if (spec == null) {
            return "is not a parameter of this template";
        }
        if (value == null) {
            return "must not be null";
        }
        String type = spec.type() == null ? "number" : spec.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "boolean" -> value instanceof Boolean ? null : "must be true or false";
            case "enum" -> {
                if (!(value instanceof String s)) {
                    yield "must be one of " + spec.options();
                }
                yield spec.options() == null || spec.options().contains(s) ? null : "must be one of " + spec.options();
            }
            case "integer" -> {
                if (!(value instanceof Number n) || !isIntegral(n)) {
                    yield "must be a whole number";
                }
                yield range(spec, n.doubleValue());
            }
            default -> {
                if (!(value instanceof Number n)) {
                    yield "must be a number";
                }
                yield range(spec, n.doubleValue());
            }
        };
    }

    private static boolean isIntegral(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return true;
        }
        double d = n.doubleValue();
        return Double.isFinite(d) && d == Math.rint(d);
    }

    private static String range(TemplateDescriptor.Param spec, double v) {
        boolean belowMin = spec.min() != null && v < spec.min();
        boolean aboveMax = spec.max() != null && v > spec.max();
        if (!belowMin && !aboveMax) {
            return null;
        }
        String unit = spec.unit() == null || spec.unit().isBlank() ? "" : " " + spec.unit();
        return "must be between " + fmt(spec.min()) + " and " + fmt(spec.max()) + unit + " (got " + fmt(v) + ")";
    }

    private static String fmt(Double d) {
        if (d == null) {
            return "…";
        }
        return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }
}
