package studio.aakar.api.media.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.aakar.api.media.UploadDto;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;

/** Customer content for the Chhaap and Swaroop: a user or guest identity is required (like the cart). */
@RestController
@RequestMapping("/api/uploads")
@Tag(name = "uploads")
class UploadsController {

    private final Uploads uploads;

    UploadsController(Uploads uploads) {
        this.uploads = uploads;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload customer content (an image for a relief, or a model file for Swaroop or a hero form)", description = """
            Multipart `file` and `kind` (`image` | `model`). Images: png, jpg, webp, heic up to 15 MB. Models: stl, glb, 3mf, obj, \
            ply, off, gltf up to 50 MB. The format is checked by extension and by the file's first bytes (422 `unsupported_format`); \
            too large → 413 `payload_too_large`. Needs `Authorization` or `X-Aakar-Guest` (401 otherwise). A file the content \
            scanner flags answers `status: pending_review` and cannot be placed on a design until a reviewer approves it.""")
    ResponseEntity<UploadDto> create(@RequestPart("file") MultipartFile file, @RequestParam("kind") String kind, Identity identity) {
        identity.requireKnown();
        UploadKind parsed = UploadKind.parse(kind).orElseThrow(() -> ApiProblemException.validation("kind must be image or model"));
        return ResponseEntity.status(HttpStatus.CREATED).body(uploads.store(identity, parsed, file));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of your uploads", description = "Owner only: 404 for anybody else. Poll it while `status` is `pending_review`; "
            + "`message` explains a rejection.")
    UploadDto get(@PathVariable UUID id, Identity identity) {
        return uploads.findOwned(id, identity).orElseThrow(() -> ApiProblemException.notFound("Upload", id));
    }
}
