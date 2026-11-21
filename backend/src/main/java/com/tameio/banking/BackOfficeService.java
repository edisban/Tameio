package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.InternalBalance;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.banking.BankingViews.TrialBalance;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The bank's view of its books and its customers' accounts. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BackOfficeService {

    private final BankAccountRepository accounts;
    private final PostingRepository postings;

    TrialBalance trialBalance() {
        List<InternalBalance> internal = accounts.findByKindOrderByCode(BankAccount.Kind.INTERNAL).stream()
            .map(a -> new InternalBalance(a.getCode(), a.getName(), a.getBalance()))
            .toList();
        BankAccountRepository.Deposits deposits = accounts.customerDeposits();
        BigDecimal total = internal.stream().map(InternalBalance::balance).reduce(deposits.total(), BigDecimal::add);
        return new TrialBalance(internal, deposits.total(), deposits.accounts(), total, total.signum() == 0, postings.countEntries());
    }

    Page<AccountView> customerAccounts(String search, Pageable pageable) {
        return accounts.searchCustomers(search == null ? "" : search.strip(), pageable).map(AccountView::of);
    }

    Page<TransactionView> statement(UUID accountId, Pageable pageable) {
        customer(accountId);
        return postings.statement(accountId, pageable).map(TransactionView::of);
    }

    /** Stops all money in and out, for example while a suspicion of fraud or money laundering is checked. */
    @Transactional
    AccountView freeze(CurrentUser staff, UUID accountId, String reason) {
        BankAccount account = customer(accountId);
        account.freeze(reason);
        log.warn("Account {} frozen by {}: {}", accountId, staff.id(), reason);
        return AccountView.of(account);
    }

    @Transactional
    AccountView unfreeze(CurrentUser staff, UUID accountId) {
        BankAccount account = customer(accountId);
        account.unfreeze();
        log.info("Account {} unfrozen by {}", accountId, staff.id());
        return AccountView.of(account);
    }

    private BankAccount customer(UUID accountId) {
        return accounts.findById(accountId).filter(BankAccount::isCustomer).orElseThrow(() -> new NotFoundException("Account not found."));
    }
}
