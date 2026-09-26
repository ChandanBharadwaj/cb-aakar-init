package studio.aakar.api.media;

import java.util.Map;

/**
 * Turns the stored {@code assets} map of a design version into the map the storefront should see.
 * Phase 0: identity. Later: swap storage keys for short-TTL presigned URLs.
 */
public interface AssetUrlResolver {

    /** @param assets keyed by output kind (glb, 3mf, stl, usdz, thumb), each with key/url/bytes/content_type */
    Map<String, Object> resolve(Map<String, Object> assets);
}
