package com.tameio.banking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param demoDeposits lets a business put demo money into its account, standing in for a payment from
 *                     a customer; on for local stacks, off in production
 * @param maxAccounts  how many current accounts one business may open
 */
@ConfigurationProperties("tameio.banking")
record BankingProperties(boolean demoDeposits, int maxAccounts) {

    BankingProperties {
        maxAccounts = maxAccounts <= 0 ? 5 : maxAccounts;
    }
}
