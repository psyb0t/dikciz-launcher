---
name: "dikciz-android-lab"
description: "Build, test or inspect Dikciz Launcher source using Android Lab. Use when working on the launcher checkout, not when installing a supplied APK or operating an existing launcher."
---

# Dikciz with Android Lab

Use the installed `android-lab` skill for build and emulator operations. If it is unavailable, read the canonical [Android Lab skill](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab/SKILL.md) and its [setup reference](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab/references/setup.md) before running lab commands. Read the installed `android-lab-kotlin` skill before Kotlin, Java or Gradle changes; if unavailable, read [its canonical skill](https://github.com/psyb0t/android-lab/blob/main/.agents/skills/android-lab-kotlin/SKILL.md). Follow reference links needed for the requested operation. If the instructions cannot be retrieved, stop rather than guessing the commands.

Run from the Dikciz Launcher checkout and read its current project instructions, Makefile and [build instructions](https://github.com/psyb0t/dikciz-launcher#run-it). Use its supported Make targets and its required local Android Lab image version. Do not run from the installed skill directory, assume a sibling lab checkout exists, or silently substitute another image. Missing images require the [Android Lab local build](https://github.com/psyb0t/android-lab#build-the-images-locally) after the user accepts the SDK license.

Use the private lab emulator for development. Preserve the workspace's `.android-lab/` state. Installation of a supplied APK and operation of an existing device belong to the `dikciz-launcher` skill and do not require Android Lab. Stop after the requested build, focused test or inspection has a verified result; do not reset a device or publish changes without explicit authorization.
