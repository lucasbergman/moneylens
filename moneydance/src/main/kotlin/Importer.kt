package com.moneydance.modules.features.moneylens

import com.google.protobuf.TextFormat
import com.infinitekind.moneydance.model.AbstractTxn
import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.infinitekind.moneydance.model.SplitTxn
import house.bergman.moneylens.proto.Split
import house.bergman.moneylens.proto.Transaction
import house.bergman.moneylens.proto.TransactionImport
import java.io.File

class Importer(
    private val book: AccountBook,
) {
    fun import(file: File): ImportResult {
        val content = file.readText()
        val builder = TransactionImport.newBuilder()
        TextFormat.merge(content, builder)
        val importData = builder.build()

        var imported = 0
        val errors = mutableListOf<String>()

        for (txn in importData.transactionsList) {
            try {
                createTransaction(txn)
                imported++
            } catch (e: Exception) {
                errors.add("Failed to import transaction '${txn.description}': ${e.message}")
            }
        }

        return ImportResult(imported, errors)
    }

    private fun createTransaction(txn: Transaction) {
        val account =
            findAccountByUuid(txn.accountId)
                ?: throw IllegalArgumentException("Account not found: ${txn.accountId}")

        val dateInt = parseDateInt(txn.date)
        val taxDateInt = if (txn.taxDate.isEmpty()) dateInt else parseDateInt(txn.taxDate)

        val parentTxn =
            ParentTxn.makeParentTxn(
                book,
                dateInt,
                taxDateInt,
                -1L,
                txn.checkNumber,
                account,
                txn.description,
                txn.memo,
                -1L,
                fromProtoStatus(txn.status),
            )

        if (txn.splitsList.isNotEmpty()) {
            for (split in txn.splitsList) {
                addSplit(parentTxn, split)
            }
        } else if (txn.categoryId.isNotEmpty()) {
            val category =
                findAccountByUuid(txn.categoryId)
                    ?: throw IllegalArgumentException("Category not found: ${txn.categoryId}")
            val splitTxn =
                SplitTxn.makeSplitTxn(
                    parentTxn,
                    txn.amount,
                    1.0,
                    category,
                    txn.memo,
                    -1L,
                    fromProtoStatus(txn.status),
                )
            parentTxn.addSplit(splitTxn)
        }

        book.transactionSet.addNewTxn(parentTxn)
    }

    private fun addSplit(
        parentTxn: ParentTxn,
        split: Split,
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
                split.memo,
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

    private fun fromProtoStatus(status: Transaction.Status): Byte =
        when (status) {
            Transaction.Status.STATUS_CLEARED -> AbstractTxn.STATUS_CLEARED
            Transaction.Status.STATUS_RECONCILING -> AbstractTxn.STATUS_RECONCILING
            Transaction.Status.STATUS_UNRECONCILED -> AbstractTxn.STATUS_UNRECONCILED
            else -> AbstractTxn.STATUS_UNRECONCILED
        }

    data class ImportResult(
        val importedCount: Int,
        val errors: List<String>,
    )
}
