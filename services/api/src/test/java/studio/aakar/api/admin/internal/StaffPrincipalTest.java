package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.shared.ApiProblemException;

/** Role gating: configuration writes are owner-only. */
class StaffPrincipalTest {

    @Test
    void ownersMayChangeConfiguration() {
        StaffPrincipal owner = new StaffPrincipal(UUID.randomUUID(), "studio@aakar.local", "Aakar Studio", StaffRole.owner);
        assertThat(owner.isOwner()).isTrue();
        assertThatCode(owner::requireOwner).doesNotThrowAnyException();
        assertThat(owner.toDto().role()).isEqualTo(StaffRole.owner);
    }

    @Test
    void studioRoleGets403Forbidden() {
        StaffPrincipal karigar = new StaffPrincipal(UUID.randomUUID(), "karigar@aakar.local", "Karigar Desk", StaffRole.studio);
        assertThat(karigar.isOwner()).isFalse();
        assertThatThrownBy(karigar::requireOwner).isInstanceOfSatisfying(ApiProblemException.class, e -> {
            assertThat(e.status().value()).isEqualTo(403);
            assertThat(e.code()).isEqualTo("forbidden");
            assertThat(e.getMessage()).contains("karigar@aakar.local").contains("studio");
        });
    }

    @Test
    void staffAuthenticationCarriesTheRoles() {
        StaffAuthentication owner = new StaffAuthentication(new StaffPrincipal(UUID.randomUUID(), "o@aakar.local", "O", StaffRole.owner));
        assertThat(owner.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_OWNER");
        StaffAuthentication studio = new StaffAuthentication(new StaffPrincipal(UUID.randomUUID(), "s@aakar.local", "S", StaffRole.studio));
        assertThat(studio.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STAFF");
        assertThat(studio.isAuthenticated()).isTrue();
        assertThat(studio.getName()).isEqualTo("s@aakar.local");
    }
}
