package studio.aakar.api.media;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Phase 0: the geometry service already returns fetchable URLs, so hand them over untouched. */
@Component
class PassThroughAssetUrlResolver implements AssetUrlResolver {

    @Override
    public Map<String, Object> resolve(Map<String, Object> assets) {
        return assets == null ? Map.of() : assets;
    }
}
