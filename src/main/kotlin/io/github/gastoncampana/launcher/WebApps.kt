package io.github.gastoncampana.launcher

import java.net.URI

fun cachedWebApp(cache: MutableMap<String, Boolean>, id: String, read: () -> Boolean, onFailure: (Exception) -> Unit): Boolean {
    cache[id]?.let { return it }
    // Optional classification must not hide native apps or turn a locked profile into a cached negative.
    return readOrKeep<Boolean?>(null, { read().also { cache[id] = it } }, onFailure) ?: false
}

fun <T> readOrKeep(previous: T, read: () -> T, onFailure: (Exception) -> Unit): T = try {
    read()
} catch (failure: Exception) {
    onFailure(failure)
    previous
}

fun Layout.withDockedIcon(from: Int?, item: Item.Icon, page: Int, slot: Int): Layout {
    if (page !in dock.indices || slot !in 0 until dockSize || slot !in dock[page].indices || dock[page][slot].tap != null) return this
    if (from != null && item !in desktop.getOrNull(from).orEmpty()) return this
    return copy(
        dock = dock.replaced(page) { it.replaced(slot) { icon -> icon.copy(tap = item.target) } },
        desktop = if (from == null) desktop else desktop.replaced(from) { it - item },
    )
}

fun isPackagedWebApp(startUrl: String?, manifestUrl: String?): Boolean {
    fun isWebUrl(value: String?): Boolean = try {
        value?.let { URI(it) }?.let { it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() } == true
    } catch (_: java.net.URISyntaxException) {
        false
    }
    return isWebUrl(startUrl) && isWebUrl(manifestUrl)
}

// Launchers cannot read shortcut intents; browser-owned pinned-only entries are their installed web shortcuts.
fun isBrowserWebShortcut(browser: Boolean, pinned: Boolean, dynamic: Boolean, manifest: Boolean, enabled: Boolean): Boolean =
    browser && pinned && !dynamic && !manifest && enabled

/** Native ids keep their existing format; shortcuts have an unambiguous namespace independent of their label. */
fun Target.entryId(): String = when (this) {
    is Target.App -> id
    is Target.Shortcut -> "shortcut:${pkg.length}:$pkg$id"
}

fun <T> drawerFinder(entries: List<T>, target: (T) -> Target): (String) -> T? {
    val byId = entries.associateBy { target(it).entryId() }
    val native = finder(entries.filter { target(it) is Target.App }) { (target(it) as Target.App).id }
    return { byId[it] ?: native(it) }
}

private fun Target.installKey(): String = when (this) {
    is Target.App -> packageKey(id)
    is Target.Shortcut -> entryId()
}

data class WebAppSync(val layout: Layout, val known: Set<String>)

/** An explicit pin can restore a removed icon, but never duplicates an existing desktop or dock target. */
fun Layout.withPinnedIcon(target: Target, label: String): Layout {
    val key = target.installKey()
    val existing = desktop.flatten().filterIsInstance<Item.Icon>().map { it.target } +
        dock.flatten().flatMap { listOfNotNull(it.tap, it.up, it.down) }
    if (existing.any { it.installKey() == key }) return this
    return placed(defaultPage, Item.Icon(target, label, Cell(0f, 0f)))
}

/** Known identities outlive desktop removal and temporary discovery gaps, so a refresh cannot undo a user's choice. */
fun Layout.withWebApps(targets: List<Target>, known: Set<String>, label: (Target) -> String = {
    when (it) {
        is Target.App -> it.id
        is Target.Shortcut -> it.label
    }
}): WebAppSync {
    var next = this
    val discovered = known.toMutableSet()
    for (target in targets) {
        if (!discovered.add(target.installKey())) continue
        val hidden = when (target) {
            is Target.App -> hidden.any { !it.startsWith("shortcut:") && packageKey(it) == packageKey(target.id) }
            is Target.Shortcut -> target.entryId() in hidden
        }
        if (!hidden) next = next.withPinnedIcon(target, label(target))
    }
    return WebAppSync(next, discovered)
}
