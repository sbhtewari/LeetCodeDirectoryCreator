package io.github.sbhtewari.leetcodedirectorycreator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.IOException


class LeetCodeDirectoryCreator : AnAction(Icons.AddDirectory) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get the selected directory in the Project View
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val parentDir = if (selectedFile.isDirectory) selectedFile else selectedFile.parent
        if (parentDir == null) return

        val inputMessage = "Enter LeetCode problem name"
        val inputTitle = "Create LeetCode Name Directory"
        val inputInitialValue = ""
        val input = Messages.showInputDialog(
            project,
            inputMessage,
            inputTitle,
            Messages.getQuestionIcon(),
            inputInitialValue,
            NonEmptyValidator()
        )

        if (input.isNullOrEmpty()) return

        val directoryName = buildPackageDirectoryName(input)
        val kotlinObjectName = buildKotlinObjectName(input)

        if (directoryName.isEmpty() || kotlinObjectName.isEmpty()) return

        // 3. Perform File Action (Must be wrapped in a WriteCommandAction)
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                if (parentDir.findChild(directoryName) == null) {
                    val childDir = parentDir.createChildDirectory(this, directoryName)
                    createJavaFileWithPackage(project, childDir, "Solution")
                    createKotlinObjectFile(project, childDir, kotlinObjectName)
                } else {
                    println("Directory already exists")
                }
            } catch (ex: IOException) {
                Messages.showErrorDialog(project, "Could not create directory: " + ex.message, "Error")
            }
        }
    }

    fun createJavaFileWithPackage(
        project: Project,
        childDirVirtualFile: VirtualFile,
        className: String
    ): PsiFile? {
        return try {
            WriteAction.computeAndWait<PsiFile, IOException> {
                // 1. Convert VirtualFile directory to a PSI Directory
                val psiDirectory: PsiDirectory = PsiManager.getInstance(project)
                    .findDirectory(childDirVirtualFile) ?: return@computeAndWait null

                // 2. Create a Java Class file using IntelliJ's built-in Java directory service.
                // This automatically calculates the proper package header (e.g., "package com.example.foo;")
                // based on the directory's position relative to the Source Root.
                val psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, className)

                // 3. Return the underlying file
                psiClass.containingFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createKotlinObjectFile(
        project: Project,
        childDirVirtualFile: VirtualFile,
        objectName: String
    ): KtFile? {
        return try {
            WriteAction.computeAndWait<KtFile, IOException> {
                // 1. Convert the VirtualFile directory to a PSI Directory
                val psiDirectory: PsiDirectory = PsiManager.getInstance(project)
                    .findDirectory(childDirVirtualFile) ?: return@computeAndWait null

                // 2. Automatically calculate the correct package path relative to the Source Roots
                val psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory)
                val packageName = psiPackage?.qualifiedName

                // 3. Use KtPsiFactory to dynamically build the structured file content
                val psiFactory = KtPsiFactory(project)

                // Build the source code buffer cleanly
                val fileContent = buildString {
                    if (!packageName.isNullOrBlank()) {
                        appendLine("package $packageName")
                        appendLine()
                    }
                    appendLine("import test.SimpleTestCase")
                    appendLine()
                    appendLine("object $objectName : SimpleTestCase() {")
                    appendLine("    override fun calculateResult(): Any? {")
                    appendLine("        ")
                    appendLine("    }")
                    appendLine("}")
                }

                // 4. Instantiate the file safely inside the target directory
                val ktFile = psiFactory.createFile("${objectName}.kt", fileContent)
                val addedFile = psiDirectory.add(ktFile) as KtFile

                addedFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
        var processingIndex = 0

        val problemId = extractProblemNumber(nameSplit[0])
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

    private fun buildKotlinObjectName(input: String): String {
        val output = mutableListOf<String>()
        val nameSplit = convertToTitleCase(input)
        var processingIndex = 0

        val problemId = extractProblemNumber(nameSplit[0])
        // skip problem number for Object name
        if (!problemId.isNullOrEmpty()) {
            processingIndex++
        }

        val wordWithDigitPrefix = extractNameStartWithDigit(nameSplit[processingIndex])
        if (!wordWithDigitPrefix.isNullOrEmpty()) {
            processingIndex++
            output.add(wordWithDigitPrefix)
        }

        // add all remaining indexes to output
        output.addAll(removeSpecialCharacters(nameSplit, processingIndex))

        return output.joinToString("")
    }

    private fun convertToTitleCase(input: String): List<String> {
        return input.split(" ")
            .filter { it.isNotEmpty() }
            .map { word ->
                word.lowercase()
                    .replaceFirstChar { it.titlecase() }
            }
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

    private fun extractProblemNumber(sourceStr: String): String? {
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
