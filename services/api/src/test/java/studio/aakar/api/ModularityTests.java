package studio.aakar.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Fails the build on illegal cross-module access or cyclic module dependencies (PLAN §6). */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(AakarApiApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.forEach(System.out::println);
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
