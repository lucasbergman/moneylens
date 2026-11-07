package com.moneydance.modules.features.moneylens

import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.Account.AccountType
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.CurrencySnapshot
import com.infinitekind.moneydance.model.CurrencyType
import com.infinitekind.moneydance.model.InvestFields
import com.infinitekind.moneydance.model.InvestTxnType
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn
import com.moneydance.apps.md.controller.Util

class MoneydanceAccountRepository(
    private val accountBookSupplier: () -> AccountBook?,
) : AccountRepository {
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
        val typeFilter = filter.types?.map { it.toAccountType() }?.toSet()
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
                    type = account.accountType.toAccountKind(),
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
        isUnconfirmed: Boolean?,
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
            }.let { txns ->
                if (isUnconfirmed != null) {
                    txns.filter { txn ->
                        val unconfirmed = txn.isNew || (0 until txn.splitCount).any { txn.getSplit(it).isNew }
                        unconfirmed == isUnconfirmed
                    }
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
                    txnModel.status?.toByteStatus() ?: AbstractTxn.STATUS_UNRECONCILED,
                )

            if (txnModel.investAction != null && account.accountType == AccountType.INVESTMENT) {
                val investFields = InvestFields()
                // setFieldStatus configures the InvestFields flags (hasSecurity, hasXfrAcct, etc)
                // for the given action, but also populates defaults (like "Bills") that we
                // want to override with our own candidates.
                investFields.setFieldStatus(txnModel.investAction, pTxn)
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
                        investFields.hasSecurity && investFields.security == null && acct.accountType == AccountType.SECURITY -> {
                            investFields.security = acct
                        }

                        investFields.hasXfrAcct && investFields.xfrAcct == null -> {
                            investFields.xfrAcct = acct
                        }

                        investFields.hasCategory && investFields.category == null -> {
                            investFields.category = acct
                        }
                    }
                }

                // Fallback for BANK transactions to ensure the transfer account is set.
                if (txnModel.investAction == InvestTxnType.BANK && investFields.xfrAcct == null) {
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
                                -splitModel.amount,
                                -splitModel.amount,
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
                            -txnModel.amount,
                            -txnModel.amount,
                            1.0,
                            category,
                            txnModel.memo ?: "",
                            -1L,
                            txnModel.status?.toByteStatus() ?: AbstractTxn.STATUS_UNRECONCILED,
                        )
                    pTxn.addSplit(sTxn)
                }
            }

            book.transactionSet.addNewTxn(pTxn)
        }
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
        var investAction: InvestTxnType? = null
        var amount = txn.value

        if (txn.account.accountType == AccountType.INVESTMENT) {
            val investFields = InvestFields()
            investFields.setFieldStatus(txn)
            if (investFields.txnType != null) {
                shares = investFields.shares
                price = investFields.price
                investAction = investFields.txnType
                amount = investFields.amount
            }
        }

        val isUnconfirmed = txn.isNew || (0 until txn.splitCount).any { txn.getSplit(it).isNew }
        val downloaded = txn.wasDownloaded() || (0 until txn.splitCount).any { txn.getSplit(it).wasDownloaded() }
        val fiTxnId =
            txn.getFiTxnId(0)?.ifBlank { null }
                ?: txn.getFiTxnId(1)?.ifBlank { null }
                ?: (0 until txn.splitCount).firstNotNullOfOrNull { i ->
                    val split = txn.getSplit(i)
                    split.getFiTxnId(0)?.ifBlank { null } ?: split.getFiTxnId(1)?.ifBlank { null }
                }

        return TransactionModel(
            date = txn.dateInt.toIsoDateString(),
            taxDate = taxDate,
            description = txn.description,
            accountId = txn.account.uuid,
            amount = amount,
            status = txn.status.toTransactionStatus(),
            memo = txn.memo?.ifEmpty { null },
            checkNumber = txn.checkNumber?.ifEmpty { null },
            tags = tags,
            attachments = null,
            categoryId = null,
            splits = splits.ifEmpty { null },
            shares = shares,
            price = price,
            investAction = investAction,
            isUnconfirmed = isUnconfirmed,
            downloaded = downloaded,
            fiTxnId = fiTxnId,
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

    override fun updateSecurityPrices(
        tickerOrId: String,
        prices: List<SecurityPriceModel>,
    ): UpdateSecurityPricesResult {
        val book = accountBookSupplier() ?: error("Account book is not available")
        val currencies = book.currencies

        val identifier = tickerOrId.trim()
        val security =
            currencies.getCurrencyByTickerSymbol(identifier)
                ?: currencies.getCurrencyByTickerSymbol(identifier.uppercase())
                ?: currencies.getCurrencyByUUID(identifier)
                ?: currencies.getCurrencyByName(identifier)
                ?: currencies.allCurrencies.firstOrNull {
                    it.currencyType == CurrencyType.Type.SECURITY &&
                        (
                            it.tickerSymbol.equals(identifier, ignoreCase = true) ||
                                it.name.equals(identifier, ignoreCase = true) ||
                                it.idString.equals(identifier, ignoreCase = true)
                        )
                }
                ?: error("Security not found for identifier: '$tickerOrId'")

        if (security.currencyType != CurrencyType.Type.SECURITY) {
            error("Currency '$tickerOrId' (${security.name}) is not a security")
        }

        val relativeCurrency = security.relativeCurrency ?: currencies.baseType
        val sortedPrices = prices.sortedBy { it.date }
        var latestSnapshot: CurrencySnapshot? = null

        for (priceModel in sortedPrices) {
            val dateInt = priceModel.date.toMoneydanceDateInt()
            val effectivePrice = priceModel.effectivePrice
            require(effectivePrice > 0.0) {
                "Price for date ${priceModel.date} must be greater than 0, got $effectivePrice"
            }

            val rate = 1.0 / Util.safeRate(effectivePrice)
            val snapshot = security.setSnapshotInt(dateInt, rate, relativeCurrency)

            priceModel.volume?.let { snapshot.dailyVolume = it }
            priceModel.high?.let {
                require(it > 0.0) { "High price must be positive on ${priceModel.date}" }
                snapshot.setDailyHigh(1.0 / Util.safeRate(it), relativeCurrency)
            }
            priceModel.low?.let {
                require(it > 0.0) { "Low price must be positive on ${priceModel.date}" }
                snapshot.setDailyLow(1.0 / Util.safeRate(it), relativeCurrency)
            }

            snapshot.syncItem()

            if (latestSnapshot == null || dateInt >= latestSnapshot.dateInt) {
                latestSnapshot = snapshot
            }
        }

        latestSnapshot?.let { latest ->
            val dateMs = Util.convertIntDateToLong(latest.dateInt).time
            if (dateMs >= security.getLongParameter("price_date", 0L)) {
                security.setRate(latest.rate, relativeCurrency)
                security.setParameter("price_date", dateMs)
                security.syncItem()
            }
        }

        currencies.fireCurrencyTableModified()

        val currentPrice =
            latestSnapshot?.let { if (it.rate > 0.0) 1.0 / it.rate else null }
                ?: security.getRate(relativeCurrency).let { if (it > 0.0) 1.0 / it else null }

        return UpdateSecurityPricesResult(
            ticker = security.tickerSymbol ?: security.idString,
            securityId = security.uuid,
            securityName = security.name,
            updatedCount = sortedPrices.size,
            firstDate = sortedPrices.firstOrNull()?.date,
            lastDate = sortedPrices.lastOrNull()?.date,
            currentPrice = currentPrice,
        )
    }
}

private fun AccountKind.toAccountType(): AccountType =
    when (this) {
        AccountKind.BANK -> AccountType.BANK
        AccountKind.CREDIT_CARD -> AccountType.CREDIT_CARD
        AccountKind.INVESTMENT -> AccountType.INVESTMENT
        AccountKind.SECURITY -> AccountType.SECURITY
        AccountKind.ASSET -> AccountType.ASSET
        AccountKind.LIABILITY -> AccountType.LIABILITY
        AccountKind.LOAN -> AccountType.LOAN
        AccountKind.EXPENSE -> AccountType.EXPENSE
        AccountKind.INCOME -> AccountType.INCOME
        AccountKind.ROOT -> AccountType.ROOT
    }

private fun AccountType.toAccountKind(): AccountKind =
    when (this) {
        AccountType.BANK -> AccountKind.BANK
        AccountType.CREDIT_CARD -> AccountKind.CREDIT_CARD
        AccountType.INVESTMENT -> AccountKind.INVESTMENT
        AccountType.SECURITY -> AccountKind.SECURITY
        AccountType.ASSET -> AccountKind.ASSET
        AccountType.LIABILITY -> AccountKind.LIABILITY
        AccountType.LOAN -> AccountKind.LOAN
        AccountType.EXPENSE -> AccountKind.EXPENSE
        AccountType.INCOME -> AccountKind.INCOME
        AccountType.ROOT -> AccountKind.ROOT
    }

private fun TransactionStatus.toByteStatus(): Byte =
    when (this) {
        TransactionStatus.CLEARED -> AbstractTxn.STATUS_CLEARED
        TransactionStatus.RECONCILING -> AbstractTxn.STATUS_RECONCILING
        TransactionStatus.UNRECONCILED -> AbstractTxn.STATUS_UNRECONCILED
    }

private fun Byte.toTransactionStatus(): TransactionStatus? =
    when (this) {
        AbstractTxn.STATUS_CLEARED -> TransactionStatus.CLEARED
        AbstractTxn.STATUS_RECONCILING -> TransactionStatus.RECONCILING
        AbstractTxn.STATUS_UNRECONCILED -> TransactionStatus.UNRECONCILED
        else -> null
    }
