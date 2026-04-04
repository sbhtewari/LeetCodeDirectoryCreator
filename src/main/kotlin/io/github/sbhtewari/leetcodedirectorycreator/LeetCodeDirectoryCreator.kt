package io.github.sbhtewari.leetcodedirectorycreator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import java.io.IOException


class LeetCodeDirectoryCreator : AnAction(Icons.AddDirectory) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get the selected directory in the Project View
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val parentDir = if (selectedFile.isDirectory) selectedFile else selectedFile.parent
        if (parentDir == null) return

        val input = Messages.showInputDialog(
            project,
            "Enter LeetCode problem name",
            "Create LeetCode Name Directory",
            Messages.getQuestionIcon(),
            "",
            NonEmptyValidator()
        )

        if (input.isNullOrEmpty()) return

        val directoryName = buildPackageDirectoryName(input)

        if (directoryName.isEmpty()) return

        // 3. Perform File Action (Must be wrapped in a WriteCommandAction)
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                if (parentDir.findChild(directoryName) == null) {
                    parentDir.createChildDirectory(this, directoryName)
                } else {
                    println("Directory already exists")
                }
            } catch (ex: IOException) {
                Messages.showErrorDialog(project, "Could not create directory: " + ex.message, "Error")
            }
        }
    }

    // Validator Class to ensure input isn't empty
    private class NonEmptyValidator : InputValidator {
        override fun checkInput(inputString: String): Boolean {
            return !inputString.trim { it <= ' ' }.isEmpty()
        }

        override fun canClose(inputString: String): Boolean {
            return checkInput(inputString)
        }
    }

    private fun buildPackageDirectoryName(input: String): String {
        val output = mutableListOf<String>()
        val nameSplit = input.lowercase().split(" ")

        val problemId = extractProblemId(nameSplit[0])
        var processingIndex = 0
        if (!problemId.isNullOrEmpty()) {
            processingIndex++
            output.add("p$problemId")
        }

        val wordWithDigitPrefix = extractNameStartWithDigit(nameSplit[processingIndex])
        if (!wordWithDigitPrefix.isNullOrEmpty()) {
            processingIndex++
            output.add(wordWithDigitPrefix)
        }

        // add all remaining indexes to output
        output.addAll(removeSpecialCharacters(nameSplit, processingIndex))

        return output.joinToString("_")
    }

    private fun removeSpecialCharacters(input: List<String>, startIdx: Int): List<String> {
        val subList = input.subList(startIdx, input.size)
        val output = mutableListOf<String>()
        subList.forEach { subStr ->
            val clean = subStr.filter { it.isLetterOrDigit() }
            if (clean.isNotBlank()) output.add(clean)
        }
        return output
    }

    private fun extractNameStartWithDigit(input: String): String? {
        val pattern = Regex("^([0-9]+)(.*)")
        val match = pattern.matchEntire(input)

        if (match != null && match.groups.size == 2) {
            // Get all digits from starting of the word
            val digits = StringBuilder()
            match.groupValues[1].forEach { digit ->
                digits.append(DigitToWord[digit.digitToInt()])
            }

            // Capitalize first character
            val name = match.groupValues[2].replaceFirstChar { it.uppercaseChar() }

            return digits.toString() + name
        }
        return null
    }

    private fun extractProblemId(sourceStr: String): String? {
        val pattern = Regex("^([0-9]+\\.)")

        val match = pattern.find(sourceStr)
        return match?.groups?.get(1)?.value?.trim()?.dropLast(1)
    }
}

private val DigitToWord = mapOf(
    0 to "Zero",
    1 to "One",
    2 to "Two",
    3 to "Three",
    4 to "Four",
    5 to "Five",
    6 to "Six",
    7 to "Seven",
    8 to "Eight",
    9 to "Nine"
)
