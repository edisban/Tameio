/**
 * A simulated SEPA clearing house and the other banks behind it (ADR 0010). In reality this is a system
 * run by someone else, so the module talks to the bank only through RabbitMQ, with JSON messages it
 * defines itself, and shares no Java type with the banking module. It could run as its own service
 * unchanged.
 *
 * <p>Deterministic rules let demos and tests produce every outcome: payments to an account number ending
 * in 0000 are rejected as closed (AC04), ending in 9999 as unknown (AC01); everything else settles.
 */
@ApplicationModule(displayName = "Clearing house (simulated)")
package com.tameio.clearing;

import org.springframework.modulith.ApplicationModule;
