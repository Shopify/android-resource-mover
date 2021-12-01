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

package com.shopify.resourcemover.cli

import com.shopify.resourcemover.core.ResourceType
import com.shopify.resourcemover.core.toResourceType
import kotlinx.cli.ArgType
import java.io.File

val ArgType.Companion.File: ArgType<File>
    get() = FileArgType()

val ArgType.Companion.Regex: ArgType<Regex>
    get() = RegexArgType()

val ArgType.Companion.ResourceType: ArgType<ResourceType>
    get() = ResourceArgType()

private class FileArgType : ArgType<File>(hasParameter = true) {
    override val description: kotlin.String
        get() = "Path to a file"

    override fun convert(value: kotlin.String, name: kotlin.String): File {
        return File(value)
    }
}

private class RegexArgType : ArgType<Regex>(hasParameter = true) {
    override val description: kotlin.String
        get() = "A regular expression pattern"

    override fun convert(value: kotlin.String, name: kotlin.String): Regex {
        return Regex(value)
    }
}

private class ResourceArgType : ArgType<ResourceType>(hasParameter = true) {
    companion object {
        private val ALL_RESOURCE_TYPES =
            com.shopify.resourcemover.core.ResourceType.values().map { it.rawName }.toString()
    }

    override val description: kotlin.String
        get() = "An Android resource type, one of $ALL_RESOURCE_TYPES"

    override fun convert(value: kotlin.String, name: kotlin.String): ResourceType {
        return checkNotNull(value.toResourceType()) {
            "$value is not a valid android resource, pick from $ALL_RESOURCE_TYPES."
        }
    }
}
