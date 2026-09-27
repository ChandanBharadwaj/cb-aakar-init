/**
 * Assets. Phase 0 passes through the URLs the geometry service returned (local static files or a public
 * MinIO bucket); the {@link studio.aakar.api.media.AssetUrlResolver} seam is where presigning lands. Since the
 * management API (ADR-0012) the module also keeps staff uploads (QC photos) through {@link studio.aakar.api.media.MediaStore}:
 * files under {@code aakar.media.dir} served at {@code GET /media/{key}}; an S3 store is a drop-in implementation.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media")
package studio.aakar.api.media;
