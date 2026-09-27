package studio.aakar.api.identity;

/** {@code Session} in the OpenAPI document: the token plus what moved over from the guest identity. */
public record SessionResponse(String accessToken, String tokenType, long expiresInS, UserDto user, Attached attached) {

    public static final String BEARER = "Bearer";

    /** Counts of what the sign-in attached from the guest identity. */
    public record Attached(int designs, int cartItems) {
        public static final Attached NOTHING = new Attached(0, 0);
    }
}
