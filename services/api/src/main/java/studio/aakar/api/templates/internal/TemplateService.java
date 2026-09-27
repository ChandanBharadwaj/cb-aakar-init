package studio.aakar.api.templates.internal;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@Service
class TemplateService implements Templates {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateClient client;
    private final TemplateParamValidator validator;
    private final TemplateFlagRepository flags;
    private final Clock clock;

    TemplateService(TemplateClient client, TemplateParamValidator validator, TemplateFlagRepository flags, Clock clock) {
        this.client = client;
        this.validator = validator;
        this.flags = flags;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDescriptor> all() {
        Set<String> hidden = flags.findByLiveFalse().stream().map(TemplateFlagEntity::templateId).collect(Collectors.toSet());
        return client.all().stream().filter(d -> !hidden.contains(d.id())).toList();
    }

    @Override
    public List<TemplateDescriptor> allKnown() {
        return client.all();
    }

    @Override
    public Optional<TemplateDescriptor> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String bare = id.contains("@") ? id.substring(0, id.indexOf('@')) : id;
        return client.all().stream().filter(d -> bare.equals(d.id())).findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLive(String templateId) {
        return flags.findById(templateId).map(TemplateFlagEntity::live).orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> liveFlags() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        flags.findAll().forEach(f -> map.put(f.templateId(), f.live()));
        return map;
    }

    @Override
    @Transactional
    public TemplateDescriptor setLive(String templateId, boolean live) {
        TemplateDescriptor descriptor = byId(templateId).orElseThrow(() -> ApiProblemException.notFound("Template", templateId));
        flags.findById(descriptor.id()).ifPresentOrElse(f -> f.set(live, clock.instant()),
                () -> flags.save(new TemplateFlagEntity(descriptor.id(), live, clock.instant())));
        log.info("Template {} is now {}", descriptor.id(), live ? "live" : "hidden");
        return descriptor;
    }

    @Override
    public void validateParams(TemplateDescriptor descriptor, Map<String, Object> params) {
        validator.validate(descriptor, params);
    }
}
