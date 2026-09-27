package studio.aakar.api.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.cart.Carts;
import studio.aakar.api.design.Designs;
import studio.aakar.api.identity.SessionResponse;
import studio.aakar.api.identity.UserDto;
import studio.aakar.api.identity.Users;
import studio.aakar.api.media.Uploads;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;

/** Sign-in: verify the code, find or create the user, issue a token, attach the guest's designs, uploads and cart. */
@Service
class AuthService implements Users {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otp;
    private final UserRepository users;
    private final SessionService sessions;
    private final Designs designs;
    private final Carts carts;
    private final Uploads uploads;
    private final Clock clock;

    AuthService(OtpService otp, UserRepository users, SessionService sessions, Designs designs, Carts carts, Uploads uploads, Clock clock) {
        this.otp = otp;
        this.users = users;
        this.sessions = sessions;
        this.designs = designs;
        this.carts = carts;
        this.uploads = uploads;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse signIn(UUID requestId, String code, Identity caller) {
        String phone = otp.verify(requestId, code);
        Instant now = clock.instant();
        UserEntity user = users.findByPhone(phone).orElseGet(() -> {
            UserEntity created = users.save(new UserEntity(phone, now));
            log.info("User {} created for {}", created.id(), phone);
            return created;
        });
        SessionService.IssuedToken token = sessions.issue(user.id());

        SessionResponse.Attached attached = SessionResponse.Attached.NOTHING;
        if (caller.isGuest()) {
            int movedDesigns = designs.attachGuest(caller.id(), user.id());
            // The photos and model files on those designs follow them; the contract's `attached` counts designs and cart items only.
            int movedUploads = uploads.attachGuest(caller.id(), user.id());
            int movedItems = carts.mergeGuestCart(caller.id(), user.id());
            attached = new SessionResponse.Attached(movedDesigns, movedItems);
            log.info("Guest {} attached to user {}: {} designs, {} uploads, {} cart items", caller.id(), user.id(), movedDesigns, movedUploads,
                    movedItems);
        }
        return new SessionResponse(token.accessToken(), SessionResponse.BEARER, token.expiresInS(), user.toDto(), attached);
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        return requireUser(userId).toDto();
    }

    @Transactional
    public UserDto updateProfile(UUID userId, String name, String email) {
        UserEntity user = requireUser(userId);
        user.update(name, email, clock.instant());
        return user.toDto();
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessions.revoke(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> find(UUID userId) {
        return users.findById(userId).map(UserEntity::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserDto> findAll(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream().map(UserEntity::toDto).collect(Collectors.toMap(UserDto::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findIdsByPhoneContaining(String digits) {
        if (digits == null || digits.isBlank()) {
            return List.of();
        }
        return users.findIdsByPhoneContaining(digits.trim());
    }

    private UserEntity requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiProblemException.unauthenticated("This account no longer exists"));
    }
}
