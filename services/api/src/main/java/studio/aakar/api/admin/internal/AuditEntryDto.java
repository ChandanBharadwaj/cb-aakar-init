package studio.aakar.api.admin.internal;

import java.time.Instant;
import java.util.Map;

/** {@code AuditEntry} in the management contract. */
record AuditEntryDto(long id, Instant at, String staffEmail, String action, String target, Map<String, Object> before, Map<String, Object> after) {
}
