package studio.aakar.api.design;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** Body of {@code POST /api/versions/{versionId}/params}: values to change, optional material swap. */
public record EditParamsRequest(@NotNull(message = "params is required") Map<String, Object> params, String material) {
}
