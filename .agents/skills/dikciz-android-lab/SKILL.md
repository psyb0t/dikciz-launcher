---
name: "dikciz-android-lab"
description: "Build, test or inspect Dikciz Launcher source using Android Lab. Use when working on the launcher checkout, not when installing a supplied APK or operating an existing launcher."
---

# Dikciz with Android Lab

Use the installed `android-lab` skill for build and emulator operations. Install it with `openclaw skills install @psyb0t/android-lab` when the agent supports OpenClaw skills. If it is unavailable, read the canonical [Android Lab skill](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab/SKILL.md) and its [setup reference](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab/references/setup.md) before running lab commands. Read the installed `android-lab-kotlin` skill before Kotlin, Java or Gradle changes; if unavailable, read [its canonical skill](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab-kotlin/SKILL.md). Follow reference links needed for the requested operation. If the instructions cannot be retrieved, stop rather than guessing the commands.

Run from the Dikciz Launcher checkout and read its current project instructions, Makefile and [build instructions](https://github.com/psyb0t/dikciz-launcher#run-it). Use its supported Make targets and the Android Lab version pinned by `ANDROID_LAB_VERSION` in its Makefile. Do not run from the installed skill directory, assume a sibling lab checkout exists, or substitute another image. When the pinned images are missing, `make` builds them with that Android Lab release's installer, but only after the user accepts the [Android SDK license](https://developer.android.com/studio/terms). Ask first, then pass `ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes`. Never set it on the user's behalf.

Use the private lab emulator for development. Preserve the workspace's `.android-lab/` state. Installation of a supplied APK and operation of an existing device belong to the `dikciz-launcher` skill and do not require Android Lab. Stop after the requested build, focused test or inspection has a verified result; do not reset a device or publish changes without explicit authorization.
