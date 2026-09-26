package studio.aakar.api.studio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/** {@code events/design.failed.v1.json}; also the 422/500 body of {@code POST /v1/build}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DesignFailedPayload(UUID jobId, UUID designId, String code, String message, Map<String, Object> detail) {

    public static final String BUILD_ERROR = "build_error";
}
