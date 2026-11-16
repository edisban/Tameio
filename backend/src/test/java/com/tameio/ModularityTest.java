package com.tameio;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build when a module reaches into another module's internals or modules depend on each
 * other in a cycle, so the boundaries of the modular monolith (ADR 0001) cannot erode unnoticed.
 */
class ModularityTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(TameioCoreApplication.class).verify();
    }
}
