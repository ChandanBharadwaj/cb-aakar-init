package studio.aakar.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import studio.aakar.api.identity.OtpSender;
import studio.aakar.api.shared.ApiProblemException;

/** OTP request / verify / rate-limit rules. */
class OtpServiceTest {

    static final String PHONE = "+919876543210";
    static final IdentityProperties.Otp RULES = new IdentityProperties.Otp("mock", true, Duration.ofMinutes(5), 5, Duration.ofMinutes(15), 5);

    private final OtpRequestRepository repository = mock(OtpRequestRepository.class);
    private final Map<UUID, OtpRequestEntity> saved = new HashMap<>();
    private final List<String> sent = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
    private final OtpSender sender = new OtpSender() {
        @Override
        public String name() {
            return "recording";
        }

        @Override
        public void send(String phone, String code) {
            sent.add(phone + ":" + code);
        }
    };
    private OtpService otp;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            OtpRequestEntity r = inv.getArgument(0);
            if (r.id() == null) {
                ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            }
            saved.put(r.id(), r);
            return r;
        });
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<UUID>getArgument(0))));
        when(repository.countByPhoneAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        otp = new OtpService(repository, sender, RULES, true, clock);
    }

    @Test
    void requestIssuesASixDigitCodeSendsItAndExposesTheDevCode() {
        OtpService.OtpRequested requested = otp.request(PHONE);

        assertThat(requested.requestId()).isNotNull();
        assertThat(requested.expiresInS()).isEqualTo(300);
        assertThat(requested.devCode()).matches("\\d{6}");
        assertThat(sent).containsExactly(PHONE + ":" + requested.devCode());
        OtpRequestEntity row = saved.get(requested.requestId());
        assertThat(row.codeHash()).isEqualTo(OtpService.hash(requested.devCode())).isNotEqualTo(requested.devCode());
        assertThat(row.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
    }

    @Test
    void devCodeIsHiddenWhenNotExposed() {
        OtpService hidden = new OtpService(repository, sender, RULES, false, clock);
        OtpService.OtpRequested requested = hidden.request(PHONE);

        assertThat(requested.devCode()).isNull();
        assertThat(sent).hasSize(1); // still delivered through the sender
    }

    @Test
    void sixthRequestInFifteenMinutesIsRateLimited() {
        when(repository.countByPhoneAndCreatedAtAfter(PHONE, clock.instant().minus(Duration.ofMinutes(15)))).thenReturn(5L);

        assertThatThrownBy(() -> otp.request(PHONE)).isInstanceOfSatisfying(ApiProblemException.class, e -> {
            assertThat(e.status().value()).isEqualTo(429);
            assertThat(e.code()).isEqualTo("otp_rate_limited");
        });
        assertThat(sent).isEmpty();

        when(repository.countByPhoneAndCreatedAtAfter(PHONE, clock.instant().minus(Duration.ofMinutes(15)))).thenReturn(4L);
        assertThat(otp.request(PHONE).devCode()).isNotNull();
    }

    @Test
    void verifyReturnsThePhoneOnceAndRefusesReuse() {
        OtpService.OtpRequested requested = otp.request(PHONE);

        assertThat(otp.verify(requested.requestId(), requested.devCode())).isEqualTo(PHONE);
        assertThat(saved.get(requested.requestId()).verified()).isTrue();

        assertProblem(() -> otp.verify(requested.requestId(), requested.devCode()), "otp_invalid");
    }

    @Test
    void verifyAcceptsSurroundingWhitespace() {
        OtpService.OtpRequested requested = otp.request(PHONE);
        assertThat(otp.verify(requested.requestId(), " " + requested.devCode() + " ")).isEqualTo(PHONE);
    }

    @Test
    void wrongCodesCountAsAttemptsAndLockAfterFive() {
        OtpService.OtpRequested requested = otp.request(PHONE);
        String wrong = requested.devCode().equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 5; i++) {
            ApiProblemException e = assertProblem(() -> otp.verify(requested.requestId(), wrong), "otp_invalid");
            assertThat(e.status().value()).isEqualTo(401);
            assertThat(saved.get(requested.requestId()).attempts()).isEqualTo(i);
        }
        // even the right code is refused now
        ApiProblemException locked = assertProblem(() -> otp.verify(requested.requestId(), requested.devCode()), "otp_invalid");
        assertThat(locked.getMessage()).contains("Too many wrong attempts");
        assertThat(saved.get(requested.requestId()).verified()).isFalse();
    }

    @Test
    void unknownRequestIsInvalidNotNotFound() {
        assertProblem(() -> otp.verify(UUID.randomUUID(), "123456"), "otp_invalid");
    }

    @Test
    void expiredCodeIsReportedAsExpired() {
        OtpService.OtpRequested requested = otp.request(PHONE);
        clock.advance(Duration.ofMinutes(5));

        ApiProblemException e = assertProblem(() -> otp.verify(requested.requestId(), requested.devCode()), "otp_expired");
        assertThat(e.status().value()).isEqualTo(401);

        // a minute before expiry it still works
        clock.advance(Duration.ofMinutes(-2));
        assertThat(otp.verify(requested.requestId(), requested.devCode())).isEqualTo(PHONE);
    }

    private static ApiProblemException assertProblem(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call);
        assertThat(thrown).isInstanceOf(ApiProblemException.class);
        ApiProblemException problem = (ApiProblemException) thrown;
        assertThat(problem.code()).isEqualTo(code);
        return problem;
    }

    /** A clock the test moves by hand. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
