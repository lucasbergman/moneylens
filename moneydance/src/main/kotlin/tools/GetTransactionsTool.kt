package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.toMoneydanceDateInt
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
private data class GetTransactionsRequest(
    val accountId: String? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
    val description: String? = null,
    val isUnconfirmed: Boolean? = null,
)

class GetTransactionsTool(
    private val accountRepository: AccountRepository,
    objectMapper: ObjectMapper,
) {
    private val inputSchema = SchemaLoader.loadInputSchema(objectMapper, "get-transactions-request.schema.json")
    private val outputSchema = SchemaLoader.loadOutputSchema(objectMapper, "get-transactions-response.schema.json")

    val spec: SyncToolSpecification by lazy {
        SyncToolSpecification
            .builder()
            .tool(
                McpSchema.Tool
                    .builder()
                    .name("get_transactions")
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
                    .description(
                        "Returns transactions for a specific account, with optional " +
                            "date range and description filtering.",
                    ).build(),
            ).callHandler { _, request ->
                val req =
                    objectMapper.convertValue(
                        request.arguments() ?: emptyMap<String, Any?>(),
                        GetTransactionsRequest::class.java,
                    )
                val accountId =
                    req.accountId?.ifBlank { null }
                        ?: error("account_id is required")

                val afterDateInt =
                    req.minDate?.toMoneydanceDateInt()
                        ?: LocalDate
                            .now()
                            .minusDays(30)
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                            .toInt()
                val beforeDateInt = req.maxDate?.toMoneydanceDateInt()

                val transactions =
                    accountRepository.getTransactions(
                        accountId = accountId,
                        afterDateInt = afterDateInt,
                        beforeDateInt = beforeDateInt,
                        description = req.description,
                        isUnconfirmed = req.isUnconfirmed,
                    )

                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(mapOf("transactions" to transactions))
                    .build()
            }.build()
    }
}
