package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.Account.AccountType
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn

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
            "expense" to AccountType.EXPENSE,
            "income" to AccountType.INCOME,
        )

    private val accountTypes =
        setOf(
            AccountType.BANK,
            AccountType.CREDIT_CARD,
            AccountType.INVESTMENT,
            AccountType.SECURITY,
            AccountType.ASSET,
            AccountType.LIABILITY,
            AccountType.LOAN,
        )

    private val categoryTypes =
        setOf(
            AccountType.EXPENSE,
            AccountType.INCOME,
        )

    override fun listAccounts(filter: AccountFilter): List<AccountModel> = listByFilter(filter, accountTypes)

    override fun listCategories(filter: AccountFilter): List<AccountModel> = listByFilter(filter, categoryTypes)

    private fun listByFilter(
        filter: AccountFilter,
        allowed: Set<AccountType>,
    ): List<AccountModel> {
        val book = accountBookSupplier() ?: return emptyList()
        val typeFilter = filter.types?.mapNotNull { accountTypeFromString[it] }?.toSet()
        val allAccounts = collectAccounts(book.rootAccount, allowed)

        return allAccounts
            .asSequence()
            .filter { account ->
                if (typeFilter != null && account.accountType !in typeFilter) return@filter false
                if (filter.id != null && account.uuid != filter.id) return@filter false
                if (filter.name != null && !account.accountName.contains(filter.name, ignoreCase = true)) {
                    return@filter false
                }
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
            }.let { seq ->
                if (filter.limit != null) seq.take(filter.limit) else seq
            }.toList()
    }

    private fun collectAccounts(
        account: Account,
        allowed: Set<AccountType>,
    ): List<Account> {
        val result = mutableListOf<Account>()

        if (account.accountType in allowed) {
            result.add(account)
        }
        for (i in 0 until account.subAccountCount) {
            result.addAll(collectAccounts(account.getSubAccount(i), allowed))
        }
        return result
    }

    override fun getTransactions(
        accountId: String,
        afterDateInt: Int,
        beforeDateInt: Int?,
        description: String?,
    ): List<TransactionModel> {
        val book = accountBookSupplier() ?: return emptyList()
        val account = findAccountByUuid(book.rootAccount, accountId) ?: return emptyList()

        return book.transactionSet
            .getTransactionsForAccount(account)
            .filterIsInstance<ParentTxn>()
            .filter { it.dateInt >= afterDateInt && (beforeDateInt == null || it.dateInt <= beforeDateInt) }
            .let { txns ->
                if (description != null) {
                    txns.filter { it.description.contains(description, ignoreCase = true) }
                } else {
                    txns
                }
            }.map(::toTransactionModel)
    }

    override fun createTransactions(transactions: List<TransactionModel>) {
        val book = accountBookSupplier() ?: return

        for (txnModel in transactions) {
            val account = findAccountByUuid(book.rootAccount, txnModel.accountId) ?: continue
            val dateInt = parseDate(txnModel.date)
            val taxDateInt = txnModel.taxDate?.let { parseDate(it) } ?: dateInt

            val pTxn =
                ParentTxn.makeParentTxn(
                    book,
                    dateInt,
                    taxDateInt,
                    -1L,
                    txnModel.checkNumber ?: "",
                    account,
                    txnModel.description,
                    txnModel.memo ?: "",
                    -1L,
                    txnModel.status?.let { parseStatus(it) } ?: AbstractTxn.STATUS_UNRECONCILED,
                )

            if (!txnModel.splits.isNullOrEmpty()) {
                for (splitModel in txnModel.splits) {
                    val category =
                        findAccountByUuid(book.rootAccount, splitModel.categoryId) ?: continue
                    val sTxn =
                        SplitTxn.makeSplitTxn(
                            pTxn,
                            splitModel.amount,
                            1.0,
                            category,
                            splitModel.memo ?: "",
                            -1L,
                            AbstractTxn.STATUS_UNRECONCILED,
                        )
                    pTxn.addSplit(sTxn)
                }
            } else if (txnModel.categoryId != null) {
                val category =
                    findAccountByUuid(book.rootAccount, txnModel.categoryId) ?: continue
                val sTxn =
                    SplitTxn.makeSplitTxn(
                        pTxn,
                        txnModel.amount,
                        1.0,
                        category,
                        txnModel.memo ?: "",
                        -1L,
                        txnModel.status?.let { parseStatus(it) } ?: AbstractTxn.STATUS_UNRECONCILED,
                    )
                pTxn.addSplit(sTxn)
            }

            book.transactionSet.addNewTxn(pTxn)
        }
    }

    private fun parseDate(date: String): Int = date.replace("-", "").toInt()

    private fun parseStatus(status: String): Byte =
        when (status.lowercase()) {
            "cleared" -> AbstractTxn.STATUS_CLEARED
            "reconciling" -> AbstractTxn.STATUS_RECONCILING
            "unreconciled" -> AbstractTxn.STATUS_UNRECONCILED
            else -> AbstractTxn.STATUS_UNRECONCILED
        }

    private fun findAccountByUuid(
        account: Account,
        uuid: String,
    ): Account? {
        if (account.uuid == uuid) return account
        for (i in 0 until account.subAccountCount) {
            val found = findAccountByUuid(account.getSubAccount(i), uuid)
            if (found != null) return found
        }
        return null
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

    private fun formatDate(dateInt: Int): String {
        val s = dateInt.toString()
        if (s.length != 8) return s
        return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
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
