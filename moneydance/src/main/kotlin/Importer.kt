package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.databind.ObjectMapper
import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.InvestFields
import com.infinitekind.moneydance.model.InvestTxnType
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn
import java.io.File

class Importer(
    private val book: AccountBook,
) {
    fun import(file: File): ImportResult {
        val mapper = ObjectMapper()
        val importData = mapper.readValue(file, ExportModel::class.java)

        var imported = 0
        val errors = mutableListOf<String>()

        for (txn in importData.transactions) {
            try {
                createTransaction(txn)
                imported++
            } catch (e: Exception) {
                errors.add("Failed to import transaction '${txn.description}': ${e.message}")
            }
        }

        return ImportResult(imported, errors)
    }

    private fun createTransaction(txn: TransactionModel) {
        val account =
            findAccountByUuid(txn.accountId)
                ?: throw IllegalArgumentException("Account not found: ${txn.accountId}")

        val dateInt = parseDateInt(txn.date)
        val taxDateInt = if (txn.taxDate == null) dateInt else parseDateInt(txn.taxDate)

        val parentTxn =
            ParentTxn.makeParentTxn(
                book,
                dateInt,
                taxDateInt,
                -1L,
                txn.checkNumber ?: "",
                account,
                txn.description,
                txn.memo ?: "",
                -1L,
                fromStatus(txn.status),
            )

        if (txn.investAction != null && account.accountType == Account.AccountType.INVESTMENT) {
            val investFields = InvestFields()
            val action =
                try {
                    InvestTxnType.valueOf(txn.investAction.uppercase())
                } catch (e: Exception) {
                    InvestTxnType.BANK
                }
            // setFieldStatus configures the InvestFields flags (hasSecurity, hasXfrAcct, etc)
            // for the given action, but also populates defaults (like "Bills") that we
            // want to override with our own candidates.
            investFields.setFieldStatus(action, parentTxn)
            investFields.security = null
            investFields.xfrAcct = null
            investFields.category = null
            investFields.feeAcct = null

            investFields.date = dateInt
            investFields.taxDate = taxDateInt
            investFields.shares = txn.shares ?: 0L
            // Moneydance stores price as the reciprocal: shares per currency unit.
            investFields.price = txn.price ?: 0.0
            investFields.amount = txn.amount
            investFields.memo = txn.memo ?: ""

            // Map candidate accounts (from splits and category_id) to the appropriate investment fields.
            val candidateAccounts =
                (txn.splits?.mapNotNull { findAccountByUuid(it.categoryId) } ?: emptyList()) +
                    listOfNotNull(txn.categoryId?.let { findAccountByUuid(it) })

            for (acct in candidateAccounts) {
                when {
                    investFields.hasSecurity && investFields.security == null && acct.accountType == Account.AccountType.SECURITY ->
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

            investFields.storeFields(parentTxn)
        } else {
            if (!txn.splits.isNullOrEmpty()) {
                for (split in txn.splits) {
                    addSplit(parentTxn, split)
                }
            } else if (txn.categoryId != null) {
                val category =
                    findAccountByUuid(txn.categoryId)
                        ?: throw IllegalArgumentException("Category not found: ${txn.categoryId}")
                val splitTxn =
                    SplitTxn.makeSplitTxn(
                        parentTxn,
                        txn.amount,
                        1.0,
                        category,
                        txn.memo ?: "",
                        -1L,
                        fromStatus(txn.status),
                    )
                parentTxn.addSplit(splitTxn)
            }
        }

        book.transactionSet.addNewTxn(parentTxn)
    }

    private fun addSplit(
        parentTxn: ParentTxn,
        split: SplitModel,
    ) {
        val category =
            findAccountByUuid(split.categoryId)
                ?: throw IllegalArgumentException("Category not found: ${split.categoryId}")

        val splitTxn =
            SplitTxn.makeSplitTxn(
                parentTxn,
                split.amount,
                1.0,
                category,
                split.memo ?: "",
                -1L,
                AbstractTxn.STATUS_UNRECONCILED,
            )
        parentTxn.addSplit(splitTxn)
    }

    private fun findAccountByUuid(uuid: String): Account? = findAccountByUuid(book.rootAccount, uuid)

    private fun findAccountByUuid(
        account: Account,
        uuid: String,
    ): Account? {
        if (account.uuid == uuid) return account
        for (sub in account.subAccounts) {
            val found = findAccountByUuid(sub, uuid)
            if (found != null) return found
        }
        return null
    }

    private fun parseDateInt(dateStr: String): Int {
        if (dateStr.isEmpty()) return 0
        // Convert YYYY-MM-DD to YYYYMMDD integer
        return dateStr.replace("-", "").toInt()
    }

    private fun fromStatus(status: String?): Byte =
        when (status) {
            "cleared" -> AbstractTxn.STATUS_CLEARED
            "reconciling" -> AbstractTxn.STATUS_RECONCILING
            "unreconciled" -> AbstractTxn.STATUS_UNRECONCILED
            else -> AbstractTxn.STATUS_UNRECONCILED
        }

    data class ImportResult(
        val importedCount: Int,
        val errors: List<String>,
    )
}
