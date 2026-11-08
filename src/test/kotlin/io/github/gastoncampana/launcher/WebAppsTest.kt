package io.github.gastoncampana.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAppsTest {
    private val board = Target.Shortcut("browser", "board", "Board")
    private val music = Target.Shortcut("browser", "music", "Music")
    private val packaged = Target.App("web.apk/web.Main#0")

    @Test
    fun missingWebMetadataDoesNotDiscardUnrelatedNativeAppsOrCacheTheFailure() {
        val cache = mutableMapOf<String, Boolean>()
        val failures = mutableListOf<Exception>()
        val failure = Exception("Work profile metadata unavailable")
        val native = listOf("personal#0", "work#10")
        val classified = native.map { id ->
            id to cachedWebApp(cache, id, { if (id == "work#10") throw failure else true }, failures::add)
        }
        assertEquals(listOf("personal#0" to true, "work#10" to false), classified)
        assertEquals(mapOf("personal#0" to true), cache)
        assertEquals(listOf(failure), failures)
        assertTrue(cachedWebApp(cache, "work#10", { true }, failures::add))
        assertEquals(true, cache["work#10"])
    }

    @Test
    fun verifiedNegativeWebMetadataIsCachedButRuntimeErrorsAreNot() {
        val cache = mutableMapOf<String, Boolean>()
        val failures = mutableListOf<Exception>()
        assertFalse(cachedWebApp(cache, "native#0", { false }, failures::add))
        assertFalse(cachedWebApp(cache, "native#0", { throw AssertionError("Verified cache entry must be reused") }, failures::add))
        assertFalse(cachedWebApp(cache, "locked#0", { throw IllegalStateException("User locked") }, failures::add))
        assertFalse(cache.containsKey("locked#0"))
        assertEquals(1, failures.size)
    }

    @Test
    fun failedDiscoveryKeepsTheLastListForCheckedAndRuntimeExceptions() {
        val previous = listOf(board)
        for (failure in listOf(Exception("Package disappeared"), IllegalStateException("Profile locked"))) {
            val observed = mutableListOf<Exception>()
            val result = readOrKeep(previous, { throw failure }, observed::add)
            assertEquals(previous, result)
            assertEquals(listOf(failure), observed)
        }
    }

    @Test
    fun aSuccessfulEmptyQueryClearsTheDrawerList() {
        val failures = mutableListOf<Exception>()
        assertEquals(emptyList<Target>(), readOrKeep(listOf(board), { emptyList<Target.Shortcut>() }, failures::add))
        assertTrue(failures.isEmpty())
    }

    @Test
    fun aWebShortcutDraggedFromTheDrawerDocksItsActualTarget() {
        val item = Item.Icon(board, "Board", Cell(0f, 0f))
        val before = Layout(dock = listOf(listOf(DockIcon(up = music)) + List(4) { DockIcon() }))
        val after = before.withDockedIcon(null, item, 0, 0)
        assertEquals(DockIcon(tap = board, up = music), after.dock[0][0])
        assertEquals(before.desktop, after.desktop)
    }

    @Test
    fun dockingFromHomeRemovesOnlyTheDraggedItemAndNeverOverwritesATap() {
        val item = Item.Icon(packaged, "Packaged", Cell(0f, 0f))
        val widget = Item.Widget("clock/Provider", 42, Cell(1f, 0f))
        val before = Layout(desktop = listOf(listOf(item, widget)))
        val after = before.withDockedIcon(0, item, 0, 0)
        assertEquals(listOf(widget), after.desktop[0])
        assertEquals(packaged, after.dock[0][0].tap)
        assertEquals(after, after.withDockedIcon(null, Item.Icon(board, "Board", Cell(0f, 0f)), 0, 0))
        assertEquals(before, before.withDockedIcon(0, item, 4, 0))
        assertEquals(before, before.withDockedIcon(0, item, 0, before.dockSize))
    }

    @Test
    fun packagedWebAppsNeedBothWebMetadataUrls() {
        assertTrue(isPackagedWebApp("https://board.example.test/", "https://board.example.test/manifest.json"))
        assertTrue(isPackagedWebApp("http://board.example.test/", "http://board.example.test/manifest.json"))
        assertFalse(isPackagedWebApp(null, "https://board.example.test/manifest.json"))
        assertFalse(isPackagedWebApp("https://board.example.test/", null))
        assertFalse(isPackagedWebApp("javascript:alert(1)", "https://board.example.test/manifest.json"))
        assertFalse(isPackagedWebApp("https://", "https://board.example.test/manifest.json"))
        assertFalse(isPackagedWebApp("not a URL", "https://board.example.test/manifest.json"))
    }

    @Test
    fun browserWebShortcutsExcludeOrdinaryAppActionsAndDisabledEntries() {
        assertTrue(isBrowserWebShortcut(browser = true, pinned = true, dynamic = false, manifest = false, enabled = true))
        assertFalse(isBrowserWebShortcut(browser = false, pinned = true, dynamic = false, manifest = false, enabled = true))
        assertFalse(isBrowserWebShortcut(browser = true, pinned = false, dynamic = false, manifest = false, enabled = true))
        assertFalse(isBrowserWebShortcut(browser = true, pinned = true, dynamic = true, manifest = false, enabled = true))
        assertFalse(isBrowserWebShortcut(browser = true, pinned = true, dynamic = false, manifest = true, enabled = true))
        assertFalse(isBrowserWebShortcut(browser = true, pinned = true, dynamic = false, manifest = false, enabled = false))
    }

    @Test
    fun drawerIdentitiesKeepWebAppsFromOneBrowserSeparate() {
        assertNotEquals(board.entryId(), music.entryId())
        assertNotEquals(board.entryId(), Target.Shortcut("other.browser", "board", "Board").entryId())
        assertEquals(board.entryId(), board.copy(label = "Renamed").entryId())
        assertEquals(packaged.id, packaged.entryId())
        assertNotEquals(Target.Shortcut("a", "bc", "").entryId(), Target.Shortcut("ab", "c", "").entryId())
    }

    @Test
    fun drawerLookupNeverFallsBackFromOneWebShortcutToAnother() {
        val entries = listOf<Target>(packaged, Target.App("browser/browser.Main#0"), board, music)
        val find = drawerFinder(entries) { it }
        assertEquals(board, find(board.entryId()))
        assertEquals(music, find(music.entryId()))
        assertNull(find(Target.Shortcut("browser", "missing", "Missing").entryId()))
        assertEquals(packaged, find("web.apk/web.Alias#0"))
        assertNull(find("web.apk/web.Main#10"))
    }

    @Test
    fun webShortcutsCanBeTabbedAndHiddenIndependently() {
        val entries = listOf<Target>(board, music, packaged)
        val find = drawerFinder(entries) { it }
        val chosen = Tab("Board", setOf(board.entryId()), hideApps = true)
        val all = Tab("Apps")
        assertEquals(listOf(board), tabApps(chosen, listOf(chosen, all), entries, find))
        assertEquals(listOf(music, packaged), tabApps(all, listOf(chosen, all), entries, find))
        assertEquals(listOf(music, packaged), tabApps(all, listOf(all), entries, find, setOf(board.entryId())))
    }

    @Test
    fun firstDiscoveryAddsExistingWebAppsWithoutMovingSavedItems() {
        val widget = Item.Widget("clock/Provider", 42, Cell(0f, 0f, 2f, 2f))
        val before = Layout(desktop = listOf(listOf(widget)))
        val result = before.withWebApps(listOf(board, music, packaged), emptySet())
        assertEquals(widget, result.layout.desktop[0][0])
        assertEquals(setOf(board, music, packaged), result.layout.desktop.flatten().filterIsInstance<Item.Icon>().map { it.target }.toSet())
        assertEquals(3, result.known.size)
        val items = result.layout.desktop.flatten()
        items.forEachIndexed { index, item -> assertFalse(items.drop(index + 1).any { it.cell.overlaps(item.cell) }) }
    }

    @Test
    fun theFirstDiscoveryOnlyRecordsTheWebAppsAlreadyInstalled() {
        val first = Layout().withWebApps(listOf(board, packaged), null)
        assertEquals(Layout(), first.layout)
        assertEquals(2, first.known.size)
        // One installed afterwards goes on Home, once.
        val next = first.layout.withWebApps(listOf(board, packaged, music), first.known)
        assertEquals(listOf(music), next.layout.desktop.flatten().filterIsInstance<Item.Icon>().map { it.target })
        assertEquals(next, next.layout.withWebApps(listOf(board, packaged, music), next.known))
    }

    @Test
    fun discoveryIsIdempotentAndDoesNotUndoIntentionalRemoval() {
        val first = Layout().withWebApps(listOf(board, board, music), emptySet())
        assertEquals(2, first.layout.desktop.flatten().filterIsInstance<Item.Icon>().size)
        val second = first.layout.withWebApps(listOf(board, music), first.known)
        assertEquals(first, second)
        val removed = first.layout.copy(desktop = first.layout.desktop.map { page -> page.filterNot { it is Item.Icon && it.target == board } })
        assertEquals(removed, removed.withWebApps(listOf(board, music), first.known).layout)
    }

    @Test
    fun aTemporaryEmptyDiscoveryDoesNotForgetIntentionalRemoval() {
        val first = Layout().withWebApps(listOf(board), emptySet())
        assertEquals(1, first.known.size)
        val removed = Layout()
        val empty = removed.withWebApps(emptyList(), first.known)
        assertEquals(first.known, empty.known)
        assertEquals(removed, empty.layout.withWebApps(listOf(board), empty.known).layout)
    }

    @Test
    fun discoveryPreservesDockedTargetsAndRenamedShortcuts() {
        val renamed = board.copy(label = "My board")
        val desktop = listOf(Item.Icon(renamed, "Custom title", Cell(3f, 2f)))
        val before = Layout(desktop = listOf(desktop), dock = listOf(listOf(DockIcon(up = music))))
        assertEquals(before, before.withWebApps(listOf(board, music), emptySet()).layout)
    }

    @Test
    fun aChangedNativeAliasDoesNotDuplicateOrReinsertAWebApp() {
        val alias = Target.App("web.apk/web.Alias#0")
        val before = Layout(desktop = listOf(listOf(Item.Icon(alias, "Board", Cell(4f, 4f)))))
        val first = before.withWebApps(listOf(packaged), emptySet())
        assertEquals(1, first.known.size)
        assertEquals(before, first.layout)
        assertEquals(Layout(), Layout().withWebApps(listOf(alias), first.known).layout)
    }

    @Test
    fun hiddenWebAppsAreNotAutomaticallyPlacedOnHome() {
        val hidden = Layout(hidden = setOf(board.entryId(), "web.apk/web.Alias#0"))
        val result = hidden.withWebApps(listOf(board, packaged, music), emptySet())
        assertEquals(listOf(music), result.layout.desktop.flatten().filterIsInstance<Item.Icon>().map { it.target })
        assertEquals(3, result.known.size)
    }

    @Test
    fun fullPagesGetANewPageInsteadOfRejectingTheInstalledWebApp() {
        val before = Layout(columns = 2, rows = 2, desktop = listOf(listOf(Item.Widget("clock/Provider", 42, Cell(0f, 0f, 2f, 2f)))))
        val after = before.withWebApps(listOf(board), emptySet()).layout
        assertEquals(before.desktop[0], after.desktop[0])
        assertEquals(2, after.desktop.size)
        assertEquals(board, (after.desktop[1].single() as Item.Icon).target)
    }

    @Test
    fun automaticPlacementPrefersTheMainPageAndPreservesTheDefault() {
        val before = Layout(defaultPage = 1, desktop = listOf(emptyList(), emptyList()))
        val after = before.withWebApps(listOf(board), emptySet()).layout
        assertTrue(after.desktop[0].isEmpty())
        assertEquals(board, (after.desktop[1].single() as Item.Icon).target)
        assertEquals(1, after.defaultPage)
    }

    @Test
    fun explicitPinIsIdempotentAndCanRestoreAnIntentionallyRemovedIcon() {
        val once = Layout().withPinnedIcon(board, "Board")
        assertEquals(listOf(board), once.desktop.flatten().filterIsInstance<Item.Icon>().map { it.target })
        assertEquals(once, once.withPinnedIcon(board.copy(label = "Renamed"), "Renamed"))
        assertEquals(board, (Layout().withPinnedIcon(board, "Board").desktop[0].single() as Item.Icon).target)
    }

    @Test
    fun webEntriesAndTheirTabMembershipSurviveLayoutBackup() {
        val layout = Layout(tabs = listOf(Tab("Web", setOf(board.entryId(), music.entryId()))), hidden = setOf(music.entryId()))
            .withPinnedIcon(board, "Board")
        assertEquals(listOf(board), layout.desktop.flatten().filterIsInstance<Item.Icon>().map { it.target })
        assertEquals(layout, parseLayout(layout.toJson()))
    }
}
