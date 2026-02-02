package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.Account.AccountType
import com.infinitekind.moneydance.model.AccountBook

class MoneydanceAccountRepository(
    private val accountBookSupplier: () -> AccountBook?,
) : AccountRepository {
    private val accountTypeFromString: Map<String, AccountType> =
        mapOf(
            "bank" to AccountType.BANK,
            "credit_card" to AccountType.CREDIT_CARD,
            "investment" to AccountType.INVESTMENT,
            "security" to AccountType.SECURITY,
            "asset" to AccountType.ASSET,
            "liability" to AccountType.LIABILITY,
            "loan" to AccountType.LOAN,
        )

    private val allowedAccountTypes =
        setOf(
            AccountType.BANK,
            AccountType.CREDIT_CARD,
            AccountType.INVESTMENT,
            AccountType.SECURITY,
            AccountType.ASSET,
            AccountType.LIABILITY,
            AccountType.LOAN,
        )

    override fun listAccounts(types: Set<String>?): List<AccountModel> {
        val book = accountBookSupplier() ?: return emptyList()
        val typeFilter = types?.mapNotNull { accountTypeFromString[it] }?.toSet()
        val allAccounts = collectAccounts(book.rootAccount)

        return allAccounts
            .filter { account ->
                if (account.accountType !in allowedAccountTypes) return@filter false
                if (typeFilter != null && account.accountType !in typeFilter) return@filter false
                true
            }.map { account ->
                AccountModel(
                    id = account.uuid,
                    name = account.accountName,
                    fullName = account.fullAccountName,
                    type = accountTypeString(account.accountType),
                    currencyCode = account.currencyType.idString,
                    parentId =
                        if (account.parentAccount != null && account.parentAccount.accountType != AccountType.ROOT) {
                            account.parentAccount.uuid
                        } else {
                            null
                        },
                    inactive = account.accountIsInactive,
                )
            }
    }

    private fun collectAccounts(account: Account): List<Account> {
        val result = mutableListOf<Account>()

        if (account.accountType in allowedAccountTypes) {
            result.add(account)
        }
        for (i in 0 until account.subAccountCount) {
            result.addAll(collectAccounts(account.getSubAccount(i)))
        }
        return result
    }

    private fun accountTypeString(type: AccountType): String =
        when (type) {
            AccountType.BANK -> "bank"
            AccountType.CREDIT_CARD -> "credit_card"
            AccountType.INVESTMENT -> "investment"
            AccountType.SECURITY -> "security"
            AccountType.ASSET -> "asset"
            AccountType.LIABILITY -> "liability"
            AccountType.LOAN -> "loan"
            AccountType.EXPENSE -> "expense"
            AccountType.INCOME -> "income"
            AccountType.ROOT -> "root"
        }
}
