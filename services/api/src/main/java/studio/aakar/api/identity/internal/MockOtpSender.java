package studio.aakar.api.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.identity.OtpSender;

/** ADR-0013: logs the code instead of sending an SMS. Default for {@code aakar.identity.otp.sender}. */
@Component
@ConditionalOnProperty(name = "aakar.identity.otp.sender", havingValue = "mock", matchIfMissing = true)
class MockOtpSender implements OtpSender {

    static final String NAME = "mock";
    private static final Logger log = LoggerFactory.getLogger(MockOtpSender.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void send(String phone, String code) {
        log.info("[mock otp] sign-in code for {} is {}", phone, code);
    }
}
