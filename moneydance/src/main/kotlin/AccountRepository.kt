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

data class SplitModel(
    val categoryId: String,
    val amount: Double,
    val memo: String?,
)

data class TransactionModel(
    val date: String,
    val description: String,
    val accountId: String,
    val amount: Double,
    val memo: String?,
    val checkNumber: String?,
    val categoryId: String?,
    val splits: List<SplitModel>?,
)

interface AccountRepository {
    fun listAccounts(types: Set<String>? = null): List<AccountModel>
}
