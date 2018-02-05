package io.ktor.content

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.*

private val pathParameterName = "static-content-path-parameter"

private val staticRootFolderKey = AttributeKey<File>("BaseFolder")

/**
 * Base folder for relative files calculations for static content
 */
var Route.staticRootFolder: File?
    get() = attributes.getOrNull(staticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        if (value != null)
            attributes.put(staticRootFolderKey, value)
        else
            attributes.remove(staticRootFolderKey)
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

/**
 * Create a block for static content
 */
fun Route.static(configure: Route.() -> Unit): Route = apply(configure)

/**
 * Create a block for static content at specified [remotePath]
 */
fun Route.static(remotePath: String, configure: Route.() -> Unit) = route(remotePath, configure)

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
fun Route.default(localPath: String) = default(File(localPath))

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    get {
        if (file.isFile) {
            call.respond(LocalFileContent(file).applyConfigures(this@default))
        }
    }
}

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
fun Route.file(remotePath: String, localPath: String = remotePath) = file(remotePath, File(localPath))

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    get(remotePath) {
        if (file.isFile) {
            call.respond(LocalFileContent(file).applyConfigures(this@file))
        }
    }
}

/**
 * Sets up routing to serve all files from [folder]
 */
fun Route.files(folder: String) = files(File(folder))

/**
 * Sets up routing to serve all files from [folder]
 */
fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        if (file.isFile) {
            call.respond(LocalFileContent(file).applyConfigures(this@files))
        }
    }
}

private val staticBasePackageName = AttributeKey<String>("BasePackage")

/**
 * Base package for relative resources calculations for static content
 */
var Route.staticBasePackage: String?
    get() = attributes.getOrNull(staticBasePackageName) ?: parent?.staticBasePackage
    set(value) {
        if (value != null)
            attributes.put(staticBasePackageName, value)
        else
            attributes.remove(staticBasePackageName)
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

/**
 * Sets up routing to serve [resource] as [remotePath] in [resourcePackage]
 */
fun Route.resource(remotePath: String, resource: String = remotePath, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get(remotePath) {
        val content = call.resolveResource(resource, packageName)
        if (content != null)
            call.respond(content.applyConfigures(this@resource))
    }
}

/**
 * Sets up routing to serve all resources in [resourcePackage]
 */
fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val content = call.resolveResource(relativePath, packageName)
        if (content != null)
            call.respond(content.applyConfigures(this@resources))
    }
}

/**
 * Specifies [resource] as a default resources to serve when folder is requested
 */
fun Route.defaultResource(resource: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get {
        val content = call.resolveResource(resource, packageName)
        if (content != null)
            call.respond(content.applyConfigures(this@defaultResource))
    }
}

private val staticContentAspectsKey = AttributeKey<MutableList<OutgoingContent.() -> Unit>>("Aspects")

private fun OutgoingContent.applyConfigures(route: Route): OutgoingContent = apply {
    route.staticContentConfigurations.forEach { apply(it) }
}

fun Route.configureContent(configure: OutgoingContent.() -> Unit) {
    val list = attributes.getOrNull(staticContentAspectsKey)
            ?: mutableListOf<OutgoingContent.() -> Unit>().also { attributes.put(staticContentAspectsKey, it) }
    list.add(configure)
}

/**
 * List of functions that should be applied to `OutgoingContent` before it is sent out
 *
 * Allows specifying versions, cache control, etc.
 */
val Route.staticContentConfigurations: List<OutgoingContent.() -> Unit>
    get() {
        val local = attributes.getOrNull(staticContentAspectsKey)
        val parent = parent?.staticContentConfigurations
        return when {
            local == null && parent == null -> emptyList()
            local == null -> parent!! // compiler could figure it out?
            parent == null -> local
            else -> parent + local
        }
    }
