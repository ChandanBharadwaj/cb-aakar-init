/**
 * Asset URLs. Phase 0 passes through the URLs the geometry service returned (local static files or a
 * public MinIO bucket); the {@link studio.aakar.api.media.AssetUrlResolver} seam is where presigning lands.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media")
package studio.aakar.api.media;
