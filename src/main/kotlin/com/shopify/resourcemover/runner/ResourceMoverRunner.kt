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
import com.shopify.resourcemover.core.ResourceMover
import com.shopify.resourcemover.core.ResourceType
import com.shopify.resourcemover.core.toModuleInfo
import java.io.File

/**
 * [ResourceMoverRunner] is a class that orchestrates moving resources
 * between multiple directories.
 *
 * @see moveResources
 */
class ResourceMoverRunner(private val logger: Logger) {
    private val resourceMover = ResourceMover()

    /**
     * Moves only [resourceTypesToMove] from [fromDirectory] to all [toDirectories].
     *
     * A resource will only be moved from the [fromDirectory] to any target directory if
     * the following conditions are met:
     *     - The resource IS referenced in the target directory
     *     - The resource type is in the [resourceTypesToMove] set
     *     - The resource IS NOT referenced by the source directory
     *     - The resource IS NOT referenced in any of the other [toDirectories]
     *     - The resource IS NOT referenced in any of the [directoriesDependentOnSource]
     *
     * After a resource is moved new resource dependencies may be introduced in the target
     * directory. Let's say a layout resource is moved. That layout may reference resources
     * such as drawables that were not previously referenced in the target directory.
     *
     * To make sure all resources are moved we have a round system. Every round we count how
     * many resources were moved. If we get a non-zero number then we attempt a new round of
     * moving in case any of the newly moved resources introduced new dependencies.
     *
     * @param fromDirectory the directory to move resources from
     * @param toDirectories the directories to move resources to
     * @param directoriesDependentOnSource directories that are dependent on the [fromDirectory].
     * Resources referenced in these directories will not be moved.
     * @param resourceTypesToMove all resource types that should be moved
     * @param maxRounds maximum amount of moving rounds before terminating
     */
    fun moveResources(
        fromDirectory: File,
        toDirectories: List<File>,
        directoriesDependentOnSource: List<File>,
        resourceTypesToMove: Set<ResourceType>,
        maxRounds: Int
    ) {
        var isActive = true
        var moveResourcesRound = 1
        var totalResourcesMoved = 0

        while (isActive) {
            logger.logFrame(title = "Round #$moveResourcesRound") { frameLogger ->
                val totalResourcesMovedThisRound = runMoveResourceRound(
                    fromDirectory = fromDirectory,
                    toDirectories = toDirectories,
                    resourceTypesToMove = resourceTypesToMove,
                    directoriesDependentOnSource = directoriesDependentOnSource,
                    frameLogger = frameLogger
                )

                totalResourcesMoved += totalResourcesMovedThisRound

                if (totalResourcesMovedThisRound > 0) {
                    frameLogger.log("Moved ${totalResourcesMovedThisRound.toString().green} resource(s). Attempting another round of extraction to see if new dependencies were introduced.")
                    moveResourcesRound++
                } else {
                    frameLogger.log("No resources were moved. Extraction is done.")
                    isActive = false
                }

                if (moveResourcesRound > maxRounds) {
                    frameLogger.log("Exceeded maximum moving rounds ($maxRounds). Terminating moving.".red)
                    isActive = false
                }
            }
        }

        logger.logFrame(title = "Resource moving finished.") { frameLogger ->
            frameLogger.log("$totalResourcesMoved resource(s) moved over $moveResourcesRound rounds.")
        }
    }

    private fun runMoveResourceRound(
        fromDirectory: File,
        toDirectories: List<File>,
        resourceTypesToMove: Set<ResourceType>,
        directoriesDependentOnSource: List<File>,
        frameLogger: Logger
    ): Int {
        val resourceDependenciesOnSource = getAllDependenciesOnSourceModule(
            sourceDirectory = fromDirectory,
            directoriesDependentOnSource = directoriesDependentOnSource,
            resourceTypesToMove = resourceTypesToMove
        )

        val destinationModules = toDirectories.map { destinationDirectory ->
            destinationDirectory.toModuleInfo(resourceTypesToMove)
        }

        return destinationModules.mapIndexed { moduleIndex, moduleInfo ->
            val thisModulesDependencies = moduleInfo.resourceDependencies
            val otherModulesDependencies = destinationModules
                .filterIndexed { filterIndex, _ -> filterIndex != moduleIndex }
                .flatMap { it.resourceDependencies }

            val dependenciesToSkip = resourceDependenciesOnSource + otherModulesDependencies
            val dependenciesToMove = thisModulesDependencies - dependenciesToSkip

            if (dependenciesToMove.isNotEmpty()) {
                frameLogger.log("${moduleInfo.moduleRoot.path}: Only ${dependenciesToMove.size}/${thisModulesDependencies.size} resource(s) referenced can be extracted due to other modules referencing them.")
            } else {
                frameLogger.log("${moduleInfo.moduleRoot.path}: ${"No resources can be moved.".yellow}")
                return@mapIndexed 0
            }

            val totalResourcesMoved = resourceMover.moveResources(
                fromDirectory = fromDirectory,
                toDirectory = moduleInfo.moduleRoot,
                dependencies = dependenciesToMove
            )

            if (totalResourcesMoved > 0) {
                frameLogger.log("${moduleInfo.moduleRoot.path}: ${"Moved $totalResourcesMoved matching resource(s).".green}")
            } else {
                frameLogger.log("${moduleInfo.moduleRoot.path}: ${"No resources were moved.".yellow}")

            }

            totalResourcesMoved
        }.sum()
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
