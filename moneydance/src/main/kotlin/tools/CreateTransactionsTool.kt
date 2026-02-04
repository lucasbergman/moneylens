package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.TransactionModel
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

class CreateTransactionsTool(
    private val accountRepository: AccountRepository,
    private val mapper: ObjectMapper,
) {
    private val inputSchema = SchemaLoader.loadInputSchema(mapper, "create-transactions-request.schema.json")
    private val outputSchema = SchemaLoader.loadOutputSchema(mapper, "create-transactions-response.schema.json")

    val spec: SyncToolSpecification by lazy {
        SyncToolSpecification
            .builder()
            .tool(
                McpSchema.Tool
                    .builder()
                    .name("create_transactions")
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
                    .description(
                        "Creates new transactions in Moneydance. Accepts a list of transactions.",
                    ).build(),
            ).callHandler { _, request ->
                val txnsArg =
                    request.arguments()?.get("transactions")
                        ?: throw IllegalArgumentException("transactions argument is required")
                val txns = mapper.convertValue(txnsArg, object : TypeReference<List<TransactionModel>>() {})

                accountRepository.createTransactions(txns)

                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(mapOf("created_count" to txns.size))
                    .build()
            }.build()
    }
}
