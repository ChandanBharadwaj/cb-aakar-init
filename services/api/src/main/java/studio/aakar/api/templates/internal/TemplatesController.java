package studio.aakar.api.templates.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@RestController
@RequestMapping("/api/templates")
@Tag(name = "templates")
class TemplatesController {

    private final Templates templates;

    TemplatesController(Templates templates) {
        this.templates = templates;
    }

    @GetMapping
    @Operation(summary = "Template descriptors (cached from the geometry service)")
    List<TemplateDescriptor> all() {
        return templates.all();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Template descriptor by id")
    TemplateDescriptor byId(@PathVariable String id) {
        return templates.byId(id).orElseThrow(() -> ApiProblemException.notFound("Template", id));
    }
}
