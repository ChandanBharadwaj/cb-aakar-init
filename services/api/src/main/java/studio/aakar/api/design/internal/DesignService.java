package studio.aakar.api.design.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.design.CreateDesignRequest;
import studio.aakar.api.design.DesignAccepted;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignSource;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.design.EditParamsRequest;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.studio.GenerationJobs;
import studio.aakar.api.studio.GenerationRequest;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@Service
class DesignService implements Designs {

    static final String NOT_YET_AVAILABLE_DETAIL =
            "Create from a description arrives in Phase 2; start from a template or a Shop piece instead.";
    private static final Logger log = LoggerFactory.getLogger(DesignService.class);

    private final DesignRepository designs;
    private final DesignVersionRepository versions;
    private final Catalog catalog;
    private final Templates templates;
    private final GenerationJobs jobs;
    private final VersionMapper mapper;

    DesignService(DesignRepository designs, DesignVersionRepository versions, Catalog catalog, Templates templates,
            GenerationJobs jobs, VersionMapper mapper) {
        this.designs = designs;
        this.versions = versions;
        this.catalog = catalog;
        this.templates = templates;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @Transactional
    public DesignAccepted create(CreateDesignRequest request, Identity owner) {
        if (request.hasPrompt()) {
            throw ApiProblemException.unprocessable(ProblemCodes.NOT_YET_AVAILABLE, "Not yet available", NOT_YET_AVAILABLE_DETAIL);
        }
        Draft draft = request.source() == DesignSource.shop ? fromShop(request) : fromTemplate(request);
        if (!templates.isLive(draft.descriptor().id())) {
            throw ApiProblemException.unprocessable(ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                    "Template " + draft.descriptor().id() + " is paused by the studio; new designs from it are not accepted right now");
        }
        templates.validateParams(draft.descriptor(), draft.params());
        requireMaterial(draft.descriptor(), draft.material(), HttpStatus.UNPROCESSABLE_ENTITY);

        Instant now = Instant.now();
        DesignEntity design = designs.save(new DesignEntity(request.source(), draft.catalogItemSlug(), draft.title(), owner, now));
        Map<String, Object> spec = DesignSpecs.build(draft.descriptor(), draft.params(), draft.material());
        DesignVersionEntity version = versions.save(new DesignVersionEntity(design.id(), 1, null, spec,
                DesignSpecs.templateRef(draft.descriptor()), DesignVersionEntity.CREATED_BY_USER, now));
        UUID jobId = jobs.start(new GenerationRequest(design.id(), version.id(), 1, null, spec));
        version.attachJob(jobId);
        log.info("Design {} created from {} ({}) by {}; job {}", design.id(), request.source(), draft.descriptor().ref(), owner, jobId);
        return new DesignAccepted(design.id(), 1, jobId, GenerationJobs.eventsPath(jobId));
    }

    @Transactional
    public DesignAccepted editParams(UUID versionId, EditParamsRequest request) {
        DesignVersionEntity parent = versions.findById(versionId).orElseThrow(() -> ApiProblemException.notFound("Version", versionId));
        DesignEntity design = designs.lockById(parent.designId()).orElseThrow(() -> ApiProblemException.notFound("Design", parent.designId()));

        String templateId = DesignSpecs.templateId(parent.spec());
        TemplateDescriptor descriptor = templates.byId(templateId).orElseThrow(() -> ApiProblemException.unprocessable(
                ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                "Template " + templateId + " is no longer offered by the geometry service"));
        Map<String, Object> params = DesignSpecs.merge(DesignSpecs.params(parent.spec()), request.params());
        templates.validateParams(descriptor, params);
        String material = request.material() == null || request.material().isBlank() ? DesignSpecs.material(parent.spec()) : request.material();
        requireMaterial(descriptor, material, HttpStatus.UNPROCESSABLE_ENTITY);

        Instant now = Instant.now();
        int versionNo = versions.maxVersionNo(design.id()) + 1;
        Map<String, Object> spec = DesignSpecs.build(descriptor, params, material);
        DesignVersionEntity version = versions.save(new DesignVersionEntity(design.id(), versionNo, parent.id(), spec,
                DesignSpecs.templateRef(descriptor), DesignVersionEntity.CREATED_BY_USER, now));
        UUID jobId = jobs.start(new GenerationRequest(design.id(), version.id(), versionNo, parent.id(), spec));
        version.attachJob(jobId);
        design.touch(now);
        return new DesignAccepted(design.id(), versionNo, jobId, GenerationJobs.eventsPath(jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DesignResponse> find(UUID designId) {
        return designs.findById(designId).map(design -> mapper.toResponse(design,
                versions.findFirstByDesignIdOrderByVersionNoDesc(designId).orElse(null), versions.countByDesignId(designId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DesignVersionResponse> findVersion(UUID versionId) {
        return versions.findById(versionId).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public int attachGuest(UUID guestId, UUID userId) {
        int moved = designs.attachGuest(guestId, userId, Instant.now());
        if (moved > 0) {
            log.info("{} guest designs of {} now belong to user {}", moved, guestId, userId);
        }
        return moved;
    }

    @Transactional(readOnly = true)
    public DesignResponse get(UUID designId) {
        DesignEntity design = designs.findById(designId).orElseThrow(() -> ApiProblemException.notFound("Design", designId));
        DesignVersionEntity latest = versions.findFirstByDesignIdOrderByVersionNoDesc(designId).orElse(null);
        return mapper.toResponse(design, latest, versions.countByDesignId(designId));
    }

    @Transactional(readOnly = true)
    public List<DesignVersionResponse> versions(UUID designId) {
        if (!designs.existsById(designId)) {
            throw ApiProblemException.notFound("Design", designId);
        }
        return versions.findByDesignIdOrderByVersionNoDesc(designId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DesignVersionResponse version(UUID versionId) {
        return mapper.toResponse(requireVersion(versionId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> printability(UUID versionId) {
        DesignVersionEntity version = requireReady(versionId);
        Map<String, Object> report = version.printability();
        if (report == null) {
            throw ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY, "Version not ready",
                    "Version " + versionId + " has no printability report");
        }
        return report;
    }

    @Transactional(readOnly = true)
    public PriceBreakdown price(UUID versionId, String materialId) {
        DesignVersionEntity version = requireReady(versionId);
        var material = catalog.material(materialId).orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND,
                ProblemCodes.UNKNOWN_MATERIAL, "Unknown material", "Material '" + materialId + "' is not offered"));
        return mapper.price(version, material).orElseThrow(() -> ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY,
                "Version not ready", "Version " + versionId + " has no print estimate yet"));
    }

    private Draft fromShop(CreateDesignRequest request) {
        if (request.catalogItemSlug() == null || request.catalogItemSlug().isBlank()) {
            throw ApiProblemException.validation("catalog_item_slug is required when source is shop");
        }
        CatalogItemDto item = catalog.item(request.catalogItemSlug())
                .orElseThrow(() -> ApiProblemException.notFound("Catalog item", request.catalogItemSlug()));
        if (!item.available()) {
            throw ApiProblemException.unprocessable(ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                    item.name() + " is not available to order yet; its template is still being built.");
        }
        TemplateDescriptor descriptor = templates.byId(item.templateId()).orElseThrow(() -> ApiProblemException.unprocessable(
                ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                "Template " + item.templateId() + " for " + item.name() + " is not published by the geometry service yet"));
        Map<String, Object> params = DesignSpecs.merge(DesignSpecs.merge(descriptor.defaultParams(), item.defaultParams()), request.params());
        String material = blank(request.material()) ? item.defaultMaterial() : request.material();
        String title = blank(request.title()) ? item.name() : request.title();
        return new Draft(descriptor, params, material, title, item.slug());
    }

    private Draft fromTemplate(CreateDesignRequest request) {
        if (blank(request.templateId())) {
            throw ApiProblemException.validation("template_id is required when source is " + request.source()
                    + " (or catalog_item_slug with source shop)");
        }
        TemplateDescriptor descriptor = templates.byId(request.templateId())
                .orElseThrow(() -> ApiProblemException.notFound("Template", request.templateId()));
        Map<String, Object> params = DesignSpecs.merge(descriptor.defaultParams(), request.params());
        String material = blank(request.material())
                ? descriptor.materials().isEmpty() ? null : descriptor.materials().get(0)
                : request.material();
        String title = blank(request.title()) ? descriptor.name() : request.title();
        return new Draft(descriptor, params, material, title, null);
    }

    private void requireMaterial(TemplateDescriptor descriptor, String materialId, HttpStatus status) {
        if (blank(materialId) || catalog.material(materialId).isEmpty()) {
            throw new ApiProblemException(status, ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                    "Material '" + materialId + "' is not offered");
        }
        if (!descriptor.materials().isEmpty() && !descriptor.materials().contains(materialId)) {
            throw new ApiProblemException(status, ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                    "Material '" + materialId + "' is not offered for template " + descriptor.id() + "; choose one of " + descriptor.materials());
        }
    }

    private DesignVersionEntity requireVersion(UUID versionId) {
        return versions.findById(versionId).orElseThrow(() -> ApiProblemException.notFound("Version", versionId));
    }

    private DesignVersionEntity requireReady(UUID versionId) {
        DesignVersionEntity version = requireVersion(versionId);
        if (version.status() == VersionStatus.generating) {
            throw ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY, "Version not ready",
                    "Version " + versionId + " is still generating; follow the job's event stream and retry");
        }
        if (version.status() == VersionStatus.failed) {
            throw ApiProblemException.conflict(ProblemCodes.VERSION_NOT_READY, "Version not ready",
                    "Version " + versionId + " failed to generate; edit the parameters to try again");
        }
        return version;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private record Draft(TemplateDescriptor descriptor, Map<String, Object> params, String material, String title, String catalogItemSlug) {
    }
}
