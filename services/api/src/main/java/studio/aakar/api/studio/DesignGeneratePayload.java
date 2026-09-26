package studio.aakar.api.studio;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code events/design.generate.v1.json}; also the body of {@code POST /v1/build} on the geometry service. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignGeneratePayload(
        UUID jobId,
        UUID designId,
        int versionNo,
        UUID parentVersionId,
        Map<String, Object> spec,
        List<String> outputs,
        String callbackUrl) {

    public static final List<String> DEFAULT_OUTPUTS = List.of("glb", "3mf", "stl");
}
