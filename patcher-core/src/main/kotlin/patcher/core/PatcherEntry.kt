package patcher.core
import patcher.core.ui.*

import javax.swing.*

object PatcherEntry {
    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (e: Exception) {}
            PatcherUI().isVisible = true
        }
    }
}