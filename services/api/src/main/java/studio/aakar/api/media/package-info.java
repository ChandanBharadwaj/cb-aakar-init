/**
 * Assets. Phase 0 passes through the URLs the geometry service returned (local static files or a public
 * MinIO bucket); the {@link studio.aakar.api.media.AssetUrlResolver} seam is where presigning lands. Since the
 * management API (ADR-0012) the module also keeps staff uploads (QC photos) through {@link studio.aakar.api.media.MediaStore}:
 * files under {@code aakar.media.dir} served at {@code GET /media/{key}}; an S3 store is a drop-in implementation.
 *
 * <p>Customer uploads (ADR-0014, plan §4): {@link studio.aakar.api.media.Uploads} stores photos and model files sent to
 * {@code POST /api/uploads} after sniffing their format and passing them through the
 * {@link studio.aakar.api.media.ContentScanner}; flagged files wait in {@code pending_review} for a content review in the
 * portal. The browser gets the public URL; design specs carry the internal URL the geometry service fetches.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Media")
package studio.aakar.api.media;
