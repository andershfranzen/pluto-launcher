package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.ReorderOp
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.ThemePreference
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.overlay.ActionRow
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonBar
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.ChoiceGroup
import dev.pluto.launcher.ui.overlay.ConfirmDialog
import dev.pluto.launcher.ui.overlay.LayerScaffold
import dev.pluto.launcher.ui.overlay.MAX_NAME_LENGTH
import dev.pluto.launcher.ui.overlay.NoticeCard
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.SectionHeader
import dev.pluto.launcher.ui.overlay.StepperRow
import dev.pluto.launcher.ui.overlay.SwitchRow
import dev.pluto.launcher.ui.overlay.TextInputDialog
import dev.pluto.launcher.ui.overlay.percent
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import dev.pluto.launcher.ui.overlay.stepped

private const val SCRIM_STEP = 0.05f
private const val ICON_STEP = 0.1f
private const val TEXT_STEP = 0.05f

private enum class SettingsDialog { DISABLE_HISTORY, CLEAR_HISTORY }

/** Appearance, layout & rotation, history, organisation links, controller, default launcher, about/limitations. */
@Composable
fun SettingsScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val settings = state.settings
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var dialogOpener by rememberSaveable { mutableStateOf<String?>(null) }
    val screenFocus = rememberScreenFocus("settings:theme:${ThemePreference.SYSTEM}")
    val requestHomeRole = rememberHomeRoleRequest()
    val versionName = rememberVersionName()
    val isTop = state.session.topLayer == Layer.Settings

    fun open(which: SettingsDialog, opener: String) {
        dialogOpener = opener
        dialog = which
    }

    fun close() {
        dialog = null
        screenFocus.returnFrom(dialogOpener)
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) = vm.updateSettings(transform)

    LayerScaffold(
        title = SettingsText.SETTINGS,
        idPrefix = "settings",
        onClose = { vm.back() },
        trapFocus = isTop && dialog == null,
        overlay = {
            when (dialog) {
                SettingsDialog.DISABLE_HISTORY -> ConfirmDialog(
                    idPrefix = "settings:history:off",
                    title = SettingsText.DISABLE_HISTORY_TITLE,
                    text = SettingsText.DISABLE_HISTORY_TEXT,
                    confirmLabel = SettingsText.TURN_OFF,
                    destructive = true,
                    onConfirm = {
                        vm.setHistoryEnabled(false)
                        close()
                    },
                    onDismiss = ::close,
                )
                SettingsDialog.CLEAR_HISTORY -> ConfirmDialog(
                    idPrefix = "settings:history:clear",
                    title = SettingsText.CLEAR_HISTORY_TITLE,
                    text = SettingsText.CLEAR_HISTORY_TEXT,
                    confirmLabel = SettingsText.CLEAR,
                    destructive = true,
                    onConfirm = {
                        vm.clearHistory()
                        close()
                    },
                    onDismiss = ::close,
                )
                null -> Unit
            }
        },
    ) {
        // --- Appearance ---
        SectionHeader(SettingsText.APPEARANCE)
        ChoiceGroup(
            idPrefix = "settings:theme",
            label = SettingsText.THEME,
            options = listOf(
                ThemePreference.SYSTEM to SettingsText.THEME_SYSTEM,
                ThemePreference.LIGHT to SettingsText.THEME_LIGHT,
                ThemePreference.DARK to SettingsText.THEME_DARK,
            ),
            selected = settings.theme,
            onSelect = { theme -> update { it.copy(theme = theme) } },
        )
        StepperRow(
            idPrefix = "settings:scrim",
            label = SettingsText.SCRIM,
            supporting = SettingsText.SCRIM_HELP,
            valueText = percent(settings.scrimAlpha),
            onDecrease = stepped(settings.scrimAlpha, SCRIM_STEP, 0f..LauncherSettings.SCRIM_MAX, -1)
                ?.let { v -> { update { it.copy(scrimAlpha = v) } } },
            onIncrease = stepped(settings.scrimAlpha, SCRIM_STEP, 0f..LauncherSettings.SCRIM_MAX, 1)
                ?.let { v -> { update { it.copy(scrimAlpha = v) } } },
        )
        StepperRow(
            idPrefix = "settings:icons",
            label = SettingsText.ICON_SIZE,
            valueText = percent(settings.iconScale),
            onDecrease = stepped(settings.iconScale, ICON_STEP, LauncherSettings.ICON_SCALE_RANGE, -1)
                ?.let { v -> { update { it.copy(iconScale = v) } } },
            onIncrease = stepped(settings.iconScale, ICON_STEP, LauncherSettings.ICON_SCALE_RANGE, 1)
                ?.let { v -> { update { it.copy(iconScale = v) } } },
        )
        StepperRow(
            idPrefix = "settings:text",
            label = SettingsText.TEXT_SIZE,
            supporting = SettingsText.TEXT_SIZE_HELP,
            valueText = percent(settings.textScale),
            onDecrease = stepped(settings.textScale, TEXT_STEP, LauncherSettings.TEXT_SCALE_RANGE, -1)
                ?.let { v -> { update { it.copy(textScale = v) } } },
            onIncrease = stepped(settings.textScale, TEXT_STEP, LauncherSettings.TEXT_SCALE_RANGE, 1)
                ?.let { v -> { update { it.copy(textScale = v) } } },
        )
        SwitchRow(
            id = "settings:motion",
            label = SettingsText.REDUCE_MOTION,
            supporting = SettingsText.REDUCE_MOTION_HELP,
            checked = settings.reducedMotion,
            onCheckedChange = { on -> update { it.copy(reducedMotion = on) } },
        )

        // --- Layout & rotation ---
        SectionHeader(SettingsText.LAYOUT)
        ChoiceGroup(
            idPrefix = "settings:rotation",
            label = SettingsText.ROTATION,
            supporting = SettingsText.ROTATION_HELP,
            options = listOf(
                RotationPreference.FOLLOW_SYSTEM to SettingsText.ROTATION_FOLLOW,
                RotationPreference.PORTRAIT to SettingsText.ROTATION_PORTRAIT,
                RotationPreference.LANDSCAPE to SettingsText.ROTATION_LANDSCAPE,
                RotationPreference.LANDSCAPE_WHEN_CONTROLLER to SettingsText.ROTATION_CONTROLLER,
            ),
            selected = settings.rotation,
            onSelect = { rotation -> update { it.copy(rotation = rotation) } },
        )
        ChoiceGroup(
            idPrefix = "settings:handheld",
            label = SettingsText.HANDHELD,
            supporting = SettingsText.HANDHELD_HELP,
            options = listOf(
                HandheldAppearance.AUTOMATIC to SettingsText.HANDHELD_AUTO,
                HandheldAppearance.ALWAYS to SettingsText.HANDHELD_ALWAYS,
                HandheldAppearance.NEVER to SettingsText.HANDHELD_NEVER,
            ),
            selected = settings.handheldAppearance,
            onSelect = { appearance -> update { it.copy(handheldAppearance = appearance) } },
        )

        // --- Organisation ---
        SectionHeader(SettingsText.ORGANISATION)
        ActionRow(
            id = "settings:categories",
            label = SettingsText.CATEGORIES,
            icon = Icons.Rounded.Category,
            supporting = SettingsText.categoryCount(state.categories.size),
            onClick = { vm.openLayer(Layer.Categories) },
        )
        ActionRow(
            id = "settings:hidden",
            label = SettingsText.HIDDEN_APPS,
            icon = Icons.Rounded.VisibilityOff,
            supporting = SettingsText.hiddenCount(state.allApps.count { it.key in state.hidden }),
            onClick = { vm.openLayer(Layer.HiddenApps) },
        )
        ActionRow(
            id = "settings:edit",
            label = SettingsText.EDIT_HOME,
            icon = Icons.Rounded.Edit,
            supporting = SettingsText.EDIT_HOME_HELP,
            onClick = { vm.openLayer(Layer.Edit) },
        )

        // --- Recent launches ---
        SectionHeader(SettingsText.HISTORY)
        SwitchRow(
            id = "settings:history",
            label = SettingsText.KEEP_HISTORY,
            icon = Icons.Rounded.History,
            supporting = if (settings.historyEnabled) SettingsText.KEEP_HISTORY_ON else SettingsText.KEEP_HISTORY_OFF,
            checked = settings.historyEnabled,
            onCheckedChange = { on ->
                if (on) vm.setHistoryEnabled(true) else open(SettingsDialog.DISABLE_HISTORY, "settings:history")
            },
        )
        ActionRow(
            id = "settings:history:clear",
            label = SettingsText.CLEAR_HISTORY,
            icon = Icons.Rounded.DeleteSweep,
            supporting = SettingsText.historyCount(state.recents.size),
            onClick = { open(SettingsDialog.CLEAR_HISTORY, "settings:history:clear") },
        )

        // --- Controller ---
        SectionHeader(SettingsText.CONTROLLER_TITLE)
        ActionRow(
            id = "settings:controller",
            label = SettingsText.CONTROLLER,
            icon = Icons.Rounded.SportsEsports,
            supporting = if (state.controllerConnected) {
                SettingsText.controllerConnected(state.controllers.size)
            } else {
                SettingsText.CONTROLLER_HELP_NONE
            },
            onClick = { vm.openLayer(Layer.ControllerSettings) },
        )

        // --- Default launcher ---
        SectionHeader(SettingsText.DEFAULT_LAUNCHER)
        NoticeCard(if (state.isDefaultHome) SettingsText.IS_DEFAULT else SettingsText.NOT_DEFAULT, icon = Icons.Rounded.Home)
        BodyText(SettingsText.HOW_TO_SWITCH_BACK)
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.isDefaultHome) {
                PlutoButton("settings:default:set", SettingsText.SET_DEFAULT, requestHomeRole, icon = Icons.Rounded.Home, style = ButtonStyle.FILLED)
            }
            val context = LocalContext.current
            PlutoButton(
                "settings:default:open",
                SettingsText.OPEN_DEFAULT_APPS,
                { openDefaultAppsSettings(context) },
                icon = Icons.Rounded.SettingsApplications,
            )
        }

        if (state.otherProfilesPresent) {
            NoticeCard(SettingsText.PROFILES_NOTICE, icon = Icons.Rounded.Work)
        }

        // --- About ---
        SectionHeader(SettingsText.ABOUT)
        BodyText(SettingsText.version(versionName))
        BodyText(SettingsText.LICENSE)
        BodyText(SettingsText.PRIVACY)
        BodyText(SettingsText.PROFILES_NOTICE)
        BodyText(SettingsText.LIMITATIONS)
        Spacer(Modifier.heightIn(min = 16.dp))
    }
}

/** Lists hidden apps with Restore. Explains hiding is organisation, not security. */
@Composable
fun HiddenAppsScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val hiddenApps = state.allApps.filter { it.key in state.hidden }
    val isTop = state.session.topLayer == Layer.HiddenApps
    val restoreId = { index: Int -> hiddenApps.getOrNull(index)?.let { "hidden:restore:${it.key.encode()}" } }
    val screenFocus = rememberScreenFocus(restoreId(0) ?: "hidden:close")

    LayerScaffold(
        title = SettingsText.HIDDEN_TITLE,
        idPrefix = "hidden",
        onClose = { vm.back() },
        trapFocus = isTop,
    ) {
        NoticeCard(SettingsText.HIDDEN_EXPLANATION, icon = Icons.Rounded.VisibilityOff)
        if (hiddenApps.isEmpty()) {
            BodyText(SettingsText.HIDDEN_EMPTY)
        } else {
            if (hiddenApps.size > 1) {
                ButtonBar(Modifier.padding(horizontal = 8.dp)) {
                    PlutoButton(
                        "hidden:restoreall",
                        SettingsText.RESTORE_ALL,
                        {
                            hiddenApps.forEach { vm.unhide(it.key) }
                            screenFocus.focus("hidden:close")
                        },
                        icon = Icons.Rounded.Visibility,
                        style = ButtonStyle.TEXT,
                    )
                }
            }
            hiddenApps.forEachIndexed { index, app ->
                key(app.key.encode()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        PlutoButton(
                            id = "hidden:restore:${app.key.encode()}",
                            label = SettingsText.RESTORE,
                            icon = Icons.Rounded.Visibility,
                            onClick = {
                                vm.unhide(app.key)
                                screenFocus.focus(restoreId(index + 1) ?: restoreId(index - 1) ?: "hidden:close")
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class CategoryDialogKind { ADD, RENAME, DELETE }

/** Add, rename, delete (not ALL), reorder categories. */
@Composable
fun CategoriesScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val categories = state.categories.sortedBy { it.position }
    val isTop = state.session.topLayer == Layer.Categories
    var dialogKind by rememberSaveable { mutableStateOf<CategoryDialogKind?>(null) }
    var dialogCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialogOpener by rememberSaveable { mutableStateOf<String?>(null) }
    val screenFocus = rememberScreenFocus("categories:add")

    fun open(kind: CategoryDialogKind, categoryId: Long?, opener: String) {
        dialogCategoryId = categoryId
        dialogOpener = opener
        dialogKind = kind
    }

    fun close(focusId: String? = dialogOpener) {
        dialogKind = null
        screenFocus.returnFrom(focusId)
    }

    LayerScaffold(
        title = SettingsText.CATEGORIES_TITLE,
        idPrefix = "categories",
        onClose = { vm.back() },
        trapFocus = isTop && dialogKind == null,
        overlay = {
            val category = categories.firstOrNull { it.id == dialogCategoryId }
            if (dialogKind != null && dialogKind != CategoryDialogKind.ADD && category == null) {
                // The category vanished while its dialog was open; close after composition.
                LaunchedEffect(Unit) { close() }
            }
            when (dialogKind) {
                CategoryDialogKind.ADD -> TextInputDialog(
                    idPrefix = "categories:new",
                    title = SettingsText.NEW_CATEGORY,
                    fieldLabel = SettingsText.CATEGORY_NAME,
                    initial = "",
                    confirmLabel = SettingsText.ADD,
                    onConfirm = { name ->
                        vm.addCategory(name.take(MAX_NAME_LENGTH))
                        close()
                    },
                    onDismiss = { close() },
                )
                CategoryDialogKind.RENAME -> if (category != null) {
                    TextInputDialog(
                        idPrefix = "categories:rename",
                        title = SettingsText.RENAME_CATEGORY,
                        fieldLabel = SettingsText.CATEGORY_NAME,
                        initial = category.name,
                        confirmLabel = SettingsText.SAVE,
                        onConfirm = { name ->
                            vm.renameCategory(category.id, name)
                            close()
                        },
                        onDismiss = { close() },
                    )
                }
                CategoryDialogKind.DELETE -> if (category != null && !category.isAll) {
                    ConfirmDialog(
                        idPrefix = "categories:delete",
                        title = SettingsText.deleteCategoryTitle(category.name),
                        text = SettingsText.DELETE_CATEGORY_TEXT,
                        confirmLabel = SettingsText.DELETE,
                        destructive = true,
                        onConfirm = {
                            vm.deleteCategory(category.id)
                            val index = categories.indexOf(category)
                            val neighbour = categories.getOrNull(index + 1) ?: categories.getOrNull(index - 1)
                            close(neighbour?.let { "categories:${it.id}:rename" } ?: "categories:add")
                        },
                        onDismiss = { close() },
                    )
                }
                null -> Unit
            }
        },
    ) {
        BodyText(SettingsText.CATEGORIES_HELP)
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            PlutoButton(
                "categories:add",
                SettingsText.ADD_CATEGORY,
                { open(CategoryDialogKind.ADD, null, "categories:add") },
                icon = Icons.Rounded.Add,
                style = ButtonStyle.FILLED,
            )
        }
        categories.forEachIndexed { index, category ->
            key(category.id) {
                val count = if (category.isAll) state.apps.size else state.categoryMembers[category.id].orEmpty().size
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text(
                        if (category.isAll) SettingsText.ALL_CATEGORY_NOTE else SettingsText.memberCount(count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    ButtonBar(
                        Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp),
                        arrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val prefix = "categories:${category.id}"
                        PlutoButton(
                            "$prefix:rename",
                            SettingsText.RENAME,
                            { open(CategoryDialogKind.RENAME, category.id, "$prefix:rename") },
                            icon = Icons.Rounded.DriveFileRenameOutline,
                        )
                        PlutoButton(
                            "$prefix:up",
                            SettingsText.MOVE_UP,
                            { vm.moveCategory(category.id, ReorderOp.Up) },
                            icon = Icons.Rounded.ArrowUpward,
                            enabled = index > 0,
                        )
                        PlutoButton(
                            "$prefix:down",
                            SettingsText.MOVE_DOWN,
                            { vm.moveCategory(category.id, ReorderOp.Down) },
                            icon = Icons.Rounded.ArrowDownward,
                            enabled = index < categories.lastIndex,
                        )
                        if (!category.isAll) {
                            PlutoButton(
                                "$prefix:delete",
                                SettingsText.DELETE,
                                { open(CategoryDialogKind.DELETE, category.id, "$prefix:delete") },
                                icon = Icons.Rounded.Delete,
                                destructive = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
