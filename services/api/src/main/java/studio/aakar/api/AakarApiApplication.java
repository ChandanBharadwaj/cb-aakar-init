package studio.aakar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aakar storefront API. A Spring Modulith: every direct sub-package of {@code studio.aakar.api}
 * is an application module (catalog, templates, design, studio, pricing, media, identity, cart, order,
 * payment, shipping, notification, shared).
 */
@Modulith(systemName = "Aakar API", sharedModules = "shared")
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AakarApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AakarApiApplication.class, args);
    }
}
