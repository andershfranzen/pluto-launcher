# Room, DataStore, Compose and kotlinx libraries ship their own consumer rules.
# Keep enum names stable: settings and layers persist enum .name values.
-keepclassmembers enum dev.pluto.launcher.** { *; }
