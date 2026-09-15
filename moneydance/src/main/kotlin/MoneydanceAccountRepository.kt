package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.Account.AccountType
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.InvestFields
import com.infinitekind.moneydance.model.InvestTxnType
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
                    decimalPlaces = account.currencyType.decimalPlaces,
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
        for (sub in account.subAccounts) {
            result.addAll(collectAccounts(sub, allowed))
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
        val accountMap = buildAccountMap(book.rootAccount)

        for (txnModel in transactions) {
            val account = accountMap[txnModel.accountId] ?: continue
            val dateInt = txnModel.date.toMoneydanceDateInt()
            val taxDateInt = txnModel.taxDate?.toMoneydanceDateInt() ?: dateInt

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

            if (txnModel.investAction != null && account.accountType == AccountType.INVESTMENT) {
                val investFields = InvestFields()
                val action =
                    try {
                        InvestTxnType.valueOf(txnModel.investAction.uppercase())
                    } catch (_: Exception) {
                        InvestTxnType.BANK
                    }
                // setFieldStatus configures the InvestFields flags (hasSecurity, hasXfrAcct, etc)
                // for the given action, but also populates defaults (like "Bills") that we
                // want to override with our own candidates.
                investFields.setFieldStatus(action, pTxn)
                investFields.security = null
                investFields.xfrAcct = null
                investFields.category = null
                investFields.feeAcct = null

                investFields.date = dateInt
                investFields.taxDate = taxDateInt
                investFields.shares = txnModel.shares ?: 0L
                // Moneydance stores price as the reciprocal: shares per currency unit.
                investFields.price = txnModel.price ?: 0.0
                investFields.amount = txnModel.amount
                investFields.memo = txnModel.memo ?: ""

                // Map candidate accounts (from splits and category_id) to the appropriate investment fields.
                val candidateAccounts =
                    (txnModel.splits?.mapNotNull { accountMap[it.categoryId] } ?: emptyList()) +
                        listOfNotNull(txnModel.categoryId?.let { accountMap[it] })

                for (acct in candidateAccounts) {
                    when {
                        investFields.hasSecurity && investFields.security == null && acct.accountType == AccountType.SECURITY ->
                            investFields.security = acct
                        investFields.hasXfrAcct && investFields.xfrAcct == null ->
                            investFields.xfrAcct = acct
                        investFields.hasCategory && investFields.category == null ->
                            investFields.category = acct
                    }
                }

                // Fallback for BANK transactions to ensure the transfer account is set.
                if (action == InvestTxnType.BANK && investFields.xfrAcct == null) {
                    investFields.xfrAcct = candidateAccounts.firstOrNull()
                }

                investFields.storeFields(pTxn)
            } else {
                if (!txnModel.splits.isNullOrEmpty()) {
                    for (splitModel in txnModel.splits) {
                        val category =
                            accountMap[splitModel.categoryId] ?: continue
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
                        accountMap[txnModel.categoryId] ?: continue
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
            }

            book.transactionSet.addNewTxn(pTxn)
        }
    }

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
        return account.subAccounts.firstNotNullOfOrNull { findAccountByUuid(it, uuid) }
    }

    private fun buildAccountMap(root: Account): Map<String, Account> {
        val map = mutableMapOf<String, Account>()

        fun traverse(account: Account) {
            map[account.uuid] = account
            for (sub in account.subAccounts) {
                traverse(sub)
            }
        }
        traverse(root)
        return map
    }

    private fun toTransactionModel(txn: ParentTxn): TransactionModel {
        val tags = txn.keywords.toList().ifEmpty { null }
        val taxDate =
            if (txn.taxDateInt != txn.dateInt) txn.taxDateInt.toIsoDateString() else null
        val splits =
            (0 until txn.splitCount).map { i -> toSplitModel(txn.getSplit(i)) }

        var shares: Long? = null
        var price: Double? = null
        var investAction: String? = null
        var amount = txn.value

        if (txn.account.accountType == AccountType.INVESTMENT) {
            val investFields = InvestFields()
            investFields.setFieldStatus(txn)
            if (investFields.txnType != null) {
                shares = investFields.shares
                price = investFields.price
                investAction = investFields.txnType.name
                amount = investFields.amount
            }
        }

        return TransactionModel(
            date = txn.dateInt.toIsoDateString(),
            taxDate = taxDate,
            description = txn.description,
            accountId = txn.account.uuid,
            amount = amount,
            status = toStatus(txn.status),
            memo = txn.memo?.ifEmpty { null },
            checkNumber = txn.checkNumber?.ifEmpty { null },
            tags = tags,
            attachments = null,
            categoryId = null,
            splits = splits.ifEmpty { null },
            shares = shares,
            price = price,
            investAction = investAction,
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
