package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.toMoneydanceDateInt
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                val args = request.arguments() ?: emptyMap()
                val accountId =
                    args["account_id"] as? String
                        ?: error("account_id is required")
                val minDate = args["min_date"] as? String
                val maxDate = args["max_date"] as? String
                val description = args["description"] as? String
                val isUnconfirmed = args["is_unconfirmed"] as? Boolean

                val afterDateInt =
                    minDate?.toMoneydanceDateInt()
                        ?: LocalDate
                            .now()
                            .minusDays(30)
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                            .toInt()
                val beforeDateInt = maxDate?.toMoneydanceDateInt()

                val transactions =
                    accountRepository.getTransactions(
                        accountId = accountId,
                        afterDateInt = afterDateInt,
                        beforeDateInt = beforeDateInt,
                        description = description,
                        isUnconfirmed = isUnconfirmed,
                    )

                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(mapOf("transactions" to transactions))
                    .build()
            }.build()
    }
}
