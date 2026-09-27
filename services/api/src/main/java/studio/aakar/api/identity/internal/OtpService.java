package studio.aakar.api.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.identity.OtpSender;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

/**
 * Sign-in codes: 6 digits, {@code aakar.identity.otp.ttl} (5 min) to live, at most
 * {@code max-requests-per-window} (5) requests per phone per {@code window} (15 min) → 429
 * {@code otp_rate_limited}, at most {@code max-attempts} (5) wrong codes per request → 401 {@code otp_invalid}.
 * Only the SHA-256 of a code is stored; the mock sender logs the plain code and, when
 * {@code expose-dev-code} is on outside production, the response carries it as {@code dev_code}.
 */
@Service
class OtpService {

    static final int CODE_DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRequestRepository requests;
    private final OtpSender sender;
    private final IdentityProperties.Otp rules;
    private final boolean exposeDevCode;
    private final Clock clock;

    @Autowired
    OtpService(OtpRequestRepository requests, OtpSender sender, IdentityProperties properties,
            studio.aakar.api.shared.AakarProperties aakar, Clock clock) {
        this(requests, sender, properties.otp(), properties.otp().exposeDevCode() && !aakar.production(), clock);
    }

    OtpService(OtpRequestRepository requests, OtpSender sender, IdentityProperties.Otp rules, boolean exposeDevCode, Clock clock) {
        this.requests = requests;
        this.sender = sender;
        this.rules = rules;
        this.exposeDevCode = exposeDevCode;
        this.clock = clock;
    }

    @Transactional
    public OtpRequested request(String phone) {
        Instant now = clock.instant();
        long recent = requests.countByPhoneAndCreatedAtAfter(phone, now.minus(rules.window()));
        if (recent >= rules.maxRequestsPerWindow()) {
            throw ApiProblemException.tooManyRequests(ProblemCodes.OTP_RATE_LIMITED, "Too many sign-in codes",
                    "At most " + rules.maxRequestsPerWindow() + " codes per " + rules.window().toMinutes() + " minutes for this phone; try again later");
        }
        String code = newCode();
        Instant expiresAt = now.plus(rules.ttl());
        OtpRequestEntity saved = requests.save(new OtpRequestEntity(phone, hash(code), now, expiresAt));
        sender.send(phone, code);
        return new OtpRequested(saved.id(), rules.ttl().toSeconds(), exposeDevCode ? code : null);
    }

    /** Consumes the request and returns the verified phone. */
    @Transactional
    public String verify(UUID requestId, String code) {
        Instant now = clock.instant();
        OtpRequestEntity request = requests.findById(requestId).orElseThrow(() -> invalid("Unknown sign-in request; request a new code"));
        if (request.verified()) {
            throw invalid("This code was already used; request a new one");
        }
        if (!now.isBefore(request.expiresAt())) {
            throw ApiProblemException.unauthorized(ProblemCodes.OTP_EXPIRED, "Code expired", "The code expired; request a new one");
        }
        if (request.attempts() >= rules.maxAttempts()) {
            throw invalid("Too many wrong attempts; request a new code");
        }
        if (code == null || !MessageDigest.isEqual(hash(code.trim()).getBytes(StandardCharsets.UTF_8),
                request.codeHash().getBytes(StandardCharsets.UTF_8))) {
            request.recordFailedAttempt();
            requests.saveAndFlush(request);
            int left = rules.maxAttempts() - request.attempts();
            throw invalid(left > 0 ? "Wrong code; " + left + " attempt" + (left == 1 ? "" : "s") + " left" : "Wrong code; request a new one");
        }
        request.markVerified(now);
        return request.phone();
    }

    private static ApiProblemException invalid(String detail) {
        return ApiProblemException.unauthorized(ProblemCodes.OTP_INVALID, "Invalid code", detail);
    }

    static String newCode() {
        return String.format("%0" + CODE_DIGITS + "d", RANDOM.nextInt((int) Math.pow(10, CODE_DIGITS)));
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 202 body of {@code POST /api/auth/otp/request}. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record OtpRequested(UUID requestId, long expiresInS, String devCode) {
    }
}
