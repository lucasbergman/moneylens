package com.moneydance.modules.features.moneylens.tools

import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.spec.McpSchema

object HelloTool {
    val specification =
        McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool(
                "hello",
                null,
                "Says hello from Money Lens",
                McpSchema.JsonSchema("object", emptyMap(), emptyList(), false, null, null),
                null,
                null,
                null,
            ),
            null,
        ) { _, _ ->
            McpSchema.CallToolResult
                .builder()
                .textContent(listOf("Hello from Money Lens!"))
                .build()
        }
}
