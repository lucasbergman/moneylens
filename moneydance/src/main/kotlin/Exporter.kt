package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class Exporter(
    private val book: AccountBook,
) {
    fun export(interval: Period): ExportModel {
        val root = book.rootAccount
        val accounts = allExportableAccounts(root)
        val accountModels = accounts.map(::toAccountModel)

        val cutoffDate = LocalDate.now().minus(interval)
        val cutoffInt = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()

        val transactions =
            accounts.flatMap { account ->
                book.transactionSet
                    .getTransactionsForAccount(account)
                    .filterIsInstance<ParentTxn>()
                    .filter { it.dateInt >= cutoffInt }
                    .map(::toTransactionModel)
            }

        return ExportModel(
            accounts = accountModels,
            transactions = transactions,
        )
    }

    private fun allExportableAccounts(account: Account): List<Account> {
        val accounts = mutableListOf<Account>()
        if (account.accountType == Account.AccountType.BANK) {
            accounts.add(account)
        }
        for (a in account.subAccounts) {
            accounts.addAll(allExportableAccounts(a))
        }
        return accounts
    }

    private fun toAccountModel(account: Account): AccountModel =
        AccountModel(
            id = account.uuid,
            name = account.accountName,
            fullName = account.fullAccountName,
            type = toAccountType(account.accountType),
            currencyCode = account.currencyType.idString,
            parentId = account.parentAccount?.uuid,
            inactive = account.accountIsInactive,
        )

    private fun toAccountType(type: Account.AccountType): String =
        when (type) {
            Account.AccountType.ROOT -> "root"
            Account.AccountType.BANK -> "bank"
            Account.AccountType.CREDIT_CARD -> "credit_card"
            Account.AccountType.INVESTMENT -> "investment"
            Account.AccountType.SECURITY -> "security"
            Account.AccountType.ASSET -> "asset"
            Account.AccountType.LIABILITY -> "liability"
            Account.AccountType.LOAN -> "loan"
            Account.AccountType.EXPENSE -> "expense"
            Account.AccountType.INCOME -> "income"
        }

    private fun toTransactionModel(txn: ParentTxn): TransactionModel {
        val tags = txn.keywords.toList().ifEmpty { null }
        val taxDate =
            if (txn.taxDateInt != txn.dateInt) formatDate(txn.taxDateInt) else null

        val splits =
            (0 until txn.splitCount).map { i -> toSplitModel(txn.getSplit(i)) }

        return TransactionModel(
            date = formatDate(txn.dateInt),
            taxDate = taxDate,
            description = txn.description,
            accountId = txn.account.uuid,
            amount = txn.value,
            status = toStatus(txn.status),
            memo = txn.memo?.ifEmpty { null },
            checkNumber = txn.checkNumber?.ifEmpty { null },
            tags = tags,
            attachments = null,
            categoryId = null,
            splits = splits.ifEmpty { null },
        )
    }

    private fun toSplitModel(split: SplitTxn): SplitModel {
        val tags = split.keywords.toList().ifEmpty { null }
        return SplitModel(
            categoryId = split.account.uuid,
            amount = split.value,
            memo = split.description?.ifEmpty { null },
            tags = tags,
        )
    }

    private fun toStatus(status: Byte): String? =
        when (status) {
            AbstractTxn.STATUS_CLEARED -> "cleared"
            AbstractTxn.STATUS_RECONCILING -> "reconciling"
            AbstractTxn.STATUS_UNRECONCILED -> "unreconciled"
            else -> null
        }

    /**
     * Converts an integer date (e.g., 20230101) to a formatted string (e.g., 2023-01-01).
     */
    private fun formatDate(dateInt: Int): String {
        val s = dateInt.toString()
        if (s.length != 8) return s // Fallback, not Y10K-compatible :(
        return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
    }
}
