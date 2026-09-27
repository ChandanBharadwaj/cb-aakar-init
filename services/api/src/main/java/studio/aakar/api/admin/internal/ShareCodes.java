package studio.aakar.api.admin.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.shared.AakarProperties;

/**
 * Mints the 8-character base32 code printed (as text and QR) on the packaging card, once per order at the first
 * card request. The public storefront page {@code {aakar.web.url}/k/{code}} (reprint / remix) arrives later; the
 * row already names the design and version to open.
 */
@Service
class ShareCodes {

    /** RFC 4648 base32 alphabet: unambiguous in print, no padding. */
    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    static final int LENGTH = 8;
    static final String PATH = "/k/";
    static final int MAX_ATTEMPTS = 20;
    private static final Logger log = LoggerFactory.getLogger(ShareCodes.class);

    private final ShareCodeRepository codes;
    private final Random random;
    private final String webUrl;
    private final Clock clock;

    @Autowired
    ShareCodes(ShareCodeRepository codes, AakarProperties properties, Clock clock) {
        this(codes, new SecureRandom(), properties.web().url(), clock);
    }

    ShareCodes(ShareCodeRepository codes, Random random, String webUrl, Clock clock) {
        this.codes = codes;
        this.random = random;
        this.webUrl = webUrl;
        this.clock = clock;
    }

    /** The order's code, minted on first call; later calls return the same code. */
    @Transactional
    public ShareCodeEntity mintFor(UUID orderId, UUID designId, UUID versionId) {
        return codes.findByOrderId(orderId).orElseGet(() -> {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                String code = generate(random);
                if (!codes.existsById(code)) {
                    ShareCodeEntity saved = codes.save(new ShareCodeEntity(code, orderId, designId, versionId, clock.instant()));
                    log.info("Share code {} minted for order {} (design {}, version {})", code, orderId, designId, versionId);
                    return saved;
                }
            }
            throw new IllegalStateException("Could not mint a unique share code after " + MAX_ATTEMPTS + " attempts");
        });
    }

    String link(String code) {
        return webUrl + PATH + code;
    }

    static String generate(Random random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
