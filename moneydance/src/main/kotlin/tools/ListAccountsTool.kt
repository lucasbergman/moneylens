package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import java.io.InputStream

class ListAccountsTool(
    private val accountRepository: AccountRepository,
    private val objectMapper: ObjectMapper,
) {
    private val inputSchema =
        loadSchemaResource("list-accounts-request.schema.json") {
            objectMapper
                .reader()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(it, McpSchema.JsonSchema::class.java)
        }

    private val outputSchema =
        loadSchemaResource("list-accounts-response.schema.json") {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

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

    private fun <T> loadSchemaResource(
        fileName: String,
        parser: (InputStream) -> T,
    ): T =
        javaClass
            .getResourceAsStream("/com/moneydance/modules/features/moneylens/schema/$fileName")
            ?.use(parser)
            ?: error("Could not find $fileName")
}
