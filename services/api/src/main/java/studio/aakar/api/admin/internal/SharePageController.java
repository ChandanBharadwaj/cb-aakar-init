package studio.aakar.api.admin.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Public {@code GET /api/share/{code}}: the storefront page behind the QR on the packaging card. Unauthenticated
 * (it sits under the customer chain's public routes) and rate-limited per client address, since codes are
 * short and public.
 */
@RestController
class SharePageController {

    static final int MAX_PER_MINUTE = 120;

    private final SharePageService pieces;
    private final Cache<String, AtomicInteger> hits = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(50_000).build();

    SharePageController(SharePageService pieces) {
        this.pieces = pieces;
    }

    @GetMapping("/api/share/{code}")
    SharedPieceDto get(@PathVariable String code, HttpServletRequest request) {
        String client = request.getHeader("X-Forwarded-For") != null ? request.getHeader("X-Forwarded-For") : request.getRemoteAddr();
        int count = hits.get(client, k -> new AtomicInteger()).incrementAndGet();
        if (count > MAX_PER_MINUTE) {
            throw ApiProblemException.tooManyRequests(ProblemCodes.RATE_LIMITED, "Too many requests", "Try again in a minute");
        }
        return pieces.resolve(code);
    }
}
