/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Shopify Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.shopify.resourcemover.core

import com.shopify.resourcemover.core.extensions.detachElementAndAssociatedWhitespaceAndComments
import com.shopify.resourcemover.core.extensions.listResourceFiles
import com.shopify.resourcemover.core.extensions.postProcessEscapeSequences
import com.shopify.resourcemover.core.extensions.preProcessEscapeSequences
import com.shopify.resourcemover.core.extensions.resourceName
import com.shopify.resourcemover.core.extensions.trimStart
import org.jdom2.Document
import org.jdom2.Text
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader

/**
 * [ResourceMover] is a utility class that moves resources between project directories.
 *
 * @see moveResources
 */
class ResourceMover {
    companion object {
        private val EMPTY_RESOURCES_DOCUMENT = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources xmlns:tools="http://schemas.android.com/tools">
            </resources>
        """.trimIndent()
    }

    private val saxBuilder = SAXBuilder()

    private val xmlOutputter = XMLOutputter().apply {
        // Android style xml formatting
        format = Format.getRawFormat().apply {
            lineSeparator = "\n"
        }
    }

    /**
     * Moves resources from directory [fromDirectory] to directory [toDirectory]. This only
     * moves resources specified in the set of [dependencies].
     *
     * @param fromDirectory directory to extract resources from
     * @param toDirectory directory to move resources to
     * @param dependencies resources that should be moved
     *
     * @return [Int] the number of resources that have been moved.
     */
    fun moveResources(
        fromDirectory: File,
        toDirectory: File,
        dependencies: Set<ResourceDependency>
    ): Int {
        return fromDirectory
            .listResourceFiles()
            .sumOf { fileToExtractResourcesFrom ->
                val relativePathOfSource = fileToExtractResourcesFrom.relativeTo(fromDirectory).path
                val destinationFile = toDirectory.resolve(relativePathOfSource)

                moveResourcesInFile(
                    fromFile = fileToExtractResourcesFrom,
                    toFile = destinationFile,
                    dependencies = dependencies
                )
            }
    }

    private fun moveResourcesInFile(
        fromFile: File,
        toFile: File,
        dependencies: Set<ResourceDependency>
    ): Int {
        val resourceNamesToMove = dependencies.map { it.name }.toSet()

        fun moveResourceFileIfNotReferencedByDependencies(): Int {
            return if (fromFile.nameWithoutExtension in resourceNamesToMove && !toFile.exists()) {
                // not a resource file, we should move it to the destination and that's it
                fromFile.copyTo(toFile, overwrite = true)
                fromFile.delete()
                1
            } else {
                // this module doesn't depend on this file
                0
            }
        }

        if (fromFile.extension != "xml") {
            return moveResourceFileIfNotReferencedByDependencies()
        }

        fromFile.preProcessEscapeSequences()
        toFile.preProcessEscapeSequences()

        val sourceXmlDocument = saxBuilder.build(fromFile)
        val destinationXmlDocument = loadDestinationDocument(toFile)

        // If the root element of the document is not resources then the file itself is a standalone resource.
        // Try to move that file to the destination.
        if (sourceXmlDocument.rootElement.name != "resources") {
            return moveResourceFileIfNotReferencedByDependencies()
        }

        // Get all elements that match the resources to move
        val elementsToMove = sourceXmlDocument.rootElement.children.filter { element ->
            element.resourceName in resourceNamesToMove
        }

        if (elementsToMove.isEmpty()) {
            fromFile.postProcessEscapeSequences()
            toFile.postProcessEscapeSequences()
            return 0
        }

        // Lift out dependent resources and drop them into the destination document.
        elementsToMove.forEach { resourceElement ->
            val contentToMove =
                sourceXmlDocument.rootElement.detachElementAndAssociatedWhitespaceAndComments(resourceElement)

            contentToMove.forEach { content ->
                destinationXmlDocument.rootElement.addContent(content)
            }
        }

        if (sourceXmlDocument.rootElement.children.size == 0) {
            // We removed all elements in the source file, delete it.
            fromFile.delete()
        } else {
            // Update the source file.
            xmlOutputter.output(sourceXmlDocument, fromFile.outputStream())
            fromFile.postProcessEscapeSequences()
        }

        // Clean excess whitespace from start of document
        destinationXmlDocument.rootElement.trimStart()

        // Append a newline after the last resource.
        val newLineNode = Text("\n")
        destinationXmlDocument.rootElement.addContent(newLineNode)

        val destinationOutputStream = toFile.apply {
            parentFile.mkdirs()
            createNewFile()
        }.outputStream()

        destinationOutputStream.use {
            xmlOutputter.output(destinationXmlDocument, it)
            toFile.postProcessEscapeSequences()
        }

        return elementsToMove.size
    }

    private fun loadDestinationDocument(destinationFile: File): Document {
        // Try to load existing destination document. If it doesn't exist initialize a new empty one.
        return try {
            saxBuilder.build(destinationFile)
        } catch (ex: Exception) {
            saxBuilder.build(InputSource(StringReader(EMPTY_RESOURCES_DOCUMENT)))
        }
    }
}
