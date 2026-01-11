package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn
import house.bergman.moneylens.proto.AccountInfo
import house.bergman.moneylens.proto.Split
import house.bergman.moneylens.proto.Transaction
import house.bergman.moneylens.proto.TransactionExport
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class Exporter(
    private val book: AccountBook,
) {
    fun export(interval: Period): TransactionExport {
        val root = book.rootAccount
        val accounts = allExportableAccounts(root)
        val accountInfos = accounts.map(::toAccountInfo)

        val cutoffDate = LocalDate.now().minus(interval)
        val cutoffInt = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()

        val transactions =
            accounts.flatMap { account ->
                book.transactionSet
                    .getTransactionsForAccount(account)
                    .filterIsInstance<ParentTxn>()
                    .filter { it.dateInt >= cutoffInt }
                    .map(::toTransactionProto)
            }

        return TransactionExport
            .newBuilder()
            .addAllAccounts(accountInfos)
            .addAllTransactions(transactions)
            .build()
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

    private fun toAccountInfo(account: Account): AccountInfo =
        AccountInfo
            .newBuilder()
            .setId(account.uuid)
            .setName(account.accountName)
            .setFullName(account.fullAccountName)
            .setType(toProtoAccountType(account.accountType))
            .setCurrencyCode(account.currencyType.idString)
            .setParentId(account.parentAccount?.uuid ?: "")
            .build()

    private fun toProtoAccountType(type: Account.AccountType): AccountInfo.AccountType =
        when (type) {
            Account.AccountType.ROOT -> AccountInfo.AccountType.ACCOUNT_TYPE_ROOT
            Account.AccountType.BANK -> AccountInfo.AccountType.ACCOUNT_TYPE_BANK
            Account.AccountType.CREDIT_CARD -> AccountInfo.AccountType.ACCOUNT_TYPE_CREDIT_CARD
            Account.AccountType.INVESTMENT -> AccountInfo.AccountType.ACCOUNT_TYPE_INVESTMENT
            Account.AccountType.SECURITY -> AccountInfo.AccountType.ACCOUNT_TYPE_SECURITY
            Account.AccountType.ASSET -> AccountInfo.AccountType.ACCOUNT_TYPE_ASSET
            Account.AccountType.LIABILITY -> AccountInfo.AccountType.ACCOUNT_TYPE_LIABILITY
            Account.AccountType.LOAN -> AccountInfo.AccountType.ACCOUNT_TYPE_LOAN
            Account.AccountType.EXPENSE -> AccountInfo.AccountType.ACCOUNT_TYPE_EXPENSE
            Account.AccountType.INCOME -> AccountInfo.AccountType.ACCOUNT_TYPE_INCOME
        }

    private fun toTransactionProto(txn: ParentTxn): Transaction {
        val result =
            Transaction
                .newBuilder()
                // TODO: Passing dates as ints and strings is not ideal
                .setDate(formatDate(txn.dateInt))
                .setDescription(txn.description)
                .setAccountId(txn.account.uuid)
                .setAmount(txn.value)
                .setStatus(toProtoStatus(txn.status))
                .setMemo(txn.memo ?: "")
                .setCheckNumber(txn.checkNumber ?: "")

        if (txn.taxDateInt != txn.dateInt) {
            result.setTaxDate(formatDate(txn.taxDateInt))
        }

        // Tags
        txn.keywords.forEach { result.addTags(it) }

        // Splits
        for (i in 0 until txn.splitCount) {
            val split = txn.getSplit(i)
            result.addSplits(toSplitProto(split))
        }

        return result.build()
    }

    private fun toSplitProto(split: SplitTxn): Split {
        val result =
            Split
                .newBuilder()
                .setCategoryId(split.account.uuid)
                .setAmount(split.value)
                .setMemo(split.description ?: "")
        split.keywords.forEach { result.addTags(it) }
        return result.build()
    }

    private fun toProtoStatus(status: Byte): Transaction.Status =
        when (status) {
            AbstractTxn.STATUS_CLEARED -> Transaction.Status.STATUS_CLEARED
            AbstractTxn.STATUS_RECONCILING -> Transaction.Status.STATUS_RECONCILING
            AbstractTxn.STATUS_UNRECONCILED -> Transaction.Status.STATUS_UNRECONCILED
            else -> Transaction.Status.STATUS_UNSPECIFIED
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
