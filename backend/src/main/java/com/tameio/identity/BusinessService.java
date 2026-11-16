package com.tameio.identity;

import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Registering businesses and answering "which businesses can this user act for, and as what?". */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    private final BusinessRepository businesses;
    private final MembershipRepository memberships;

    /** Registers a business; whoever registers it becomes its owner. */
    @Transactional
    public BusinessView register(CurrentUser user, RegisterBusinessRequest request) {
        // A friendly answer for the common case; the unique constraint still covers two requests at once
        if (businesses.existsByAfm(request.afm())) {
            throw new ConflictException("A business with this ΑΦΜ is already registered.");
        }
        Business business = businesses.save(Business.register(request.afm(), request.name()));
        Membership owner = memberships.save(Membership.of(business, user.id(), MembershipRole.OWNER));
        log.info("Business {} registered by user {}", business.getId(), user.id());
        return BusinessView.of(owner);
    }

    public List<BusinessView> businessesOf(CurrentUser user) {
        return memberships.findAllWithBusinessByUserId(user.id()).stream().map(BusinessView::of).toList();
    }

    /** The business, if this user belongs to it. Someone else's business is reported as not found. */
    public BusinessView businessOf(CurrentUser user, UUID businessId) {
        return memberships.findWithBusiness(user.id(), businessId)
            .map(BusinessView::of)
            .orElseThrow(() -> new NotFoundException("Business not found."));
    }
}
