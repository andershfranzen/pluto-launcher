package dev.pluto.launcher.input

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Where a movement came from; used for duplicate suppression. */
enum class MoveSource { KEY, HAT, STICK }

/** Semantic controller actions, after button mapping. */
sealed interface LauncherAction {
    data class Move(val direction: Direction) : LauncherAction
    data object Confirm : LauncherAction
    data object Back : LauncherAction
    data object Actions : LauncherAction
    data object Search : LauncherAction
    data object Settings : LauncherAction
    data object PrevCategory : LauncherAction
    data object NextCategory : LauncherAction
}
