package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import com.moneydance.modules.features.moneylens.SecurityPriceModel
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema

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
                val args = request.arguments() ?: emptyMap()
                val ticker = args["ticker"] as? String
                val securityId = args["security_id"] as? String

                val identifier =
                    ticker?.ifBlank { null }
                        ?: securityId?.ifBlank { null }
                        ?: throw IllegalArgumentException("Either 'ticker' or 'security_id' must be specified")

                val pricesArg =
                    args["prices"]
                        ?: throw IllegalArgumentException("prices argument is required")
                val prices = mapper.convertValue(pricesArg, object : TypeReference<List<SecurityPriceModel>>() {})

                if (prices.isEmpty()) {
                    throw IllegalArgumentException("No price entries provided to update")
                }

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
