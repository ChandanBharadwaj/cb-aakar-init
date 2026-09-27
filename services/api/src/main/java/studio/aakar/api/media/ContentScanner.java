package studio.aakar.api.media;

/**
 * Looks at a customer upload before it is stored (ADR-0014, ADR-0013 adapter pattern). The default
 * ({@code aakar.uploads.scanner=terms}) flags files whose name mentions a configured term ({@code aakar.uploads.flag-terms},
 * the seed of the trademark guardrail); {@code noop} passes everything and is refused in production by the
 * {@link studio.aakar.api.shared.ProductionGuard}. A real scanner (malware, image moderation) is a drop-in implementation.
 */
public interface ContentScanner {

    /**
     * @param kind image or model
     * @param originalFilename the name the customer's browser sent, possibly null
     * @param format the detected format id ({@code png}, {@code stl}…)
     * @param bytes the file content
     */
    ScanResult scan(UploadKind kind, String originalFilename, String format, byte[] bytes);
}
