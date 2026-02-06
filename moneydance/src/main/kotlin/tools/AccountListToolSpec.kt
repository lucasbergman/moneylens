package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountFilter
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
            val args = request.arguments()
            val filter =
                AccountFilter(
                    types =
                        (args?.get("type") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.toSet(),
                    name = args?.get("name") as? String,
                    id = args?.get("id") as? String,
                    limit = (args?.get("limit") as? Number)?.toInt(),
                )

            val results = listFn(filter)
            McpSchema.CallToolResult
                .builder()
                .structuredContent(mapOf(responseKey to results))
                .build()
        }.build()
}
