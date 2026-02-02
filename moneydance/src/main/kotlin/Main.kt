package com.moneydance.modules.features.moneylens

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.moneydance.apps.md.controller.FeatureModule
import java.io.File
import java.time.Period
import javax.swing.JOptionPane

private const val URI = "moneylens:hello"

/**
 * A "Hello, World" sample Moneydance extension written in Kotlin.
 */
class Main : FeatureModule() {
    private val mcpServer =
        McpServer(
            MoneydanceAccountRepository { context.currentAccountBook },
        )

    /**
     * Called by Moneydance when the extension is loaded. This is where we register
     * the feature to be invoked, for example, from a menu item or toolbar button.
     */
    override fun init() {
        try {
            ensureStateDirectory()
            context!!.registerFeature(this, URI, null, "Money Lens")
            mcpServer.start()
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    override fun cleanup() {
        mcpServer.stop()
    }

    /**
     * Called by Moneydance when the user selects our feature. The 'uri' is the
     * command we registered in the init() method.
     */
    override fun invoke(uri: String) {
        if (uri != URI) return
        MainDialog(::onExport, ::onImport).isVisible = true
    }

    private fun onExport(f: File) {
        if (context == null) throw IllegalStateException("Null context")
        val book = context.currentAccountBook
        if (book == null) {
            System.err.println("No account book open")
            return
        }

        try {
            val export = Exporter(book).export(Period.ofDays(10))
            val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            mapper.writeValue(f, export)
        } catch (e: Exception) {
            System.err.println("Exception on export: ${e.message}")
            e.printStackTrace(System.err)
        }
        JOptionPane.showMessageDialog(null, "Export successful!")
    }

    private fun onImport(f: File) {
        if (context == null) throw IllegalStateException("Null context")
        val book = context.currentAccountBook
        if (book == null) {
            System.err.println("No account book open")
            return
        }

        try {
            val result = Importer(book).import(f)
            val message =
                buildString {
                    append("Imported ${result.importedCount} transaction(s).")
                    if (result.errors.isNotEmpty()) {
                        append("\n\nErrors:\n")
                        result.errors.forEach { append("• $it\n") }
                    }
                }
            JOptionPane.showMessageDialog(null, message)
        } catch (e: Exception) {
            System.err.println("Exception on import: ${e.message}")
            e.printStackTrace(System.err)
            JOptionPane.showMessageDialog(
                null,
                "Import failed: ${e.message}",
                "Import Error",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    /**
     * Returns the name of the extension.
     */
    override fun getName(): String = "Money Lens"

    private fun ensureStateDirectory() {
        val stateDir = getStateDirectory()
        if (!stateDir.exists()) {
            stateDir.mkdirs()
        }
    }

    private fun getStateDirectory(): File {
        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name")

        return when {
            osName.startsWith("windows", ignoreCase = true) -> {
                // Windows: %APPDATA%\MoneyLens
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                File(appData, "MoneyLens")
            }
            osName.startsWith("mac", ignoreCase = true) -> {
                // macOS: ~/Library/Application Support/MoneyLens
                File(userHome, "Library/Application Support/MoneyLens")
            }
            else -> {
                // Unix: ~/.config/moneylens
                val configHome = System.getenv("XDG_CONFIG_HOME") ?: "$userHome/.config"
                File(configHome, "moneylens")
            }
        }
    }
}
