package studio.aakar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aakar storefront API. A Spring Modulith: every direct sub-package of {@code studio.aakar.api}
 * is an application module (catalog, templates, design, studio, pricing, media, identity, cart, order,
 * payment, shipping, notification, shared). Customers authenticate with our own JWTs, so Spring Security's
 * generated in-memory user is excluded.
 */
@Modulith(systemName = "Aakar API", sharedModules = "shared")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class AakarApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AakarApiApplication.class, args);
    }
}
