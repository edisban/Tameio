package com.tameio.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsByAfm(String afm);
}
