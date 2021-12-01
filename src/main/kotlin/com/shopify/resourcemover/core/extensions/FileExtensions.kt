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

package com.shopify.resourcemover.core.extensions

import com.shopify.resourcemover.core.ResourceType
import com.shopify.resourcemover.core.toResourceType
import java.io.File

private val RESOURCE_FILE_EXTENSIONS = setOf("xml", "png", "webp")
private val ESCAPE_SEQUENCE_REGEX = Regex("&[\\w#]+;")
private const val ESCAPE_SEQUENCE_START_MARKER = "RESOURCE_MOVER_ESCAPE_SEQUENCE_START_MARKER"

internal fun File.listFilesRecursively(): List<File> {
    return (listFiles() ?: emptyArray()).flatMap {
        if (it.isDirectory) {
            it.listFilesRecursively()
        } else {
            listOf(it)
        }
    }
}

internal fun File.listResourceFiles(): List<File> {
    return resolve("src/main/res")
        .listFilesRecursively()
        .filter { it.extension in RESOURCE_FILE_EXTENSIONS }
}

/**
 * XML considers character escape sequences such as &apos; equivalent to their
 * UTF-8 character. XML parsers are not required to preserve such escape sequences
 * and can transform them to their equivalent UTF-8 character leading to changes
 * in when the parsed XML is saved. To avoid making these unrelated changes
 * we replace the first character of all escape sequences with some marker that
 * is very unlikely to appear in our source XML files, [ESCAPE_SEQUENCE_START_MARKER].
 * This will prevent the XML parser from converting the character escape sequences.
 */
internal fun File.preProcessEscapeSequences() {
    if (!exists()) {
        return
    }

    val processedFile = readText()
        .replace(ESCAPE_SEQUENCE_REGEX) { result ->
            "$ESCAPE_SEQUENCE_START_MARKER${result.value.drop(1)}"
        }

    writeText(processedFile)
}

/**
 * Follow up to [preProcessEscapeSequences] that replaces all the [ESCAPE_SEQUENCE_START_MARKER]
 * with an '&' to preserve the original escape sequences.
 */
internal fun File.postProcessEscapeSequences() {
    if (!exists()) {
        return
    }

    val processedFile = readText()
        .replace(ESCAPE_SEQUENCE_START_MARKER, "&")

    writeText(processedFile)
}

/**
 * Determines the resource type based on the parent directory name.
 * Example: src/main/res/anim/slide_in_from_top.xml would have a [ResourceType.Animation]
 */
internal val File.resourceType: ResourceType?
    get() = parentFile.name.split("-").first().toResourceType()
