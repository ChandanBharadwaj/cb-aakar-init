package studio.aakar.api.admin.internal;

import studio.aakar.api.admin.StaffDto;

/** {@code StaffSession} in the management contract. */
record StaffSession(String accessToken, String tokenType, long expiresInS, StaffDto staff) {

    static final String BEARER = "Bearer";
}
