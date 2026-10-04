package studio.aakar.api.admin.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.shared.PageDto;

/** Every admin write records who changed what, with the value before and after (ADR-0012). */
@Service
class AuditLog {

    static final String ORDER_ADVANCE = "order.advance";
    static final String ORDER_QC_PHOTO = "order.qc_photo";
    static final String PRICING_PUBLISH = "pricing.publish";
    static final String MATERIAL_CREATE = "material.create";
    static final String MATERIAL_UPDATE = "material.update";
    static final String CATALOG_CREATE = "catalog.create";
    static final String CATALOG_UPDATE = "catalog.update";
    static final String TEMPLATE_LIVE = "template.live";
    static final String FAMILY_CREATE = "family.create";
    static final String FAMILY_UPDATE = "family.update";
    static final String HARDWARE_CREATE = "hardware.create";
    static final String HARDWARE_UPDATE = "hardware.update";
    static final String EXPERIENCE_CREATE = "experience.create";
    static final String EXPERIENCE_UPDATE = "experience.update";
    static final String REVIEW_DECIDE = "review.decide";
    static final String CONTENT_TERM_CREATE = "content_term.create";
    static final String CONTENT_TERM_UPDATE = "content_term.update";
    static final int MAX_PAGE_SIZE = 200;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    private final AuditLogRepository entries;
    private final ObjectMapper json;
    private final Clock clock;

    AuditLog(AuditLogRepository entries, ObjectMapper json, Clock clock) {
        this.entries = entries;
        this.json = json;
        this.clock = clock;
    }

    /** @param before / @param after any object Jackson can turn into a snake_case map, or null */
    @Transactional
    public AuditEntryDto record(String staffEmail, String action, String target, Object before, Object after) {
        AuditEntryDto entry = entries.save(new AuditLogEntity(clock.instant(), staffEmail, action, target, toMap(before), toMap(after))).toDto();
        log.info("Audit: {} {} {}", staffEmail, action, target);
        return entry;
    }

    @Transactional(readOnly = true)
    public PageDto<AuditEntryDto> page(int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                Sort.by(Sort.Order.desc("at"), Sort.Order.desc("id")));
        return PageDto.of(entries.findAll(request).map(AuditLogEntity::toDto));
    }

    private Map<String, Object> toMap(Object value) {
        return value == null ? null : json.convertValue(value, MAP);
    }
}
