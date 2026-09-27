package studio.aakar.api.admin.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.pricing.PriceCalculator;
import studio.aakar.api.pricing.PriceInputs;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyStore;
import studio.aakar.api.pricing.PricingPolicyStore.PricingPolicyInfo;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

@RestController
@RequestMapping("/admin/api/pricing")
@Tag(name = "admin · pricing")
@SecurityRequirement(name = "staffBearer")
class AdminPricingController {

    static final String PREVIEW_VERSION = "preview";

    private final PricingPolicyStore policies;
    private final PriceCalculator calculator;
    private final Catalog catalog;
    private final AuditLog audit;

    AdminPricingController(PricingPolicyStore policies, PriceCalculator calculator, Catalog catalog, AuditLog audit) {
        this.policies = policies;
        this.calculator = calculator;
        this.catalog = catalog;
        this.audit = audit;
    }

    @GetMapping("/policies")
    @Operation(summary = "All pricing policy versions, newest first")
    List<PolicyVersionDto> history(StaffPrincipal staff) {
        return policies.history().stream().map(PolicyVersionDto::from).toList();
    }

    @GetMapping("/policies/active")
    @Operation(summary = "The active pricing policy")
    PolicyVersionDto active(StaffPrincipal staff) {
        return policies.activeInfo().map(PolicyVersionDto::from).orElseGet(() -> {
            PricingPolicy active = policies.active(); // seeds when the table is empty
            return policies.activeInfo().map(PolicyVersionDto::from)
                    .orElse(new PolicyVersionDto(active.version(), true, PricingPolicyInput.from(active), null, Instant.now(), "seed"));
        });
    }

    @PostMapping("/policies")
    @Operation(summary = "Publish a new policy version (becomes active)", description = "Owner only (403 `forbidden`). 409 `policy_version_exists` "
            + "for a reused version; 422 `validation_failed` for a body outside the schema; 422 `unknown_family` when `family_rules` names a "
            + "family that is not in the catalog. Audited as `pricing.publish` with the previous active policy as `before`.")
    ResponseEntity<PolicyVersionDto> publish(@Valid @RequestBody PublishRequest request, StaffPrincipal staff) {
        staff.requireOwner();
        request.policy().familyIds().forEach(this::requireFamilyKnown);
        PricingPolicy before = policies.active();
        PricingPolicyInfo published = policies.publish(request.policy().toPolicy(request.version()), staff.email(), request.note());
        audit.record(staff.email(), AuditLog.PRICING_PUBLISH, published.version(), before, published.policy());
        return ResponseEntity.status(HttpStatus.CREATED).body(PolicyVersionDto.from(published));
    }

    @PostMapping("/preview")
    @Operation(summary = "What a piece would cost under a draft policy", description = "Runs the price calculator with the supplied policy "
            + "(version `preview`), material, extruded volume and print time. With `family_id` the family's default hardware (at the draft's "
            + "`hardware_markup_pct`), its setup fee and its minimum apply, as for a piece of that family. 422 `unknown_material` for an unknown "
            + "material; 422 `unknown_family` for a `family_id` that is not in the catalog.")
    PriceBreakdown preview(@Valid @RequestBody PreviewRequest request, StaffPrincipal staff) {
        MaterialDto material = catalog.material(request.material()).orElseThrow(() -> ApiProblemException.unprocessable(
                ProblemCodes.UNKNOWN_MATERIAL, "Unknown material", "Material '" + request.material() + "' is not known"));
        PriceInputs.Context context = PriceInputs.Context.NONE;
        if (request.familyId() != null && !request.familyId().isBlank()) {
            String familyId = request.familyId().trim();
            requireFamilyKnown(familyId);
            context = new PriceInputs.Context(familyId, catalog.familyHardware(familyId).stream()
                    .flatMap(ref -> catalog.hardwareItem(ref.sku()).stream()
                            .map(item -> new PriceInputs.Hardware(item.sku(), item.name(), ref.qty() == null ? 1 : ref.qty(), item.unitCostPaise())))
                    .toList());
        }
        return calculator.price(new PriceInputs.PrintEstimate(request.printSeconds(), request.extrudedVolumeCm3()),
                new PriceInputs.Material(material.id(), material.densityGCm3(), material.finishClass(), material.ratePerGPaise()),
                request.policy().toPolicy(PREVIEW_VERSION), context);
    }

    private void requireFamilyKnown(String familyId) {
        if (!catalog.familyExists(familyId)) { // existence only: publishing a policy must not depend on the geometry service
            throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_FAMILY, "Unknown family",
                    "Family '" + familyId + "' is not in the catalog (see GET /admin/api/families)");
        }
    }

    record PublishRequest(
            @NotBlank(message = "version is required")
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,40}$", message = "version must be 3-40 characters of letters, digits, dot, underscore or dash")
            String version,
            @NotNull(message = "policy is required") @Valid PricingPolicyInput policy,
            @Size(max = 200, message = "note must be at most 200 characters") String note) {
    }

    record PreviewRequest(
            @NotNull(message = "policy is required") @Valid PricingPolicyInput policy,
            @NotBlank(message = "material is required") String material,
            @NotNull(message = "extruded_volume_cm3 is required") @Positive(message = "extruded_volume_cm3 must be positive") Double extrudedVolumeCm3,
            @NotNull(message = "print_seconds is required") @Positive(message = "print_seconds must be positive") Integer printSeconds,
            @Size(max = 40, message = "family_id must be at most 40 characters") String familyId) {
    }

    /** {@code PricingPolicyVersion} in the management contract. */
    record PolicyVersionDto(String version, boolean active, PricingPolicyInput policy, String note, Instant createdAt, String createdBy) {

        static PolicyVersionDto from(PricingPolicyInfo info) {
            return new PolicyVersionDto(info.version(), info.active(), PricingPolicyInput.from(info.policy()), info.note(), info.createdAt(),
                    info.createdBy());
        }
    }
}
