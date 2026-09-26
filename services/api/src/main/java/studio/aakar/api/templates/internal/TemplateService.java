package studio.aakar.api.templates.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@Service
class TemplateService implements Templates {

    private final TemplateClient client;
    private final TemplateParamValidator validator;

    TemplateService(TemplateClient client, TemplateParamValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    @Override
    public List<TemplateDescriptor> all() {
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
    public void validateParams(TemplateDescriptor descriptor, Map<String, Object> params) {
        validator.validate(descriptor, params);
    }
}
