package studio.aakar.api.admin.internal;

import java.util.Optional;

/** Downloads a version's model file for the print pack; empty when the asset cannot be fetched. */
interface AssetFetcher {

    Optional<byte[]> fetch(String url);
}
