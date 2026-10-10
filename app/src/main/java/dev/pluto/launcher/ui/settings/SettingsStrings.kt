package dev.pluto.launcher.ui.settings

import dev.pluto.launcher.data.prefs.ControllerAction

/** Plain-English texts for settings, management screens and onboarding (inline for 0.1). */
internal object SettingsText {
    // Settings
    const val SETTINGS = "Settings"
    const val APPEARANCE = "Appearance"
    const val THEME = "Theme"
    const val THEME_SYSTEM = "System"
    const val THEME_LIGHT = "Light"
    const val THEME_DARK = "Dark"
    const val SCRIM = "Wallpaper contrast"
    const val SCRIM_HELP = "Darkens (or lightens) the wallpaper so labels stay readable."
    const val ICON_SIZE = "Icon size"
    const val TEXT_SIZE = "Text size"
    const val TEXT_SIZE_HELP = "Applied on top of the Android font size."
    const val REDUCE_MOTION = "Reduce motion"
    const val REDUCE_MOTION_HELP = "Skip non-essential animations."

    const val LAYOUT = "Layout & rotation"
    const val ROTATION = "Rotation"
    const val ROTATION_HELP = "Applies to the launcher only. Apps you open keep their own orientation."
    const val ROTATION_FOLLOW = "Follow system"
    const val ROTATION_PORTRAIT = "Portrait"
    const val ROTATION_LANDSCAPE = "Landscape"
    const val ROTATION_CONTROLLER = "Landscape when controller connected"
    const val HANDHELD = "Handheld appearance"
    const val HANDHELD_HELP = "Phone mode is always used in portrait. In landscape, Handheld shows a controller-friendly " +
        "layout with category tabs and button hints."
    const val HANDHELD_AUTO = "Automatic"
    const val HANDHELD_ALWAYS = "Always in landscape"
    const val HANDHELD_NEVER = "Never"

    const val ORGANISATION = "Organisation"
    const val CATEGORIES = "Categories…"
    const val HIDDEN_APPS = "Hidden apps…"
    const val EDIT_HOME = "Edit home…"
    const val EDIT_HOME_HELP = "Reorder favourites, dock and folders"
    fun hiddenCount(n: Int) = if (n == 0) "None hidden" else if (n == 1) "1 app hidden" else "$n apps hidden"
    fun categoryCount(n: Int) = if (n == 1) "1 category" else "$n categories"

    const val HISTORY = "Recent launches"
    const val KEEP_HISTORY = "Keep history"
    const val KEEP_HISTORY_ON = "Remembers the last 12 apps opened from Pluto. Stored only on this device."
    const val KEEP_HISTORY_OFF = "Off. Nothing is recorded."
    const val DISABLE_HISTORY_TITLE = "Turn off history?"
    const val DISABLE_HISTORY_TEXT = "Stored history will be deleted and new launches will not be recorded."
    const val TURN_OFF = "Turn off"
    const val CLEAR_HISTORY = "Clear history"
    const val CLEAR_HISTORY_TITLE = "Clear recent launches?"
    const val CLEAR_HISTORY_TEXT = "The Recent launches row will be emptied. Apps are not affected."
    const val CLEAR = "Clear"
    fun historyCount(n: Int) = if (n == 0) "Empty" else if (n == 1) "1 app" else "$n apps"

    const val CONTROLLER = "Controller…"
    const val CONTROLLER_HELP_NONE = "No controller connected · mapping, stick tuning, diagnostics"
    fun controllerConnected(n: Int) = if (n == 1) "1 controller connected" else "$n controllers connected"

    const val DEFAULT_LAUNCHER = "Default launcher"
    const val IS_DEFAULT = "Pluto is your Home app."
    const val NOT_DEFAULT = "Pluto is not your Home app yet."
    const val SET_DEFAULT = "Set Pluto as Home app"
    const val HOW_TO_SWITCH_BACK = "How to switch back: open Android Settings ▸ Apps ▸ Default apps ▸ Home app " +
        "and choose your previous launcher."
    const val OPEN_DEFAULT_APPS = "Open Default apps settings"
    const val HOME_ROLE_DECLINED = "Pluto is not your Home app yet. You can choose it any time under " +
        "Android Settings ▸ Apps ▸ Default apps ▸ Home app."

    const val PROFILES_NOTICE = "Work profile and Private Space apps are not shown in this version."

    const val ABOUT = "About"
    const val SUPPORT = "Support Pluto"
    const val ICONS = "Icons"
    const val CAROUSEL = "Console carousel"
    const val CAROUSEL_HELP = "How games and apps are laid out in console mode."
    const val ICON_PACK = "Icon pack"
    const val SYSTEM_ICONS = "System icons"
    const val SYSTEM_ICONS_HELP = "Each app's own icon"
    const val ICON_PACKS_LOADING = "Looking for icon packs…"
    const val NO_ICON_PACKS = "No icon packs installed. Install one from your app store (most support Nova or ADW launchers), then choose it here."
    const val ICON_PACK_MISSING = "Icon pack not installed: using system icons"
    const val KOFI = "Buy me a coffee on Ko-fi"
    const val KOFI_URL = "ko-fi.com/anderslc"
    fun version(name: String) = if (name.isBlank()) "Pluto" else "Pluto $name"
    const val LICENSE = "Open source under the MIT licence."
    const val PRIVACY = "No account, ads, analytics or network access."
    const val LIMITATIONS = "Known limitations: work profile and Private Space apps are not supported yet; " +
        "widgets always take a full row of the home grid."

    // Hidden apps
    const val HIDDEN_TITLE = "Hidden apps"
    const val HIDDEN_EXPLANATION = "Hiding only removes apps from Pluto's lists. It is not a security or privacy feature; " +
        "hidden apps can still be opened from Settings or other apps."
    const val HIDDEN_EMPTY = "No hidden apps. To hide one, open All apps, tap Actions, choose the app and pick Hide " +
        "(or press and hold the app, or press X with a controller)."
    const val RESTORE = "Restore"
    const val RESTORE_ALL = "Restore all"

    // Categories
    const val CATEGORIES_TITLE = "Categories"
    const val CATEGORIES_HELP = "Categories organise apps for browsing. They never limit access: every app is always in All apps."
    const val ADD_CATEGORY = "Add category"
    const val NEW_CATEGORY = "New category"
    const val CATEGORY_NAME = "Category name"
    const val RENAME = "Rename"
    const val RENAME_CATEGORY = "Rename category"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val DELETE = "Delete"
    const val SAVE = "Save"
    const val ADD = "Add"
    const val ALL_CATEGORY_NOTE = "Built in · always contains every app"
    fun deleteCategoryTitle(name: String) = "Delete category “$name”?"
    const val DELETE_CATEGORY_TEXT = "Apps in this category are not affected or uninstalled."
    fun memberCount(n: Int) = if (n == 1) "1 app" else "$n apps"

    // Controller
    const val CONTROLLER_TITLE = "Controller"
    const val CONNECTED = "Connected controllers"
    const val NO_CONTROLLER = "No controller connected. You can connect one over USB or Bluetooth at any time."
    fun ids(vendor: Int, product: Int) = "Vendor %04X · Product %04X".format(vendor, product)
    fun descriptor(short: String) = "ID $short"
    const val DIAGNOSTICS = "Live input"
    const val DIAGNOSTICS_HELP = "Press buttons or move the sticks on the controller to check what Android reports."
    const val DIAGNOSTICS_UNAVAILABLE = "Live input is unavailable right now."
    const val LAST_KEY = "Last button"
    const val NO_KEY_YET = "None yet"
    const val AXES = "Sticks and D-pad"
    const val NO_MOTION_YET = "No movement yet"
    const val MAPPING = "Button mapping"
    const val MAPPING_HELP = "Prompts use the names Android reports, because button letters differ between controllers. " +
        "Movement (D-pad and left stick), Home and Recents cannot be remapped."
    const val APPLY_TO = "Apply changes to"
    const val THIS_CONTROLLER = "This controller"
    const val ALL_CONTROLLERS = "All controllers"
    const val USING_OVERRIDE = "This controller uses its own mapping."
    const val USING_DEFAULT = "This controller uses the mapping for all controllers."
    const val REMAP = "Remap"
    const val NOT_ASSIGNED = "Not assigned"
    const val PRESS_A_BUTTON = "Press a button on the controller…"
    fun captureFor(action: String, seconds: Int) = "Remapping “$action”. Press a button… (cancels in $seconds s)"
    const val CAPTURE_REFUSED = "That button is reserved for movement or the system. Press a different button."
    const val CAPTURE_TIMEOUT = "No button pressed. Remap cancelled."
    const val CAPTURE_CANCELLED = "Remap cancelled."
    fun captured(key: String, action: String, previous: String?) =
        "“$key” now does “$action”." + (previous?.let { " It no longer does “$it”." } ?: "")
    const val RESET = "Reset to defaults"
    const val RESET_DONE = "Mapping reset to defaults."
    const val STICK = "Stick tuning"
    const val DEAD_ZONE = "Dead zone"
    const val DEAD_ZONE_HELP = "Stick movement smaller than this is ignored."
    const val REPEAT_DELAY = "Repeat delay"
    const val REPEAT_DELAY_HELP = "How long to hold before movement repeats."
    const val REPEAT_INTERVAL = "Repeat interval"
    const val REPEAT_INTERVAL_HELP = "Time between repeated moves while holding."
    const val MOVE = "Move"
    const val MOVE_KEYS = "D-pad or left stick"
    fun ms(value: Int) = "$value ms"

    fun actionLabel(action: ControllerAction): String = when (action) {
        ControllerAction.CONFIRM -> "Open / confirm"
        ControllerAction.BACK -> "Back / close"
        ControllerAction.ACTIONS -> "App actions"
        ControllerAction.SEARCH -> "Search"
        ControllerAction.SETTINGS -> "Settings"
        ControllerAction.PREV_CATEGORY -> "Previous category"
        ControllerAction.NEXT_CATEGORY -> "Next category"
    }

    // Onboarding
    const val WELCOME_TITLE = "Welcome to Pluto"
    const val WELCOME_TEXT = "Pluto is a home screen for everyday phone use and for gaming with a controller. " +
        "It adapts when you rotate the phone or connect a controller, and keeps your favourites, folders and categories the same everywhere."
    const val WELCOME_READY = "Pluto is ready to use with a default layout. Everything here is optional and can be changed later in Settings."
    fun appsFound(n: Int) = if (n == 1) "1 app found." else "$n apps found."
    const val LOADING_APPS = "Looking for apps…"
    const val NO_APPS_PROBLEM = "Pluto could not find any apps to show. Check that apps are installed and not restricted, " +
        "then reopen Pluto. Until apps appear, keep your current Home app."
    const val STEP_GAMES_TITLE = "Pick your games (optional)"
    const val STEP_GAMES_TEXT = "Apps you tick go into the Games category, which Handheld mode shows as its own tab. " +
        "You can change this any time from an app's actions: tap Actions in All apps, or press and hold an app."
    const val NO_GAMES_CATEGORY = "The Games category is not available. You can create categories later in Settings."
    fun gamesSelected(n: Int) = if (n == 1) "1 app in Games" else "$n apps in Games"
    const val STEP_CONTROLLER_TITLE = "Controller controls"
    const val STEP_CONTROLLER_TEXT = "Everything in Pluto works with touch or a game controller. Connect a controller at any time; " +
        "in landscape Pluto then switches to the Handheld layout."
    const val CONTROLLER_NOW = "A controller is connected. Try moving around this screen with it."
    const val STEP_HOME_TITLE = "Set Pluto as your Home app"
    const val STEP_HOME_TEXT = "Make Pluto open when you press Home. Android asks you to confirm."
    const val STEP_HOME_DONE = "Pluto is now your Home app."
    const val NOT_NOW = "Not now"
    const val NEXT = "Next"
    const val BACK = "Back"
    const val SKIP = "Skip"
    const val FINISH = "Finish"
    const val START = "Start using Pluto"
    fun stepOf(step: Int, total: Int) = "Step $step of $total"
}
