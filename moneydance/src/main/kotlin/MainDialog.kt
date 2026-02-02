package com.moneydance.modules.features.moneylens

import java.awt.FlowLayout
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class MainDialog(
    onExport: (File) -> Unit,
    onImport: (File) -> Unit,
) : JDialog(null as Frame?, "Money Lens") {
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
        exportButton.addActionListener {
            val fileChooser = JFileChooser()
            val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            fileChooser.selectedFile = File("moneydance-export-$date.json")
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                onExport(fileChooser.selectedFile)
                isVisible = false
            }
        }
        buttonPanel.add(exportButton)

        val importButton = JButton("Import...")
        importButton.addActionListener {
            val fileChooser = JFileChooser()
            fileChooser.fileFilter = FileNameExtensionFilter("JSON Files", "json")
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                onImport(fileChooser.selectedFile)
                isVisible = false
            }
        }
        buttonPanel.add(importButton)

        buttonPanel.alignmentX = CENTER_ALIGNMENT
        panel.add(buttonPanel)

        add(panel)
        pack()
        setLocationRelativeTo(null)

        modalityType = ModalityType.APPLICATION_MODAL
    }
}
