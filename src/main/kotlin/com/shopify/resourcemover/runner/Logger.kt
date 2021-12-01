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

package com.shopify.resourcemover.runner

import kotlin.system.measureTimeMillis

open class Logger {
    open fun log(message: String) {
        println(message)
    }
}

fun Logger.logFrame(title: String, loggingContext: (frameLogger: Logger) -> Unit) {
    val frameWidth = 120

    val headerMessage = "┏━(${title})"
    log("$headerMessage${"━".repeat(frameWidth - headerMessage.length)}")

    val frameLifespan = measureTimeMillis {
        loggingContext.invoke(object : Logger() {
            override fun log(message: String) {
                super.log("┃ $message")
            }
        })
    }

    val footerStart = "┗━(${frameLifespan / 1000f}s)"
    log("$footerStart${"━".repeat(frameWidth - footerStart.length)}")
}

val String.red get() = colorize(colorCode = 31)
val String.green get() = colorize(colorCode = 32)
val String.yellow get() = colorize(colorCode = 33)

fun String.colorize(colorCode: Int): String {
    return "\u001B[${colorCode}m$this\u001B[0m"
}
