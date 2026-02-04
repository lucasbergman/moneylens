package com.moneydance.modules.features.moneylens.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.moneydance.modules.features.moneylens.AccountRepository
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification

class ListCategoriesTool(
    accountRepository: AccountRepository,
    objectMapper: ObjectMapper,
) {
    val spec: SyncToolSpecification =
        accountListSpec(
            toolName = "list_categories",
            description =
                "Lists all categories (income and expense accounts) in the open " +
                    "Moneydance file.",
            requestSchemaFile = "list-categories-request.schema.json",
            responseSchemaFile = "list-categories-response.schema.json",
            responseKey = "categories",
            objectMapper = objectMapper,
            listFn = accountRepository::listCategories,
        )
}
