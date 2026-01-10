package com.moneydance.modules.features.moneylens

import java.awt.FlowLayout
import java.awt.Frame
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel

class MainDialog : JDialog(null as Frame?, "Money Lens") {
    init {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = createEmptyBorder(20, 20, 20, 20)

        val label = JLabel("Welcome to Money Lens. Select an action below:")
        label.alignmentX = CENTER_ALIGNMENT
        panel.add(label)
        panel.add(Box.createVerticalStrut(20))

        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout(FlowLayout.CENTER)

        val exportButton = JButton("Export...")
        exportButton.isEnabled = false
        buttonPanel.add(exportButton)

        val importButton = JButton("Import...")
        importButton.isEnabled = false
        buttonPanel.add(importButton)

        buttonPanel.alignmentX = CENTER_ALIGNMENT
        panel.add(buttonPanel)

        add(panel)
        pack()
        setLocationRelativeTo(null)

        modalityType = ModalityType.APPLICATION_MODAL
    }
}
