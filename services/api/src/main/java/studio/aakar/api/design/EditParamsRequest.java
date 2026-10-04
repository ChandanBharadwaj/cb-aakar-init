package studio.aakar.api.design;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Body of {@code POST /api/versions/{versionId}/params}: values to change, optional material swap and, optionally, the
 * complete new list of content features. {@code features} null (or absent) keeps the parent version's; an empty list
 * clears them.
 */
public record EditParamsRequest(
        @NotNull(message = "params is required") Map<String, Object> params,
        String material,
        @Size(max = 8, message = "features must hold at most 8 items") List<Map<String, Object>> features) {

    /** Params and material only: the parent's features are kept. */
    public EditParamsRequest(Map<String, Object> params, String material) {
        this(params, material, null);
    }
}
