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
import com.shopify.resourcemover.core.extensions.resourceType
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.File

/**
 * [ResourceRemover] is a utility class that removes unused resources from a module.
 *
 * @see removeResources
 */
class ResourceRemover {
    private val saxBuilder = SAXBuilder()

    private val xmlOutputter = XMLOutputter().apply {
        // Android style xml formatting
        format = Format.getRawFormat().apply {
            lineSeparator = "\n"
        }
    }

    /**
     * Deletes all xml resources defined in [directory] that is not referenced
     * in any of the [dependencies] directories.
     *
     * @param directory directory to delete resources from
     * @param dependencies module directories dependent on [directory]
     *
     * @return [Int] the number of resources that have been deleted.
     */
    fun removeResources(
        directory: File,
        resourceTypesToRemove: Set<ResourceType>,
        dependencies: Set<ResourceDependency>,
        resourceNamesToIgnore: Regex?
    ): Int {
        return directory
            .listResourceFiles()
            .sumOf { fileToExtractResourcesFrom ->
                removeUnusedResourcesIn(
                    file = fileToExtractResourcesFrom,
                    dependencies = dependencies,
                    resourceTypesToRemove = resourceTypesToRemove,
                    resourceNamesToIgnore = resourceNamesToIgnore
                )
            }
    }

    private fun removeUnusedResourcesIn(
        file: File,
        resourceTypesToRemove: Set<ResourceType>,
        dependencies: Set<ResourceDependency>,
        resourceNamesToIgnore: Regex?
    ): Int {
        val resourceNamesToKeep = dependencies.map { it.name }.toSet()

        fun deleteResourceFileIfNotReferenced(): Int {
            return if (
                file.resourceType in resourceTypesToRemove &&
                file.nameWithoutExtension !in resourceNamesToKeep &&
                resourceNamesToIgnore?.containsMatchIn(file.nameWithoutExtension) != true
            ) {
                file.delete()
                1
            } else {
                0
            }
        }

        fun Element?.resourceNameMatchesIgnorePattern(): Boolean {
            return resourceNamesToIgnore?.containsMatchIn(this?.getAttribute("name")?.value ?: "") == true
        }

        if (file.extension != "xml") {
            return deleteResourceFileIfNotReferenced()
        }

        file.preProcessEscapeSequences()
        val sourceXmlDocument = saxBuilder.build(file)

        // If the root element of the document is not resources then the file itself is a standalone resource.
        // Delete the resource if it isn't referenced.
        if (sourceXmlDocument.rootElement.name != "resources") {
            return deleteResourceFileIfNotReferenced()
        }

        // Get all elements that match the resources to move
        val elementsToDelete = sourceXmlDocument.rootElement.children.filter { element ->
            element.resourceType in resourceTypesToRemove &&
                element.resourceName !in resourceNamesToKeep &&
                !element.resourceNameMatchesIgnorePattern()
        }

        if (elementsToDelete.isEmpty()) {
            file.postProcessEscapeSequences()
            return 0
        }

        // Remove elements to delete
        elementsToDelete.forEach { resourceElement ->
            sourceXmlDocument.rootElement.detachElementAndAssociatedWhitespaceAndComments(resourceElement)
        }

        if (sourceXmlDocument.rootElement.children.size == 0) {
            // We removed all elements in the source file, delete it.
            file.delete()
        } else {
            // Update the source file.
            xmlOutputter.output(sourceXmlDocument, file.outputStream())
            file.postProcessEscapeSequences()
        }

        return elementsToDelete.size
    }
}
