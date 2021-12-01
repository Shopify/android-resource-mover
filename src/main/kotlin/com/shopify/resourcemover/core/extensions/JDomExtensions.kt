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
import org.jdom2.Content
import org.jdom2.Element
import org.jdom2.Text

/**
 * This method detaches a target child [element] from this [Element] instance. It will also attempt
 * to detach whitespace content before the target [element] as well as any comments and whitespace
 * associated with those comments. See below for a better visualization:
 *
 * <TEXT>\n    </TEXT>
 * <COMMENT>This is a comment</COMMENT>
 * <TEXT><\n    </TEXT>
 * <ELEMENT><string name="test">Test</string><ELEMENT>
 *
 * Say the string element above is the target element we want to detach. To preserve the indentation
 * we also need to remove the text node before it that contains a newline and indentation. We then look
 * before the text content to see if there is a comment. If so we also detach that and the previous node
 * if it's white space.
 */
internal fun Element.detachElementAndAssociatedWhitespaceAndComments(element: Element): List<Content> {
    val detachedContent = mutableListOf<Content>()

    fun Content.detachAndAddToOutput() {
        this@detachElementAndAssociatedWhitespaceAndComments.removeContent(this)
        detach()
        detachedContent += this
    }

    val indexOfThisChild = indexOf(element)
    val potentialWhitespaceNode = getContentOrNull(indexOfThisChild - 1)
    val potentialCommentNode = getContentOrNull(indexOfThisChild - 2)

    potentialCommentNode?.let { contentNode ->
        if (contentNode.isComment) {
            val potentialWhitespaceBeforeCommentNode = getContentOrNull(indexOfThisChild - 3)

            potentialWhitespaceBeforeCommentNode?.let { contentBeforeComment ->
                if (contentBeforeComment.isWhitespace) {
                    contentBeforeComment.detachAndAddToOutput()
                }
            }

            contentNode.detachAndAddToOutput()
        }
    }

    // Check to see if the previous node is whitespace (i.e indentation). If so detach it
    // add it to the output.
    potentialWhitespaceNode?.let { contentNode ->
        if (contentNode.isWhitespace) {
            contentNode.detachAndAddToOutput()
        }
    }

    // Detach the target element and add it to the output.
    element.detachAndAddToOutput()

    return detachedContent
}

/**
 * Coalesces all whitespace nodes at start of the document into a single
 * indentation node.
 */
internal fun Element.trimStart(indentation: String = " ".repeat(4)) {
    content
        .takeWhile { it.isWhitespace }
        .forEach { removeContent(it) }

    addContent(0, Text("\n$indentation"))
}

/**
 * Gets the resource name of this xml [Element]
 */
internal val Element?.resourceName: String?
    get() = this?.getAttribute("name")?.value?.replace(".", "_")

/**
 * Gets the resource type of this xml [Element]
 */
internal val Element?.resourceType: ResourceType?
    get() = this?.name?.toResourceType()

private val Content.isComment: Boolean
    get() = cType == Content.CType.Comment

private val Content.isWhitespace: Boolean
    get() = cType == Content.CType.Text && value.isBlank()

private fun Element.getContentOrNull(index: Int): Content? {
    return if (index in 0 until content.size) {
        content[index]
    } else {
        null
    }
}
