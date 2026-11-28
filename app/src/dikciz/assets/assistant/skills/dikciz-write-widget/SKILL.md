---
name: dikciz-write-widget
description: Create, edit and verify an HTML widget with its own CSS, JavaScript and state on an existing home page.
---

# Author an HTML widget

1. Use dikciz_home_get to resolve the requested page and its existing items. If the task already supplies a pageId, use that exact ID.
2. Call dikciz_add_widget with widgetType set to html and pageId set to the target page. Keep the returned address and ID. Check for page_full or page_locked before proceeding.
3. Call dikciz_automation_dispatch with an actions array containing one patchWidget. Set sourceWidgetAddress to the new widget address. In the action object set type to patchWidget, and supply its actual widgetAddress and values with title, html, css and javascript. Keep the widget self-contained. Read saved data from selfWidget.state. Persist an object with selfWidget.patchState({...selfWidget.state, changedKey: newValue}). Initialize after the window's dikciz-ready event. Page-widget patches replace the state object, so keep existing keys.
4. Check each dispatch action outcome, then read the returned address once with dikciz_widget_get to verify the saved title and fields under source. The source object contains html, css and javascript when configuration reads are allowed. Runtime state can change only while the widget renders on Home. If asked to verify execution, show the target page, then check the readiness state written by the widget. An absent readiness value leaves execution unverified. Read the full launcher configuration only for a task that explicitly needs the full configuration. If patching failed, report the existing widget's ID and inspect it before attempting a new insertion.

To edit an existing widget, read its address with dikciz_widget_get first. Change only the requested fields, then verify them with the same tool. Preserve state and unrelated source. Check a full or locked page before asking the owner where to put a new widget.
