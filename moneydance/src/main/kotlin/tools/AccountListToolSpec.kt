package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.moneydance.modules.features.moneylens.AccountFilter
import com.moneydance.modules.features.moneylens.AccountModel
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
private data class AccountListRequest(
    @JsonProperty("type")
    val type: List<String>? = null,
    val name: String? = null,
    val id: String? = null,
    val limit: Int? = null,
) {
    fun toFilter(): AccountFilter =
        AccountFilter(
            types = type?.toSet(),
            name = name,
            id = id,
            limit = limit,
        )
}

internal fun accountListSpec(
    toolName: String,
    description: String,
    requestSchemaFile: String,
    responseSchemaFile: String,
    responseKey: String,
    objectMapper: ObjectMapper,
    listFn: (AccountFilter) -> List<AccountModel>,
): SyncToolSpecification {
    val inputSchema = SchemaLoader.loadInputSchema(objectMapper, requestSchemaFile)
    val outputSchema = SchemaLoader.loadOutputSchema(objectMapper, responseSchemaFile)

    return SyncToolSpecification
        .builder()
        .tool(
            McpSchema.Tool
                .builder()
                .name(toolName)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .description(description)
                .build(),
        ).callHandler { _, request ->
            val req =
                objectMapper.convertValue(
                    request.arguments() ?: emptyMap<String, Any?>(),
                    AccountListRequest::class.java,
                )
            val results = listFn(req.toFilter())
            McpSchema.CallToolResult
                .builder()
                .structuredContent(mapOf(responseKey to results))
                .build()
        }.build()
}
