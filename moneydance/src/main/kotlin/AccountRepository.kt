package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountModel(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
    val currencyCode: String,
    val parentId: String?,
    val inactive: Boolean,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SplitModel(
    val categoryId: String,
    val amount: Long,
    val memo: String?,
    val tags: List<String>?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransactionModel(
    val date: String,
    val taxDate: String?,
    val description: String,
    val accountId: String,
    val amount: Long,
    val status: String?,
    val memo: String?,
    val checkNumber: String?,
    val tags: List<String>?,
    val attachments: List<String>?,
    val categoryId: String?,
    val splits: List<SplitModel>?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExportModel(
    val accounts: List<AccountModel>,
    val transactions: List<TransactionModel>,
)

interface AccountRepository {
    fun listAccounts(types: Set<String>? = null): List<AccountModel>

    fun getTransactions(
        accountId: String,
        afterDateInt: Int,
        description: String? = null,
    ): List<TransactionModel>

    fun createTransactions(transactions: List<TransactionModel>)
}
