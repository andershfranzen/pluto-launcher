package dev.pluto.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonBar
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.CheckRow
import dev.pluto.launcher.ui.overlay.LayerScaffold
import dev.pluto.launcher.ui.overlay.NoticeCard
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.rememberScreenFocus

private enum class OnboardingStep { WELCOME, GAMES, CONTROLLER, HOME }

/**
 * Steps shown for the current state. Without any apps there is nothing to sort into Games,
 * and Pluto must not ask to become the Home app before it can show and launch apps.
 */
private fun stepsFor(state: LauncherUiState): List<OnboardingStep> {
    val hasApps = state.apps.isNotEmpty()
    return buildList {
        add(OnboardingStep.WELCOME)
        if (hasApps) add(OnboardingStep.GAMES)
        add(OnboardingStep.CONTROLLER)
        if (hasApps) add(OnboardingStep.HOME)
    }
}

/** First-run: default layout preview, optional category setup, controller preview, then default-launcher chooser. */
@Composable
fun OnboardingScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val steps = stepsFor(state)
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME.name) }
    val step = OnboardingStep.valueOf(stepName).takeIf { it in steps } ?: steps.first()
    val index = steps.indexOf(step)
    val isLast = index == steps.lastIndex
    val isTop = state.session.topLayer == Layer.Onboarding
    val requestHomeRole = rememberHomeRoleRequest(onDeclined = { vm.showMessage(SettingsText.HOME_ROLE_DECLINED) })
    val gamesCategory = state.categories.firstOrNull { it.builtIn == BuiltInCategory.GAMES }

    fun goTo(target: OnboardingStep) {
        stepName = target.name
    }

    fun finish() {
        vm.completeOnboarding()
        // The layer is closed here unless completing onboarding already removed it.
        if (vm.state.value.session.topLayer == Layer.Onboarding) vm.back()
    }

    rememberScreenFocus(
        when (step) {
            OnboardingStep.GAMES -> state.apps.firstOrNull()?.let { "onboarding:game:${it.key.encode()}" } ?: "onboarding:next"
            OnboardingStep.HOME -> if (state.isDefaultHome) "onboarding:next" else "onboarding:sethome"
            else -> "onboarding:next"
        },
        refocusKey = step,
    )
    BackHandler(enabled = index > 0) { goTo(steps[index - 1]) }

    LayerScaffold(
        title = SettingsText.WELCOME_TITLE,
        idPrefix = "onboarding",
        onClose = ::finish,
        trapFocus = isTop,
        scrollable = false,
    ) {
        Text(
            SettingsText.stepOf(index + 1, steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp),
        )
        val body = Modifier.weight(1f).fillMaxWidth()
        when (step) {
            OnboardingStep.WELCOME -> StepColumn(body) { WelcomeStep(state) }
            OnboardingStep.GAMES -> GamesStep(state, vm, gamesCategory?.id, body)
            OnboardingStep.CONTROLLER -> StepColumn(body) { ControllerStep(state) }
            OnboardingStep.HOME -> StepColumn(body) { HomeStep(state, requestHomeRole) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ButtonBar(Modifier.padding(16.dp)) {
            if (index > 0) {
                PlutoButton(
                    "onboarding:back",
                    SettingsText.BACK,
                    { goTo(steps[index - 1]) },
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    style = ButtonStyle.TEXT,
                )
            }
            if (step == OnboardingStep.GAMES) {
                PlutoButton("onboarding:skip", SettingsText.SKIP, { goTo(steps[index + 1]) }, style = ButtonStyle.TEXT)
            }
            when {
                step == OnboardingStep.HOME && !state.isDefaultHome ->
                    PlutoButton("onboarding:next", SettingsText.NOT_NOW, ::finish, style = ButtonStyle.OUTLINED)
                isLast -> PlutoButton(
                    "onboarding:next",
                    if (step == OnboardingStep.HOME) SettingsText.FINISH else SettingsText.START,
                    ::finish,
                    icon = Icons.Rounded.CheckCircle,
                    style = ButtonStyle.FILLED,
                )
                else -> PlutoButton(
                    "onboarding:next",
                    SettingsText.NEXT,
                    { goTo(steps[index + 1]) },
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    style = ButtonStyle.FILLED,
                )
            }
        }
    }
}

@Composable
private fun StepColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun WelcomeStep(state: LauncherUiState) {
    StepTitle(SettingsText.WELCOME_TITLE)
    BodyText(SettingsText.WELCOME_TEXT)
    when {
        state.apps.isNotEmpty() -> {
            NoticeCard(SettingsText.appsFound(state.apps.size), icon = Icons.Rounded.Apps)
            BodyText(SettingsText.WELCOME_READY)
        }
        state.loading -> BodyText(SettingsText.LOADING_APPS)
        else -> NoticeCard(SettingsText.NO_APPS_PROBLEM, isError = true, icon = Icons.Rounded.Warning)
    }
    if (state.otherProfilesPresent) NoticeCard(SettingsText.PROFILES_NOTICE, icon = Icons.Rounded.Work)
}

/** Simple checklist of apps that writes Games membership immediately; skippable. */
@Composable
private fun GamesStep(state: LauncherUiState, vm: LauncherViewModel, gamesId: Long?, modifier: Modifier) {
    val members = gamesId?.let { state.categoryMembers[it] }.orEmpty()
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item(key = "header") {
            Column {
                StepTitle(SettingsText.STEP_GAMES_TITLE)
                BodyText(SettingsText.STEP_GAMES_TEXT)
                if (gamesId == null) {
                    NoticeCard(SettingsText.NO_GAMES_CATEGORY, icon = Icons.Rounded.Warning)
                } else {
                    BodyText(SettingsText.gamesSelected(state.apps.count { it.key in members }))
                }
            }
        }
        if (gamesId != null) {
            items(state.apps, key = { it.key.encode() }) { app ->
                CheckRow(
                    id = "onboarding:game:${app.key.encode()}",
                    label = app.label,
                    checked = app.key in members,
                    leading = { AppIcon(app, 36.dp) },
                    onCheckedChange = { vm.setCategoryMembership(gamesId, app.key, it) },
                )
            }
        }
    }
}

@Composable
private fun ControllerStep(state: LauncherUiState) {
    val controller = state.controllers.firstOrNull()
    val mapping = controller?.let { state.settings.mappingFor(it.descriptor, it.vendorId, it.productId) }
        ?: state.settings.defaultMapping
    StepTitle(SettingsText.STEP_CONTROLLER_TITLE)
    BodyText(SettingsText.STEP_CONTROLLER_TEXT)
    if (controller != null) NoticeCard(SettingsText.CONTROLLER_NOW, icon = Icons.Rounded.SportsEsports)
    Spacer(Modifier.heightIn(min = 8.dp))
    LegendRow(SettingsText.MOVE_KEYS, SettingsText.MOVE)
    ControllerAction.entries.forEach { action ->
        LegendRow(keysLabel(mapping, action) ?: SettingsText.NOT_ASSIGNED, SettingsText.actionLabel(action))
    }
}

/** One "button → action" line of the controller preview, read as a single item by screen readers. */
@Composable
private fun LegendRow(keys: String, action: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            keys,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 96.dp, max = 200.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(action, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HomeStep(state: LauncherUiState, requestHomeRole: () -> Unit) {
    StepTitle(SettingsText.STEP_HOME_TITLE)
    if (state.isDefaultHome) {
        NoticeCard(SettingsText.STEP_HOME_DONE, icon = Icons.Rounded.CheckCircle)
    } else {
        BodyText(SettingsText.STEP_HOME_TEXT)
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            PlutoButton("onboarding:sethome", SettingsText.SET_DEFAULT, requestHomeRole, icon = Icons.Rounded.Home, style = ButtonStyle.FILLED)
            val context = LocalContext.current
            PlutoButton(
                "onboarding:defaultapps",
                SettingsText.OPEN_DEFAULT_APPS,
                { openDefaultAppsSettings(context) },
                icon = Icons.Rounded.SettingsApplications,
            )
        }
    }
    BodyText(SettingsText.HOW_TO_SWITCH_BACK)
}
