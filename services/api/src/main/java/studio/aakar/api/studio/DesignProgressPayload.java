package studio.aakar.api.studio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code events/design.progress.v1.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DesignProgressPayload(String stage, String message, Integer percent) {
}
