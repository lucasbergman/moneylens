package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

class ListAccountsTool(
    private val accountRepository: AccountRepository,
    objectMapper: ObjectMapper,
) {
    private val inputSchema = SchemaLoader.loadInputSchema(objectMapper, "list-accounts-request.schema.json")
    private val outputSchema = SchemaLoader.loadOutputSchema(objectMapper, "list-accounts-response.schema.json")

    val spec: SyncToolSpecification by lazy {
        SyncToolSpecification
            .builder()
            .tool(
                McpSchema.Tool
                    .builder()
                    .name("list_accounts")
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
                    .description(
                        "Lists all accounts (bank, credit card, etc.) in the open " +
                            "Moneydance file.",
                    ).build(),
            ).callHandler { _, request ->
                val typeFilter =
                    (request.arguments()?.get("type") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?.toSet()

                val accounts = accountRepository.listAccounts(typeFilter)
                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(mapOf("accounts" to accounts))
                    .build()
            }.build()
    }
}
