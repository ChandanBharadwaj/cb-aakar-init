package studio.aakar.api.media.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.media.MediaStore;
import studio.aakar.api.shared.ApiProblemException;

/** Serves files of the local media store (QC photos) publicly by their unguessable key. */
@RestController
@Tag(name = "media")
class MediaController {

    private final MediaStore store;

    MediaController(MediaStore store) {
        this.store = store;
    }

    @GetMapping("/media/{*key}")
    @Operation(summary = "A stored media file by key")
    ResponseEntity<Resource> file(@PathVariable String key) {
        String clean = key.startsWith("/") ? key.substring(1) : key;
        Resource resource = store.load(clean).orElseThrow(() -> ApiProblemException.notFound("Media", clean));
        MediaType type = MediaTypeFactory.getMediaType(clean).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(resource);
    }
}
