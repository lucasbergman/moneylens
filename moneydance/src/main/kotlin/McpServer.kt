package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import io.modelcontextprotocol.server.McpServer as McpServerFactory

class McpServer {
    private var jettyServer: Server? = null
    private var mcpSyncServer: McpSyncServer? = null

    fun start() {
        val jsonObjectMapper = ObjectMapper()

        val transportProvider =
            HttpServletStreamableServerTransportProvider
                .builder()
                .jsonMapper(JacksonMcpJsonMapper(jsonObjectMapper))
                .mcpEndpoint("/mcp")
                .build()

        val helloTool =
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
                McpSchema.CallToolResult(listOf(McpSchema.TextContent("Hello from Money Lens!")), false)
            }

        mcpSyncServer =
            McpServerFactory
                .sync(transportProvider)
                .serverInfo("moneylens", "0.1.0")
                .capabilities(
                    McpSchema.ServerCapabilities
                        .builder()
                        .tools(true)
                        .build(),
                )
                // Provide both directly to avoid ServiceLoader, which fails
                // under Moneydance's parent-first extension classloader.
                .jsonMapper(JacksonMcpJsonMapper(jsonObjectMapper))
                .jsonSchemaValidator(DefaultJsonSchemaValidator(jsonObjectMapper))
                .tools(helloTool)
                .build()

        jettyServer =
            Server().apply {
                addConnector(
                    ServerConnector(this).apply {
                        host = "127.0.0.1"
                        port = 51234
                    },
                )
                handler =
                    ServletContextHandler().apply {
                        addServlet(ServletHolder(transportProvider), "/*")
                    }
                start()
            }

        System.err.println("MCP server started on http://127.0.0.1:51234/mcp")
    }

    fun stop() {
        mcpSyncServer?.close()
        jettyServer?.stop()
        mcpSyncServer = null
        jettyServer = null
    }
}
