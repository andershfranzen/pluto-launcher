package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSuggestionsTest {
    private fun app(pkg: String, label: String) = AppEntry(AppKey("$pkg/.Main", 0), label)

    private val calc = app("com.oneplus.calculator", "Calculator")
    private val docs = app("com.example.docs", "Docs")
    private val balatro = app("com.playstack.balatro", "Balatro")
    private val chat = app("com.example.chat", "Chat")
    private val apps = listOf(balatro, calc, chat, docs)

    private val traits = mapOf(
        balatro.packageName to AppSuggestions.Traits(AppSuggestions.CATEGORY_GAME, isGame = false),
        docs.packageName to AppSuggestions.Traits(AppSuggestions.CATEGORY_PRODUCTIVITY, isGame = false),
        chat.packageName to AppSuggestions.Traits(4, isGame = false),
    )

    @Test
    fun toolsComeFromCategoryOrUtilityNames() {
        assertEquals(listOf(calc, docs), AppSuggestions.suggest(BuiltInCategory.TOOLS, apps) { traits[it] })
    }

    @Test
    fun gamesComeFromTheGameCategoryOrFlag() {
        val flagged = app("com.example.old", "Old game")
        val withFlag = traits + (flagged.packageName to AppSuggestions.Traits(-1, isGame = true))
        assertEquals(listOf(balatro, flagged), AppSuggestions.suggest(BuiltInCategory.GAMES, apps + flagged) { withFlag[it] })
    }

    @Test
    fun aGameNamedLikeAToolIsNotSuggestedAsATool() {
        val clockGame = app("com.example.clockwork", "Clockwork Quest")
        val t = mapOf(clockGame.packageName to AppSuggestions.Traits(AppSuggestions.CATEGORY_GAME, false))
        assertTrue(AppSuggestions.suggest(BuiltInCategory.TOOLS, listOf(clockGame)) { t[it] }.isEmpty())
    }

    @Test
    fun userCategoriesAndAllAppsGetNoSuggestions() {
        assertTrue(AppSuggestions.suggest(null, apps) { traits[it] }.isEmpty())
        assertTrue(AppSuggestions.suggest(BuiltInCategory.ALL, apps) { traits[it] }.isEmpty())
    }
}
