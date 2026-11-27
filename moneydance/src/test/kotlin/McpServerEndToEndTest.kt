package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.infinitekind.moneydance.model.InvestTxnType
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

fun main() {
    testEndToEndMcpServer()
    testNullBookHandling()
}

private fun testNullBookHandling() {
    val repo = MoneydanceAccountRepository(accountBookSupplier = { null })
    check(repo.listAccounts().isEmpty()) { "Expected empty accounts when no book is open" }
    check(repo.listCategories().isEmpty()) { "Expected empty categories when no book is open" }
    check(
        repo
            .getTransactions(
                accountId = "acct-1",
                afterDate = LocalDate.of(2026, 1, 1),
            ).isEmpty(),
    ) { "Expected empty transactions when no book is open" }

    // Should safely no-op when no book is open
    repo.createTransactions(
        listOf(
            TransactionModel(
                date = LocalDate.of(2026, 11, 22),
                description = "No-op",
                accountId = "acct-1",
                amount = -100L,
                status = TransactionStatus.UNRECONCILED,
                memo = null,
                checkNumber = null,
                tags = null,
                attachments = null,
                categoryId = "cat-1",
                splits = null,
            ),
        ),
    )

    val updateError =
        runCatching {
            repo.updateSecurityPrices(
                tickerOrId = "VTI",
                prices =
                    listOf(
                        SecurityPriceModel(date = LocalDate.of(2026, 11, 22), price = 300.0),
                    ),
            )
        }.exceptionOrNull()
    check(updateError is IllegalStateException) {
        "Expected IllegalStateException with no book open, got $updateError"
    }
}

private fun testEndToEndMcpServer() {
    val port = ServerSocket(0).use { it.localPort }
    val fakeRepo = FakeAccountRepository()
    val server = McpServer(fakeRepo, port = port)
    server.start()

    try {
        val client = HttpClient.newHttpClient()
        val mapper = ObjectMapper()
        val endpoint = URI.create("http://127.0.0.1:$port/mcp")
        var sessionId: String? = null
        var nextId = 1

        fun sendRpc(
            method: String,
            params: Map<String, Any?> = emptyMap(),
            isNotification: Boolean = false,
            allowRpcError: Boolean = false,
        ): JsonNode? {
            val payload =
                mutableMapOf<String, Any?>(
                    "jsonrpc" to "2.0",
                    "method" to method,
                    "params" to params,
                )
            if (!isNotification) {
                payload["id"] = nextId++
            }
            val builder =
                HttpRequest
                    .newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            sessionId?.let { builder.header("Mcp-Session-Id", it) }

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            response.headers().firstValue("Mcp-Session-Id").ifPresent { sessionId = it }
            if (isNotification) {
                check(response.statusCode() in 200..299) {
                    "Notification $method failed (${response.statusCode()}): ${response.body()}"
                }
                return null
            }

            check(response.statusCode() == 200) {
                "RPC $method failed with HTTP ${response.statusCode()}: ${response.body()}"
            }

            val body = response.body().trim()
            val jsonText =
                if (body.startsWith("data:") || body.contains("\ndata:")) {
                    body
                        .lines()
                        .filter { it.startsWith("data:") }
                        .map { it.removePrefix("data:").trim() }
                        .last { it.isNotEmpty() }
                } else {
                    body
                }
            val node = mapper.readTree(jsonText)
            if (!allowRpcError) {
                check(!node.has("error") || node.get("error").isNull) {
                    "RPC $method returned JSON-RPC error: $node"
                }
            }
            return node
        }

        // 1. Initialize MCP session
        val initRes =
            sendRpc(
                "initialize",
                mapOf(
                    "protocolVersion" to "2025-03-26",
                    "capabilities" to emptyMap<String, Any>(),
                    "clientInfo" to mapOf("name" to "test-client", "version" to "1.0.0"),
                ),
            )!!
        check(initRes["result"]["serverInfo"]["name"].asText() == "moneylens")
        sendRpc("notifications/initialized", isNotification = true)

        // 2. Verify all upstream tools are registered
        val toolsRes = sendRpc("tools/list")!!
        val toolNames = toolsRes["result"]["tools"].map { it["name"].asText() }.toSet()
        val expectedTools =
            setOf(
                "hello",
                "list_accounts",
                "list_categories",
                "get_transactions",
                "create_transactions",
                "update_security_prices",
            )
        check(toolNames == expectedTools) {
            "Expected tools $expectedTools, got $toolNames"
        }

        fun callTool(
            name: String,
            arguments: Map<String, Any?> = emptyMap(),
            expectError: Boolean = false,
        ): JsonNode {
            val res =
                sendRpc(
                    "tools/call",
                    mapOf("name" to name, "arguments" to arguments),
                    allowRpcError = expectError,
                )!!
            val hasRpcError = res.has("error") && !res.get("error").isNull
            if (hasRpcError) {
                check(expectError) { "Tool $name returned unexpected JSON-RPC error: $res" }
                return res["error"]
            }
            val result = res["result"]
            val isError = result.path("isError").asBoolean(false)
            check(isError == expectError) {
                "Tool $name expected isError=$expectError but got $result"
            }
            return result
        }

        // 3. Call every upstream tool and verify request deserialization & structured outputs
        val hello = callTool("hello")
        check(hello["content"][0]["text"].asText() == "Hello from Money Lens!")

        val accounts =
            callTool(
                "list_accounts",
                mapOf(
                    "type" to listOf("bank", "credit_card"),
                    "name" to "Check",
                    "id" to "acct-1",
                    "limit" to 5,
                ),
            )
        check(accounts["structuredContent"]["accounts"].size() == 1)
        check(accounts["structuredContent"]["accounts"][0]["type"].asText() == "bank")
        check(fakeRepo.lastAccountFilter?.types == setOf(AccountKind.BANK, AccountKind.CREDIT_CARD))
        check(fakeRepo.lastAccountFilter?.name == "Check")
        check(fakeRepo.lastAccountFilter?.id == "acct-1")
        check(fakeRepo.lastAccountFilter?.limit == 5)

        val categories =
            callTool(
                "list_categories",
                mapOf("type" to listOf("expense"), "limit" to 10),
            )
        check(categories["structuredContent"]["categories"].size() == 1)
        check(categories["structuredContent"]["categories"][0]["type"].asText() == "expense")
        check(fakeRepo.lastCategoryFilter?.types == setOf(AccountKind.EXPENSE))
        check(fakeRepo.lastCategoryFilter?.limit == 10)

        val txns =
            callTool(
                "get_transactions",
                mapOf(
                    "account_id" to "acct-1",
                    "min_date" to "2026-11-01",
                    "max_date" to "2026-11-22",
                    "description" to "Rainbow",
                    "is_unconfirmed" to false,
                ),
            )
        check(txns["structuredContent"]["transactions"].size() == 1)
        val txnNode = txns["structuredContent"]["transactions"][0]
        check(txnNode["date"].asText() == "2026-11-21")
        check(txnNode["status"].asText() == "cleared")
        check(txnNode["amount"].asLong() == -4250L)
        check(fakeRepo.lastTxnAccountId == "acct-1")
        check(fakeRepo.lastTxnAfterDate == LocalDate.of(2026, 11, 1))
        check(fakeRepo.lastTxnBeforeDate == LocalDate.of(2026, 11, 22))
        check(fakeRepo.lastTxnDescription == "Rainbow")
        check(fakeRepo.lastTxnIsUnconfirmed == false)

        val createRes =
            callTool(
                "create_transactions",
                mapOf(
                    "transactions" to
                        listOf(
                            mapOf(
                                "date" to "2026-11-22",
                                "description" to "Coffee",
                                "account_id" to "acct-1",
                                "amount" to -550,
                                "status" to "cleared",
                                "category_id" to "cat-1",
                            ),
                            mapOf(
                                "date" to "2026-11-22",
                                "description" to "Buy VTI",
                                "account_id" to "inv-1",
                                "amount" to 30000,
                                "shares" to 10000,
                                "price" to 0.003333,
                                "invest_action" to "BUY",
                                "category_id" to "sec-1",
                            ),
                        ),
                ),
            )
        check(createRes["structuredContent"]["created_count"].asInt() == 2)
        check(fakeRepo.createdTransactions.size == 2)
        check(fakeRepo.createdTransactions[0].date == LocalDate.of(2026, 11, 22))
        check(fakeRepo.createdTransactions[0].status == TransactionStatus.CLEARED)
        check(fakeRepo.createdTransactions[1].investAction == InvestTxnType.BUY)

        val updatePricesRes =
            callTool(
                "update_security_prices",
                mapOf(
                    "ticker" to "VTI",
                    "prices" to
                        listOf(
                            mapOf(
                                "date" to "2026-11-20",
                                "price" to 300.0,
                                "high" to 302.5,
                                "low" to 298.0,
                                "volume" to 100000,
                            ),
                            mapOf(
                                "date" to "2026-11-21",
                                "close" to 301.25,
                            ),
                        ),
                ),
            )
        val priceContent = updatePricesRes["structuredContent"]
        check(priceContent["ticker"].asText() == "VTI")
        check(priceContent["updated_count"].asInt() == 2)
        check(priceContent["first_date"].asText() == "2026-11-20")
        check(priceContent["last_date"].asText() == "2026-11-21")
        check(priceContent["current_price"].asDouble() == 301.25)
        check(fakeRepo.lastUpdatedTickerOrId == "VTI")
        check(fakeRepo.lastUpdatedPrices.size == 2)
        check(fakeRepo.lastUpdatedPrices[0].date == LocalDate.of(2026, 11, 20))
        check(fakeRepo.lastUpdatedPrices[1].effectivePrice == 301.25)

        // Error case: update_security_prices without ticker or security_id
        val missingIdError =
            callTool(
                "update_security_prices",
                mapOf("prices" to listOf(mapOf("date" to "2026-11-21", "price" to 301.25))),
                expectError = true,
            )
        check(missingIdError.toString().contains("ticker")) {
            "Expected missing ticker/security_id error, got $missingIdError"
        }
    } finally {
        server.stop()
    }
}

private class FakeAccountRepository : AccountRepository {
    var lastAccountFilter: AccountFilter? = null
    var lastCategoryFilter: AccountFilter? = null
    var lastTxnAccountId: String? = null
    var lastTxnAfterDate: LocalDate? = null
    var lastTxnBeforeDate: LocalDate? = null
    var lastTxnDescription: String? = null
    var lastTxnIsUnconfirmed: Boolean? = null
    val createdTransactions: MutableList<TransactionModel> = mutableListOf()
    var lastUpdatedTickerOrId: String? = null
    var lastUpdatedPrices: List<SecurityPriceModel> = emptyList()

    override fun listAccounts(filter: AccountFilter): List<AccountModel> {
        lastAccountFilter = filter
        return listOf(
            AccountModel(
                id = "acct-1",
                name = "Checking",
                fullName = "Checking",
                type = AccountKind.BANK,
                currencyCode = "USD",
                decimalPlaces = 2,
                parentId = null,
                inactive = false,
            ),
        )
    }

    override fun listCategories(filter: AccountFilter): List<AccountModel> {
        lastCategoryFilter = filter
        return listOf(
            AccountModel(
                id = "cat-1",
                name = "Groceries",
                fullName = "Groceries",
                type = AccountKind.EXPENSE,
                currencyCode = "USD",
                decimalPlaces = 2,
                parentId = null,
                inactive = false,
            ),
        )
    }

    override fun getTransactions(
        accountId: String,
        afterDate: LocalDate,
        beforeDate: LocalDate?,
        description: String?,
        isUnconfirmed: Boolean?,
    ): List<TransactionModel> {
        lastTxnAccountId = accountId
        lastTxnAfterDate = afterDate
        lastTxnBeforeDate = beforeDate
        lastTxnDescription = description
        lastTxnIsUnconfirmed = isUnconfirmed
        return listOf(
            TransactionModel(
                date = LocalDate.of(2026, 11, 21),
                taxDate = null,
                description = "Rainbow Grocery",
                accountId = "acct-1",
                amount = -4250L,
                status = TransactionStatus.CLEARED,
                memo = null,
                checkNumber = null,
                tags = listOf("food"),
                attachments = null,
                categoryId = null,
                splits = listOf(SplitModel("cat-1", 4250L, null, null)),
                isUnconfirmed = false,
                downloaded = true,
            ),
        )
    }

    override fun createTransactions(transactions: List<TransactionModel>) {
        createdTransactions.addAll(transactions)
    }

    override fun updateSecurityPrices(
        tickerOrId: String,
        prices: List<SecurityPriceModel>,
    ): UpdateSecurityPricesResult {
        lastUpdatedTickerOrId = tickerOrId
        lastUpdatedPrices = prices
        val sorted = prices.sortedBy { it.date }
        return UpdateSecurityPricesResult(
            ticker = tickerOrId,
            securityId = "sec-uuid-vti",
            securityName = "Vanguard Total Stock Market ETF",
            updatedCount = sorted.size,
            firstDate = sorted.firstOrNull()?.date,
            lastDate = sorted.lastOrNull()?.date,
            currentPrice = sorted.lastOrNull()?.effectivePrice,
        )
    }
}
