package studio.aakar.api.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is making the current request, resolved once per request by the identity module's filter
 * (bearer token → {@link #user(UUID)}, {@code X-Aakar-Guest} header → {@link #guest(UUID)}, neither →
 * {@link #anonymous()}) and injected into controller methods that declare an {@code Identity} parameter.
 * Designs and carts belong to whichever identity created them; sign-in moves a guest's to the user.
 */
public record Identity(Kind kind, UUID id) {

    public static final String GUEST_HEADER = "X-Aakar-Guest";
    /** Request attribute under which the resolved identity is stored. */
    public static final String REQUEST_ATTRIBUTE = Identity.class.getName();

    private static final Identity ANONYMOUS = new Identity(Kind.anonymous, null);

    public enum Kind {
        user, guest, anonymous
    }

    public Identity {
        Objects.requireNonNull(kind, "kind");
        if (kind != Kind.anonymous && id == null) {
            throw new IllegalArgumentException(kind + " identity needs an id");
        }
    }

    public static Identity user(UUID userId) {
        return new Identity(Kind.user, userId);
    }

    public static Identity guest(UUID guestId) {
        return new Identity(Kind.guest, guestId);
    }

    public static Identity anonymous() {
        return ANONYMOUS;
    }

    public boolean isUser() {
        return kind == Kind.user;
    }

    public boolean isGuest() {
        return kind == Kind.guest;
    }

    public boolean isAnonymous() {
        return kind == Kind.anonymous;
    }

    /** The user id, or a 401 {@code unauthenticated} problem when the caller is a guest or anonymous. */
    public UUID requireUser() {
        if (!isUser()) {
            throw ApiProblemException.unauthenticated("Sign in to continue (send Authorization: Bearer <token>)");
        }
        return id;
    }

    /** The user or guest id, or a 401 problem when neither header was sent. */
    public UUID requireKnown() {
        if (isAnonymous()) {
            throw ApiProblemException.unauthenticated(
                    "Send Authorization: Bearer <token> for a signed-in customer or " + GUEST_HEADER + ": <uuid> for a guest");
        }
        return id;
    }

    @Override
    public String toString() {
        return isAnonymous() ? "anonymous" : kind + ":" + id;
    }
}
