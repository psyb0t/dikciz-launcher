---
name: "dikciz-ui-design"
description: "Review and improve Dikciz menus, screens and interaction flows. Use for a UI redesign, usability audit, or changes to visible native or bundled app interfaces."
---

# Dikciz interface design

Read the project's UI design rule and the Android references below. Use the Android Lab skill for builds and private emulator inspection.

1. Inventory every requested screen and route, including dialogs, editors, overflow actions and empty/error states. Record the user's primary task and current friction for each.
2. Inspect real screens before editing. Identify redundant actions, missing hierarchy, unreachable controls, clipped text, misleading state and broken Back/keyboard behavior.
3. Choose shared components for repeated patterns. Put item management behind overflow, use labelled navigation rows, and reserve visual emphasis for primary actions. Preserve tool access and semantic IDs; update tests when a visible interaction requires a new step.
4. Implement and test complete user flows, including cancellation, persistence and recovery. Use compact and large-font layouts, portrait and landscape. Check touch targets, TalkBack labels, scroll reachability and keyboard overlap.
5. Capture and inspect final screenshots. Update user-facing instructions and affected documentation images. A screen is complete only when the visible result and its interactions are verified; record any remaining gap explicitly.

Store per-run evidence under `.testing/ui-refresh/`, ensuring it is ignored first. Preserve older runs in separate subdirectories. Download research into ignored `.research_files/android-ui/`; record source URLs and retrieval information. Recheck source and UI state on each invocation.

## References

- [Android settings design](https://source.android.com/docs/core/settings/settings-guidelines): navigation, grouped settings and action placement.
- [Android Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views): touch targets and accessible labels.
- [Android adaptive layout](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views): window sizes and layout behavior.
