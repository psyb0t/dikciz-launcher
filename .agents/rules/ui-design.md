# Dikciz interface quality

For visible UI changes, use the dikciz-ui-design skill before implementation. Preserve all existing capabilities and semantic automation access while improving their presentation.

- Give each screen one clear primary task. Lists open their items; secondary management actions belong in a clearly labelled overflow menu.
- Keep destructive actions out of ordinary navigation and confirm the precise consequence before execution.
- Use consistent typography, spacing, surfaces and action hierarchy from shared UI components. Do not render every navigation link as a prominent filled button.
- Keep interactive targets at least 48dp in both dimensions. Give icon actions accessible names. Respect system bars, keyboard insets, large fonts, landscape and Back navigation.
- Preserve form input on errors and external permission handoffs. Show loading, empty, error, disabled and success states where applicable.
- Verify the actual rendered screen and its interactions in the private emulator. Source inspection and compilation alone do not establish UI quality. Save before/after screenshots and check scrolling, keyboard visibility and accessibility semantics.
