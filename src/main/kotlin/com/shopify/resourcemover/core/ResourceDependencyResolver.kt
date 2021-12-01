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

import com.shopify.resourcemover.core.extensions.listFilesRecursively
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * [ResourceDependencyResolver] is a utility class that moves resources between project directories.
 *
 * @see resolveResourcesDependencies
 */
class ResourceDependencyResolver {
    companion object {
        private val RESOURCE_TYPES = ResourceType.values().joinToString("|") { it.rawName }
        private val RESOURCE_CODE_USAGE_PATTERN = Regex("($RESOURCE_TYPES)\\.(\\w+)")
        private val RESOURCE_XML_USAGE_PATTERN = Regex("@([A-Za-z]+)/([\\w.]+)")
        private val STYLE_PARENT_PATTERN = Regex("parent\\s*=\\s*\"([\\w.]+)\"")
        private val DATABINDING_IMPORT_PATTERN = Regex("databinding.(\\w+)")
        private val CAPITAL_LETTER_PATTERN = Regex("[A-Z]")
        private val SUPPORTED_FILE_TYPES = setOf("java", "xml", "kt")
    }

    /**
     * Resolves all resources referenced in [inDirectory].
     *
     * @param inDirectory the directory to scan resources in
     * @return A [Set] of [ResourceDependency] containing all resources referenced in [inDirectory]
     */
    fun resolveResourcesDependencies(inDirectory: File): Set<ResourceDependency> {
        return inDirectory
            .resolve("src")
            .listFilesRecursively()
            .filter { it.extension in SUPPORTED_FILE_TYPES }
            .flatMap { file ->
                InputStreamReader(file.inputStream()).use { inputSteamReader ->
                    BufferedReader(inputSteamReader).useLines { lines ->
                        val linesList = lines.toList()

                        linesList.flatMap { it.extractCodeResourceDependencies() } +
                            linesList.flatMap { it.extractXMLResourceDependencies() } +
                            linesList.flatMap { it.extractStyleParentDependencies() } +
                            linesList.flatMap { it.extractDataBindingDependencies() }
                    }
                }
            }
            .toSet()
    }

    private fun String.extractCodeResourceDependencies(): List<ResourceDependency> {
        return RESOURCE_CODE_USAGE_PATTERN.findAll(this).mapNotNull { regexMatch ->
            val resourceType = regexMatch.groupValues[1].toResourceType() ?: return@mapNotNull null
            val resourceName = regexMatch.groupValues[2]
            ResourceDependency(type = resourceType, name = resourceName)
        }.toList()
    }

    private fun String.extractXMLResourceDependencies(): List<ResourceDependency> {
        return RESOURCE_XML_USAGE_PATTERN.findAll(this).mapNotNull { regexMatch ->
            val resourceType = regexMatch.groupValues[1].toResourceType() ?: return@mapNotNull null
            val resourceName = regexMatch.groupValues[2].replace(".", "_")
            ResourceDependency(type = resourceType, name = resourceName)
        }.toList()
    }

    private fun String.extractStyleParentDependencies(): List<ResourceDependency> {
        return STYLE_PARENT_PATTERN.findAll(this).map { regexMatch ->
            val resourceName = regexMatch.groupValues[1].replace(".", "_")
            ResourceDependency(type = ResourceType.Style, name = resourceName)
        }.toList()
    }

    private fun String.extractDataBindingDependencies(): List<ResourceDependency> {
        return DATABINDING_IMPORT_PATTERN.findAll(this).map { regexMatch ->
            val resourceName = regexMatch.groupValues[1]
                .replace("Binding", "")
                .replace(CAPITAL_LETTER_PATTERN) { matchResult ->
                    if (matchResult.range.first == 0) {
                        matchResult.value.lowercase()
                    } else {
                        "_${matchResult.value.lowercase()}"
                    }
                }

            ResourceDependency(type = ResourceType.Layout, name = resourceName)
        }.toList()
    }
}
