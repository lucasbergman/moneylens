package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GetTransactionsTool(
    private val accountRepository: AccountRepository,
    private val objectMapper: ObjectMapper,
) {
    private val inputSchema =
        loadSchemaResource("get-transactions-request.schema.json") {
            objectMapper
                .reader()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(it, McpSchema.JsonSchema::class.java)
        }

    private val outputSchema =
        loadSchemaResource("get-transactions-response.schema.json") {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

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
                val days = (args["days"] as? Number)?.toInt() ?: 30
                val description = args["description"] as? String

                val afterDateInt =
                    LocalDate
                        .now()
                        .minusDays(days.toLong())
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .toInt()

                val transactions =
                    accountRepository.getTransactions(accountId, afterDateInt, description)

                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(mapOf("transactions" to transactions))
                    .build()
            }.build()
    }

    private fun <T> loadSchemaResource(
        fileName: String,
        parser: (InputStream) -> T,
    ): T =
        javaClass
            .getResourceAsStream("/com/moneydance/modules/features/moneylens/schema/$fileName")
            ?.use(parser)
            ?: error("Could not find $fileName")
}
