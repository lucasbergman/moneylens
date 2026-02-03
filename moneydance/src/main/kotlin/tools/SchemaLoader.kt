package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.McpSchema
import java.io.InputStream

object SchemaLoader {
    private const val SCHEMA_PATH = "/com/moneydance/modules/features/moneylens/schema"

    fun loadInputSchema(
        objectMapper: ObjectMapper,
        fileName: String,
    ): McpSchema.JsonSchema =
        loadResource("$SCHEMA_PATH/$fileName") {
            objectMapper
                .reader()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(it, McpSchema.JsonSchema::class.java)
        }

    fun loadOutputSchema(
        objectMapper: ObjectMapper,
        fileName: String,
    ): Map<String, Any> =
        loadResource("$SCHEMA_PATH/$fileName") {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

    private fun <T> loadResource(
        path: String,
        parser: (InputStream) -> T,
    ): T =
        SchemaLoader::class.java.getResourceAsStream(path)?.use(parser)
            ?: error("Could not find resource: $path")
}
