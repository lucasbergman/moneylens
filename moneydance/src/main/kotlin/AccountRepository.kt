package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.infinitekind.moneydance.model.InvestTxnType

enum class AccountKind {
    @JsonProperty("bank")
    BANK,

    @JsonProperty("credit_card")
    CREDIT_CARD,

    @JsonProperty("investment")
    INVESTMENT,

    @JsonProperty("security")
    SECURITY,

    @JsonProperty("asset")
    ASSET,

    @JsonProperty("liability")
    LIABILITY,

    @JsonProperty("loan")
    LOAN,

    @JsonProperty("expense")
    EXPENSE,

    @JsonProperty("income")
    INCOME,

    @JsonProperty("root")
    ROOT,
}

enum class TransactionStatus {
    @JsonProperty("cleared")
    CLEARED,

    @JsonProperty("reconciling")
    RECONCILING,

    @JsonProperty("unreconciled")
    UNRECONCILED,
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountModel(
    val id: String,
    val name: String,
    val fullName: String,
    val type: AccountKind,
    val currencyCode: String,
    val decimalPlaces: Int,
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
    val status: TransactionStatus?,
    val memo: String?,
    val checkNumber: String?,
    val tags: List<String>?,
    val attachments: List<String>?,
    val categoryId: String?,
    val splits: List<SplitModel>?,
    val shares: Long? = null,
    val price: Double? = null,
    val investAction: InvestTxnType? = null,
    @JsonProperty("is_unconfirmed")
    val isUnconfirmed: Boolean? = null,
    val downloaded: Boolean? = null,
    val fiTxnId: String? = null,
)

data class AccountFilter(
    val types: Set<AccountKind>? = null,
    val name: String? = null,
    val id: String? = null,
    val limit: Int? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SecurityPriceModel(
    val date: String,
    val price: Double? = null,
    val close: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long? = null,
) {
    val effectivePrice: Double
        get() = price ?: close ?: throw IllegalArgumentException("Price entry on date '$date' is missing 'price'")
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateSecurityPricesResult(
    val ticker: String,
    val securityId: String,
    val securityName: String,
    val updatedCount: Int,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val currentPrice: Double? = null,
)

interface AccountRepository {
    fun listAccounts(filter: AccountFilter = AccountFilter()): List<AccountModel>

    fun listCategories(filter: AccountFilter = AccountFilter()): List<AccountModel>

    fun getTransactions(
        accountId: String,
        afterDateInt: Int,
        beforeDateInt: Int? = null,
        description: String? = null,
        isUnconfirmed: Boolean? = null,
    ): List<TransactionModel>

    fun createTransactions(transactions: List<TransactionModel>)

    fun updateSecurityPrices(
        tickerOrId: String,
        prices: List<SecurityPriceModel>,
    ): UpdateSecurityPricesResult
}

internal fun String.toMoneydanceDateInt(): Int = replace("-", "").toInt()

internal fun Int.toIsoDateString(): String {
    val s = toString()
    if (s.length != 8) return s
    return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
}
