package studio.aakar.api.media.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.media.ContentScanner;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.UploadKind;

/**
 * {@code aakar.uploads.scanner=noop}: every upload is clean. A local convenience only; the production guard refuses it
 * (ADR-0013), because the review queue would never see anything.
 */
@Component
@ConditionalOnProperty(name = "aakar.uploads.scanner", havingValue = "noop")
class NoopContentScanner implements ContentScanner {

    @Override
    public ScanResult scan(UploadKind kind, String originalFilename, String format, byte[] bytes) {
        return ScanResult.clean();
    }
}
