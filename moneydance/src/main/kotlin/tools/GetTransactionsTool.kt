package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.LocalDateDeserializer
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import java.time.LocalDate

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
private data class GetTransactionsRequest(
    val accountId: String? = null,
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val minDate: LocalDate? = null,
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val maxDate: LocalDate? = null,
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

                val afterDate = req.minDate ?: LocalDate.now().minusDays(30)
                val beforeDate = req.maxDate

                val transactions =
                    accountRepository.getTransactions(
                        accountId = accountId,
                        afterDate = afterDate,
                        beforeDate = beforeDate,
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
