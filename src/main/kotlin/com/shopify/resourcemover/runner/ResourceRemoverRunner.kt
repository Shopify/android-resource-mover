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

import com.shopify.resourcemover.core.ResourceDependency
import com.shopify.resourcemover.core.ResourceRemover
import com.shopify.resourcemover.core.ResourceType
import com.shopify.resourcemover.core.toModuleInfo
import java.io.File

/**
 * [ResourceRemoverRunner] is a class that orchestrates removing resources from a target module.
 *
 * @see removeResources
 */
class ResourceRemoverRunner(private val logger: Logger) {
    private val resourceRemover = ResourceRemover()

    /**
     * Removes resources defined in [directory] that are not referenced by any of the
     * [directoriesDependentOnSource].
     *
     * After a resource is removed there may be new unused resources that were previously
     * referenced by the removed resources.
     *
     * To make sure all uses resources are removed we have a round system. Every round we count how
     * many resources were removed. If we get a non-zero number then we attempt a new round of
     * removal in case new unused resources were introduced.
     *
     * @param directory the directory to remove unused resources from
     * @param directoriesDependentOnSource directories that are dependent on the [directory].
     * Resources referenced in these directories will not be removed.
     * @param resourceTypesToRemove all resource types that should be removed
     * @param maxRounds maximum amount of removal rounds before terminating
     * @param resourceNamesToIgnore ignore pattern for resources that should be preserved even if unused
     */
    fun removeResources(
        directory: File,
        directoriesDependentOnSource: List<File>,
        resourceTypesToRemove: Set<ResourceType>,
        maxRounds: Int,
        resourceNamesToIgnore: Regex?
    ) {
        var isActive = true
        var removeResourcesRound = 1
        var totalResourcesRemoved = 0

        while (isActive) {
            logger.logFrame(title = "Round #$removeResourcesRound") { frameLogger ->
                val totalResourcesRemovedThisRound = runRemoveResourceRound(
                    directory = directory,
                    resourceTypesToRemove = resourceTypesToRemove,
                    directoriesDependentOnSource = directoriesDependentOnSource,
                    frameLogger = frameLogger,
                    resourceNamesToIgnore = resourceNamesToIgnore
                )

                totalResourcesRemoved += totalResourcesRemovedThisRound

                if (totalResourcesRemovedThisRound > 0) {
                    frameLogger.log("Removed ${totalResourcesRemovedThisRound.toString().green} resource(s). Attempting another round of removal to see if new dependencies were introduced.")
                    removeResourcesRound++
                } else {
                    frameLogger.log("No resources were removed. Removal is done.")
                    isActive = false
                }

                if (removeResourcesRound > maxRounds) {
                    frameLogger.log("Exceeded maximum removal rounds ($maxRounds). Terminating removal.".red)
                    isActive = false
                }
            }
        }

        logger.logFrame(title = "Resource removal finished.") { frameLogger ->
            frameLogger.log("$totalResourcesRemoved resource(s) removal over $removeResourcesRound rounds.")
        }
    }

    private fun runRemoveResourceRound(
        directory: File,
        resourceTypesToRemove: Set<ResourceType>,
        directoriesDependentOnSource: List<File>,
        frameLogger: Logger,
        resourceNamesToIgnore: Regex?
    ): Int {
        val resourceDependenciesOnSource = getAllDependenciesOnSourceModule(
            sourceDirectory = directory,
            directoriesDependentOnSource = directoriesDependentOnSource,
            resourceTypesToMove = resourceTypesToRemove
        )

        val totalResourcesRemoved = resourceRemover.removeResources(
            directory = directory,
            dependencies = resourceDependenciesOnSource,
            resourceTypesToRemove = resourceTypesToRemove,
            resourceNamesToIgnore = resourceNamesToIgnore
        )

        if (totalResourcesRemoved > 0) {
            frameLogger.log("Removed $totalResourcesRemoved matching resource(s).".green)
        } else {
            frameLogger.log("No resources were removed".yellow)

        }

        return totalResourcesRemoved
    }

    private fun getAllDependenciesOnSourceModule(
        sourceDirectory: File,
        resourceTypesToMove: Set<ResourceType>,
        directoriesDependentOnSource: List<File>
    ): Set<ResourceDependency> {
        val sourceModule = sourceDirectory.toModuleInfo(resourceTypesToMove)

        val destinationModuleDependencies = directoriesDependentOnSource.flatMap {
            it.toModuleInfo(resourceTypesToMove).resourceDependencies
        }

        return sourceModule.resourceDependencies + destinationModuleDependencies
    }
}
