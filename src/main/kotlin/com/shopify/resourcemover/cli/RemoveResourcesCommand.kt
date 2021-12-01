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
import com.shopify.resourcemover.runner.Logger
import com.shopify.resourcemover.runner.ResourceRemoverRunner
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.multiple
import kotlinx.cli.required

@OptIn(ExperimentalCli::class)
class RemoveResourcesCommand : Subcommand(
    name = "remove",
    actionDescription = "Removes unused resources from specified module"
) {
    private val sourceDirectory by option(
        type = ArgType.File,
        shortName = "s",
        fullName = "source",
        description = "Source directory"
    ).required()

    private val dependencies by option(
        type = ArgType.File,
        shortName = "d",
        fullName = "dependency",
        description = "path to module the source module depends on"
    ).multiple()

    private val resourcesToInclude by option(
        type = ArgType.ResourceType,
        shortName = "i",
        fullName = "include",
        description = "resources to delete"
    ).multiple()

    private val resourcesToExclude by option(
        type = ArgType.ResourceType,
        shortName = "e",
        fullName = "exclude",
        description = "resources to keep"
    ).multiple()

    private val resourcesNamesToIgnore by option(
        type = ArgType.Regex,
        fullName = "skip",
        description = "Regular expression pattern for resource names to skip deleting"
    )

    override fun execute() {
        if (resourcesToInclude.isNotEmpty() && resourcesToExclude.isNotEmpty()) {
            error("Cannot specify both resources to include and resources to exclude")
        }

        ResourceRemoverRunner(logger = Logger()).apply {
            removeResources(
                directory = sourceDirectory,
                directoriesDependentOnSource = dependencies,
                resourceTypesToRemove = if (resourcesToInclude.isNotEmpty()) {
                    resourcesToInclude.toSet()
                } else {
                    ResourceType.values().toSet() - resourcesToExclude.toSet()
                },
                maxRounds = 10,
                resourceNamesToIgnore = resourcesNamesToIgnore
            )
        }
    }
}
