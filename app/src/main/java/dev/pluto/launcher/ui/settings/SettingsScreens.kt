package dev.pluto.launcher.ui.settings

import androidx.compose.runtime.Composable
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel

/** Appearance, layout & rotation, history, organisation links, controller, default launcher, about/limitations. */
@Composable
fun SettingsScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** Lists hidden apps with Restore. Explains hiding is organisation, not security. */
@Composable
fun HiddenAppsScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** Add, rename, delete (not ALL), reorder categories. */
@Composable
fun CategoriesScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** Connected controllers, live input diagnostics, dead zone/repeat tuning, button remapping. */
@Composable
fun ControllerSettingsScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** First-run: default layout preview, optional category setup, controller preview, then default-launcher chooser. */
@Composable
fun OnboardingScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()
