package studio.aakar.api.design.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.FamilyDto;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.CreateDesignRequest;
import studio.aakar.api.design.DesignAccepted;
import studio.aakar.api.design.DesignResponse;
import studio.aakar.api.design.DesignSource;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.design.EditParamsRequest;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.media.UploadDto;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceInputs;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.studio.GenerationJobs;
import studio.aakar.api.studio.GenerationRequest;
import studio.aakar.api.templates.FamilyLimits;
import studio.aakar.api.templates.FeatureLabels;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

/**
 * Starts designs and their versions. Four ways in: a Shop item, a template (Remix-lite), an outcome family (Avatar:
 * {@code family_id} + the Chhaap {@code features}) and the Swaroop path ({@code source: upload}, family {@code raw_print},
 * one {@code hero_mesh}). Every path checks the template is live, its params, the finish (descriptor list and the
 * family's material rules) and the features (templates module), then resolves each content source to the customer's
 * own ready upload and writes the geometry service's URL into the spec.
 */
@Service
class DesignService implements Designs {

    static final String NOT_YET_AVAILABLE_DETAIL =
            "Create from a description arrives in Phase 2; start from a template or a Shop piece instead.";
    static final int TITLE_MAX = 80;
    private static final Logger log = LoggerFactory.getLogger(DesignService.class);

    private final DesignRepository designs;
    private final DesignVersionRepository versions;
    private final Catalog catalog;
    private final Templates templates;
    private final Uploads uploads;
    private final GenerationJobs jobs;
    private final VersionMapper mapper;

    DesignService(DesignRepository designs, DesignVersionRepository versions, Catalog catalog, Templates templates, Uploads uploads,
            GenerationJobs jobs, VersionMapper mapper) {
        this.designs = designs;
        this.versions = versions;
        this.catalog = catalog;
        this.templates = templates;
        this.uploads = uploads;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @Transactional
    public DesignAccepted create(CreateDesignRequest request, Identity owner) {
        if (request.hasPrompt() && !request.hasFamily()) {
            throw ApiProblemException.unprocessable(ProblemCodes.NOT_YET_AVAILABLE, "Not yet available", NOT_YET_AVAILABLE_DETAIL);
        }
        if (request.source() == DesignSource.upload && !request.hasFamily()) {
            throw ApiProblemException.validation("family_id is required when source is upload (your own model file prints as family raw_print)");
        }
        Draft draft = request.source() == DesignSource.shop ? fromShop(request)
                : request.hasFamily() ? fromFamily(request) : fromTemplate(request);
        requireSourceFitsFamily(request.source(), draft.family());
        if (!templates.isLive(draft.descriptor().id())) {
            throw ApiProblemException.unprocessable(ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                    "Template " + draft.descriptor().id() + " is paused by the studio; new designs from it are not accepted right now");
        }
        templates.validateParams(draft.descriptor(), draft.params());
        requireMaterial(draft.descriptor(), draft.family(), draft.material());
        List<Map<String, Object>> features = contentFeatures(request.features(), draft.descriptor(), draft.family(), owner, Set.of());

        Instant now = Instant.now();
        String familyId = draft.family() == null ? null : draft.family().id();
        DesignEntity design = designs.save(new DesignEntity(request.source(), draft.catalogItemSlug(), familyId, draft.title(), owner, now));
        Map<String, Object> spec = DesignSpecs.build(draft.descriptor(), draft.params(), draft.material(), features);
        DesignVersionEntity version = versions.save(new DesignVersionEntity(design.id(), 1, null, spec, DesignSpecs.templateRef(draft.descriptor()),
                expectedHardware(draft.descriptor(), draft.family()), DesignVersionEntity.CREATED_BY_USER, now));
        UUID jobId = jobs.start(new GenerationRequest(design.id(), version.id(), 1, null, spec));
        version.attachJob(jobId);
        log.info("Design {} created from {} ({}, family {}, {} feature(s)) by {}; job {}", design.id(), request.source(), draft.descriptor().ref(),
                familyId, features.size(), owner, jobId);
        return new DesignAccepted(design.id(), 1, jobId, GenerationJobs.eventsPath(jobId));
    }

    /**
     * A new version from a parent: params merged over the parent's, an optional finish swap and, when {@code features}
     * is present, the complete new list of content features (an empty list clears them; absent keeps the parent's).
     * Uploads already on the design stay usable by whoever edits it; a new upload must belong to the caller.
     */
    @Transactional
    public DesignAccepted editParams(UUID versionId, EditParamsRequest request, Identity caller) {
        DesignVersionEntity parent = versions.findById(versionId).orElseThrow(() -> ApiProblemException.notFound("Version", versionId));
        DesignEntity design = designs.lockById(parent.designId()).orElseThrow(() -> ApiProblemException.notFound("Design", parent.designId()));

        String templateId = DesignSpecs.templateId(parent.spec());
        TemplateDescriptor descriptor = templates.byId(templateId).orElseThrow(() -> ApiProblemException.unprocessable(
                ProblemCodes.TEMPLATE_NOT_AVAILABLE, "Template not available",
                "Template " + templateId + " is no longer offered by the geometry service"));
        FamilyDto family = familyFor(DesignSpecs.family(parent.spec())).orElse(null);
        Map<String, Object> params = DesignSpecs.merge(DesignSpecs.params(parent.spec()), request.params());
        templates.validateParams(descriptor, params);
        String material = blank(request.material()) ? DesignSpecs.material(parent.spec()) : request.material();
        requireMaterial(descriptor, family, material);
        List<Map<String, Object>> features = request.features() == null
                ? templates.validateFeatures(descriptor, limits(descriptor, family), DesignSpecs.features(parent.spec()))
                : contentFeatures(request.features(), descriptor, family, caller, DesignSpecs.uploadIds(parent.spec()));

        Instant now = Instant.now();
        int versionNo = versions.maxVersionNo(design.id()) + 1;
        Map<String, Object> spec = DesignSpecs.build(descriptor, params, material, features);
        DesignVersionEntity version = versions.save(new DesignVersionEntity(design.id(), versionNo, parent.id(), spec,
                DesignSpecs.templateRef(descriptor), expectedHardware(descriptor, family), DesignVersionEntity.CREATED_BY_USER, now));
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

    @Override
    @Transactional(readOnly = true)
    public PriceInputs.Context priceContext(DesignVersionResponse version) {
        return version == null ? PriceInputs.Context.NONE : mapper.context(version);
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

    // ---- the ways in --------------------------------------------------------------------------------------------------

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
        FamilyDto family = familyFor(item.familyId() != null ? item.familyId() : descriptor.family()).orElse(null);
        if (request.hasFamily() && (family == null || !family.id().equals(request.familyId().trim()))) {
            throw ApiProblemException.validation("catalog item " + item.slug() + " is not in family " + request.familyId().trim());
        }
        Map<String, Object> params = DesignSpecs.merge(DesignSpecs.merge(descriptor.defaultParams(), item.defaultParams()), request.params());
        String material = blank(request.material()) ? item.defaultMaterial() : request.material();
        return new Draft(descriptor, family, params, material, title(request, item.name()), item.slug());
    }

    private Draft fromFamily(CreateDesignRequest request) {
        String familyId = request.familyId().trim();
        FamilyDto family = catalog.family(familyId).orElseThrow(() -> ApiProblemException.notFound(ProblemCodes.UNKNOWN_FAMILY, "Unknown family",
                "Family " + familyId + " was not found"));
        if (!family.available() || !family.ready()) {
            throw ApiProblemException.unprocessable(ProblemCodes.FAMILY_NOT_AVAILABLE, "Family not available",
                    label(family) + " is not available to order right now.");
        }
        boolean chosen = !blank(request.templateId());
        String templateId = chosen ? request.templateId().trim() : family.defaultTemplateId();
        TemplateDescriptor descriptor = templates.byId(templateId).orElseThrow(() -> chosen
                ? ApiProblemException.notFound("Template", templateId)
                : ApiProblemException.unprocessable(ProblemCodes.FAMILY_NOT_AVAILABLE, "Family not available",
                        label(family) + " has no template to make it from right now."));
        if (!family.id().equals(descriptor.family())) {
            throw ApiProblemException.validation("template_id " + descriptor.id() + " belongs to family " + descriptor.family() + ", not " + family.id()
                    + "; leave template_id out to use the family's default");
        }
        Map<String, Object> params = DesignSpecs.merge(descriptor.defaultParams(), request.params());
        String material = blank(request.material()) ? defaultMaterial(descriptor, family) : request.material();
        return new Draft(descriptor, family, params, material, title(request, label(family)), null);
    }

    private Draft fromTemplate(CreateDesignRequest request) {
        if (blank(request.templateId())) {
            throw ApiProblemException.validation("template_id is required when source is " + request.source()
                    + " (or catalog_item_slug with source shop, or family_id)");
        }
        TemplateDescriptor descriptor = templates.byId(request.templateId())
                .orElseThrow(() -> ApiProblemException.notFound("Template", request.templateId()));
        FamilyDto family = familyFor(descriptor.family()).orElse(null);
        Map<String, Object> params = DesignSpecs.merge(descriptor.defaultParams(), request.params());
        String material = blank(request.material()) ? defaultMaterial(descriptor, family) : request.material();
        return new Draft(descriptor, family, params, material, title(request, descriptor.name()), null);
    }

    /** {@code source: upload} is the raw family (Swaroop) and the raw family is only ever {@code source: upload}. */
    private static void requireSourceFitsFamily(DesignSource source, FamilyDto family) {
        boolean raw = family != null && FamilyDto.KIND_RAW.equals(family.kind());
        if (source == DesignSource.upload && !raw) {
            throw ApiProblemException.validation("source upload prints your own model file as it is: family_id must be the raw print family");
        }
        if (raw && source != DesignSource.upload) {
            throw ApiProblemException.validation(label(family) + " starts from your own model file: send source upload");
        }
    }

    // ---- finish, content and hardware ---------------------------------------------------------------------------------

    private void requireMaterial(TemplateDescriptor descriptor, FamilyDto family, String materialId) {
        MaterialDto material = (blank(materialId) ? Optional.<MaterialDto>empty() : catalog.material(materialId))
                .orElseThrow(() -> ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                        "Material '" + materialId + "' is not offered"));
        if (!descriptor.materials().isEmpty() && !descriptor.materials().contains(material.id())) {
            throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                    "Material '" + materialId + "' is not offered for template " + descriptor.id() + "; choose one of " + descriptor.materials());
        }
        String broken = family == null ? null : materialRuleProblem(family, material);
        if (broken != null) {
            throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_MATERIAL, "Material not offered for this family", broken,
                    Map.of("family_id", family.id(), "material", material.id()));
        }
    }

    /** Why {@code material} breaks the family's {@code material_rules}, in the customer's words; null when it does not. */
    private String materialRuleProblem(FamilyDto family, MaterialDto material) {
        FamilyDto.MaterialRules rules = family.materialRules();
        if (rules == null) {
            return null;
        }
        if (rules.allowed() != null && !rules.allowed().contains(material.id())) {
            String names = rules.allowed().stream().map(id -> catalog.material(id).map(MaterialDto::name).orElse(id)).collect(Collectors.joining(", "));
            return label(family) + " is made in " + names + " only; " + material.name() + " isn't offered for it.";
        }
        if (rules.heatSafeOnly() && !material.heatSafe()) {
            return label(family) + " needs a heat-safe finish; " + material.name() + " isn't heat-safe.";
        }
        if (rules.excludedFinishClasses().contains(material.finishClass())) {
            return label(family) + " isn't offered in " + material.finishClass() + " finishes; choose another finish.";
        }
        return null;
    }

    /** The first finish the template offers that the family's rules allow (every material when the template names none). */
    private String defaultMaterial(TemplateDescriptor descriptor, FamilyDto family) {
        List<String> candidates = descriptor.materials().isEmpty() ? catalog.materials().stream().map(MaterialDto::id).toList() : descriptor.materials();
        for (String id : candidates) {
            Optional<MaterialDto> material = catalog.material(id);
            if (material.isPresent() && (family == null || materialRuleProblem(family, material.get()) == null)) {
                return id;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Validates the features (templates module) and fills each {@code content_source} from the upload: the URL the
     * geometry service fetches, the format and the origin. {@code onDesign} are uploads the parent version already
     * carries; any other upload must belong to {@code caller}.
     */
    private List<Map<String, Object>> contentFeatures(List<Map<String, Object>> requested, TemplateDescriptor descriptor, FamilyDto family,
            Identity caller, Set<UUID> onDesign) {
        List<Map<String, Object>> normalised = templates.validateFeatures(descriptor, limits(descriptor, family), requested == null ? List.of() : requested);
        List<Map<String, Object>> resolved = new ArrayList<>(normalised.size());
        for (int i = 0; i < normalised.size(); i++) {
            Map<String, Object> feature = normalised.get(i);
            if (feature.get("source") instanceof Map<?, ?> source) {
                Map<String, Object> copy = new LinkedHashMap<>(feature);
                copy.put("source", contentSource(i, String.valueOf(feature.get("type")), String.valueOf(source.get("upload_id")), caller, onDesign));
                feature = copy;
            }
            resolved.add(feature);
        }
        return resolved;
    }

    private Map<String, Object> contentSource(int index, String type, String uploadIdText, Identity caller, Set<UUID> onDesign) {
        UUID uploadId = UUID.fromString(uploadIdText);
        Map<String, Object> where = Map.of("feature", index, "upload_id", uploadId.toString());
        UploadDto upload = (onDesign.contains(uploadId) ? uploads.find(uploadId) : uploads.findOwned(uploadId, caller))
                .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Not found",
                        "We could not find the file for the " + FeatureLabels.of(type) + "; upload it again.", where));
        String label = FeatureLabels.of(type);
        switch (upload.status()) {
            case pending_review -> throw new ApiProblemException(HttpStatus.CONFLICT, ProblemCodes.UPLOAD_NOT_READY, "Upload not ready",
                    "The studio is still checking the file for the " + label + "; you can use it once it is cleared.", where);
            case rejected -> throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.UPLOAD_REJECTED, "Upload rejected",
                    upload.message() == null ? "The file for the " + label + " can't be printed; choose a different one."
                            : "The file for the " + label + " can't be printed: " + upload.message(), where);
            default -> {
            }
        }
        UploadKind needed = FeatureLabels.HERO_MESH.equals(type) ? UploadKind.model : UploadKind.image;
        if (upload.kind() != needed) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.UNSUPPORTED_FORMAT, "Unsupported format",
                    needed == UploadKind.image ? "A photo relief (Chhavi) needs a photo (PNG, JPG, WEBP or HEIC), not a model file."
                            : "Your own 3D form (Roop) needs a model file (.stl, .obj, .3mf, .glb, .gltf, .ply or .off), not a photo.", where);
        }
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("upload_id", uploadId.toString());
        source.put("url", upload.internalUrl());
        source.put("format", upload.format());
        source.put("origin", upload.origin() == null ? UploadDto.ORIGIN_UPLOAD : upload.origin());
        if (upload.provider() != null) {
            source.put("provider", upload.provider());
        }
        return source;
    }

    /** What the version packs until the geometry result says otherwise: the descriptor's hardware, else the family default. */
    static List<Map<String, Object>> expectedHardware(TemplateDescriptor descriptor, FamilyDto family) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!descriptor.hardware().isEmpty()) {
            descriptor.hardware().stream().filter(h -> h.sku() != null).forEach(h -> rows.add(hardwareRow(h.sku(), h.qty())));
        } else if (family != null) {
            family.hardware().stream().filter(h -> h.sku() != null).forEach(h -> rows.add(hardwareRow(h.sku(), h.qty() == null ? 1 : h.qty())));
        }
        return rows;
    }

    private static Map<String, Object> hardwareRow(String sku, int qty) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sku", sku);
        row.put("qty", Math.max(1, qty));
        return row;
    }

    static FamilyLimits limits(TemplateDescriptor descriptor, FamilyDto family) {
        if (family == null) {
            return FamilyLimits.none(descriptor.family());
        }
        FamilyDto.SizeEnvelope envelope = family.sizeEnvelopeMm();
        return new FamilyLimits(family.id(), family.kind(), family.contentSlot() == null ? null : family.contentSlot().maxTextChars(),
                envelope == null ? null : envelope.minLongestMm(), envelope == null ? null : envelope.maxLongestMm());
    }

    private Optional<FamilyDto> familyFor(String familyId) {
        return blank(familyId) ? Optional.empty() : catalog.family(familyId.trim());
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /** "Saathi · Keychain & bag charm": the codename always travels with its plain descriptor. */
    static String label(FamilyDto family) {
        return family.codename() == null || family.codename().isBlank() ? family.name() : family.codename() + " · " + family.name();
    }

    /** The request's title, else the customer's words, else {@code fallback}; at most {@value #TITLE_MAX} characters. */
    private static String title(CreateDesignRequest request, String fallback) {
        String title = !blank(request.title()) ? request.title().trim() : request.hasPrompt() ? request.prompt().trim() : fallback;
        if (title == null || title.isBlank()) {
            title = "Design";
        }
        return title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX).trim() : title;
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

    private record Draft(TemplateDescriptor descriptor, FamilyDto family, Map<String, Object> params, String material, String title,
            String catalogItemSlug) {
    }
}
