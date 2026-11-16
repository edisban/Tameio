/**
 * Cross-cutting building blocks every module may use: security, error responses, auditing,
 * validation of Greek identifiers and API documentation. It holds no business logic.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.tameio.platform;

import org.springframework.modulith.ApplicationModule;
