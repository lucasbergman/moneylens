package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountModel
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

internal fun accountListSpec(
    toolName: String,
    description: String,
    requestSchemaFile: String,
    responseSchemaFile: String,
    responseKey: String,
    objectMapper: ObjectMapper,
    listFn: (Set<String>?) -> List<AccountModel>,
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
            val typeFilter =
                (request.arguments()?.get("type") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toSet()

            val results = listFn(typeFilter)
            McpSchema.CallToolResult
                .builder()
                .structuredContent(mapOf(responseKey to results))
                .build()
        }.build()
}
