package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.tools.GetTransactionsTool
import com.moneydance.modules.features.moneylens.tools.HelloTool
import com.moneydance.modules.features.moneylens.tools.ListAccountsTool
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import io.modelcontextprotocol.server.McpServer as McpServerFactory

class McpServer(
    private val accountRepository: AccountRepository,
    private val port: Int = 51234,
) {
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

        val listAccountsTool = ListAccountsTool(accountRepository, jsonObjectMapper).spec
        val getTransactionsTool = GetTransactionsTool(accountRepository, jsonObjectMapper).spec

        mcpSyncServer =
            McpServerFactory
                .sync(transportProvider)
                .serverInfo("moneylens", "0.1.0")
                .capabilities(
                    McpSchema.ServerCapabilities
                        .builder()
                        .tools(true)
                        .build(),
                ).jsonMapper(JacksonMcpJsonMapper(jsonObjectMapper))
                .jsonSchemaValidator(DefaultJsonSchemaValidator(jsonObjectMapper))
                .tools(HelloTool.specification, listAccountsTool, getTransactionsTool)
                .build()

        jettyServer =
            Server().apply {
                addConnector(
                    ServerConnector(this).apply {
                        host = "127.0.0.1"
                        port = this@McpServer.port
                    },
                )
                handler =
                    ServletContextHandler().apply {
                        addServlet(ServletHolder(transportProvider), "/*")
                    }
                start()
            }

        System.err.println("MCP server started on http://127.0.0.1:$port/mcp")
    }

    fun stop() {
        mcpSyncServer?.close()
        jettyServer?.stop()
        mcpSyncServer = null
        jettyServer = null
    }
}
