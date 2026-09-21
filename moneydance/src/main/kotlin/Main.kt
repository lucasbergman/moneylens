package com.moneydance.modules.features.moneylens

import com.moneydance.apps.md.controller.FeatureModule
import java.io.File
import javax.swing.JOptionPane

private const val URI = "moneylens:hello"

/**
 * Money Lens extension entry point.
 */
@Suppress("unused")
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

    private fun cleanupInternal() {
        mcpServer.stop()
    }

    override fun unload() = cleanupInternal()

    override fun cleanup() = cleanupInternal()

    /**
     * Called by Moneydance when the user selects our feature. The 'uri' is the
     * command we registered in the init() method.
     */
    override fun invoke(uri: String) {
        if (uri != URI) return
        JOptionPane.showMessageDialog(
            null,
            "Money Lens MCP server is running on http://127.0.0.1:51234/mcp",
            "Money Lens",
            JOptionPane.INFORMATION_MESSAGE,
        )
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
