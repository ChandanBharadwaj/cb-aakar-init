package studio.aakar.api.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable {@link Clock} so time-dependent rules (OTP expiry, ETAs, order numbers) are testable. */
@Configuration
public class ClockConfig {

    /** The studio's time zone; dates such as an order's ETA are computed in it. */
    public static final java.time.ZoneId STUDIO_ZONE = java.time.ZoneId.of("Asia/Kolkata");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
