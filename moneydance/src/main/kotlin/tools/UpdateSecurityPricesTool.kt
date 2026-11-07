package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.SecurityPriceModel
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
private data class UpdateSecurityPricesRequest(
    val ticker: String? = null,
    val securityId: String? = null,
    val prices: List<SecurityPriceModel>? = null,
)

class UpdateSecurityPricesTool(
    private val accountRepository: AccountRepository,
    private val mapper: ObjectMapper,
) {
    private val inputSchema = SchemaLoader.loadInputSchema(mapper, "update-security-prices-request.schema.json")
    private val outputSchema = SchemaLoader.loadOutputSchema(mapper, "update-security-prices-response.schema.json")

    val spec: SyncToolSpecification by lazy {
        SyncToolSpecification
            .builder()
            .tool(
                McpSchema.Tool
                    .builder()
                    .name("update_security_prices")
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
                    .description("Updates historical price snapshots for a security in Moneydance.")
                    .build(),
            ).callHandler { _, request ->
                val req =
                    mapper.convertValue(
                        request.arguments() ?: emptyMap<String, Any?>(),
                        UpdateSecurityPricesRequest::class.java,
                    )
                val identifier =
                    req.ticker?.ifBlank { null }
                        ?: req.securityId?.ifBlank { null }
                        ?: throw IllegalArgumentException("Either 'ticker' or 'security_id' must be specified")

                val prices =
                    req.prices?.ifEmpty { null }
                        ?: throw IllegalArgumentException("prices argument is required")

                val result = accountRepository.updateSecurityPrices(identifier, prices)
                val responseMap =
                    mapper
                        .convertValue(result, object : TypeReference<Map<String, Any?>>() {})
                        .filterValues { it != null }

                McpSchema.CallToolResult
                    .builder()
                    .structuredContent(responseMap)
                    .build()
            }.build()
    }
}
