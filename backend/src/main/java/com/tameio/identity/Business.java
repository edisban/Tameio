package com.tameio.identity;

import com.tameio.platform.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A business using Tameio, identified by its ΑΦΜ. Every piece of business data belongs to one. */
@Entity
@Table(name = "business")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only; code creates businesses through register()
class Business extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 9)
    private String afm;

    @Column(nullable = false, length = 200)
    private String name;

    static Business register(String afm, String name) {
        var business = new Business();
        business.afm = afm;
        business.name = name;
        return business;
    }
}
