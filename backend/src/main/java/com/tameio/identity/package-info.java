/**
 * Businesses (the tenants) and the people who may act for them. Users sign in through Keycloak; this
 * module records which businesses each of them belongs to and in what role.
 *
 * <p>Other modules use {@link com.tameio.identity.BusinessService} and the types it returns; everything
 * else here is internal.
 */
@ApplicationModule(displayName = "Identity")
package com.tameio.identity;

import org.springframework.modulith.ApplicationModule;
