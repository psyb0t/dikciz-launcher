# Dikciz user guide

This guide starts after you have downloaded and installed a Dikciz APK on your Android phone. It covers using the launcher on the phone. Each screenshot follows the step beside it.

For events, HTML widgets, Lua scripts, and typed actions, start with the [scripting tutorial](scripting-tutorial.md). Read [Dikciz configuration](dikciz-configuration.md) for the editable-file layout and [automation control](automation-control.md) for WebSocket, MCP, and remote-control commands.

## Install Dikciz and make it your home screen

1. Open the Dikciz APK from your browser, file manager, or Downloads app.
2. If Android asks, allow that app to install unknown apps, then accept Android's installation screen.
3. Open Dikciz. If Android asks which Home app to use, choose **Dikciz** and make it the default. If it does not ask, press the Home button and choose Dikciz there.
4. The first Dikciz screen asks for shared-storage access. Tap **Grant file access**, enable access for Dikciz in the Android panel, then return to Dikciz.

That access lets Dikciz create and watch `/sdcard/Dikciz/`. It contains your pages, widgets, themes, scripts, packages, and logs. Dikciz cannot load or save your home until Android grants it.

The bundled home opens on `Home (1H1V)`. Its native bottom and right rails show the current horizontal and vertical page coordinates. Every page is a fixed grid. A full page rejects another native widget instead of making items overlap or extending the page below the screen.

![Bundled home](images/user-guide/01-first-launch.png)

## Move around the home

### Switch pages or search by name

Swipe across a page or tap a rail dot to move one page. Tap the magnifier beside the horizontal rail to search all pages. Search matches the page name and its stable address. The bundled pages include `Home (1H1V)`, `Notes (2H1V)`, and `Workbench (1H2V)`.

![Page search](images/user-guide/02-page-search.png)

`Notes` combines an Android Settings shortcut, an HTML card, and the script dashboard.

![Notes page](images/user-guide/03-notes-page.png)

`Workbench` is the bundled starting point for public files, automation access, and the local control interfaces.

![Workbench page](images/user-guide/04-workbench-page.png)

### Create, name, reorder, or lock a page

Long press a rail beyond its last dot to create a page at that edge. Long press before its first dot to create one on the other edge. Dikciz selects and saves the new page immediately.

![New page selected and saved](images/user-guide/05-create-page.png)

Tap the Dikciz mark beside the horizontal rail, choose **Manage this page**, then choose **Rename this page**. Use a name that tells you what belongs on the page. Scripts and configuration use the stable address, such as `2H3V`.

![Page rename](images/user-guide/20-page-rename.png)

Open page search, type the name, then tap the matching row to return to it.

![Named page filtered in search](images/user-guide/32-page-search-by-name.png)

**Manage this page** also lets you do the following:

- **Set as home page** chooses the page Dikciz opens when Android returns Home.
- **Lock page** prevents accidental layout edits until you unlock it. Widget buttons and normal page navigation still work.
- **Move left**, **Move right**, **Move up**, and **Move down** exchange the page with its neighbour. A direction with no neighbour is disabled.
- **Add page** and **Remove page** change the page grid after confirmation.

![Page management actions](images/user-guide/19-page-menu.png)

### Set the grid to fit your layout

Open **Settings**, then change grid columns, rows, gap, or outer padding. Dikciz validates the whole change before saving. If a widget would collide or leave its page, it rejects the edit and names the affected page and widget. The old layout stays in place.

![Grid settings](images/user-guide/28-grid-settings.png)

## Add apps and widgets

### Find, launch, or manage an installed app

Tap the Dikciz mark and choose **Open app drawer**. You can also swipe up from the horizontal page rail. The drawer lists launchable apps installed on your phone.

![App drawer](images/user-guide/07-app-drawer.png)

Search by app label or package name. For example, `Settings` and `com.android.settings` find the same app.

![Drawer search](images/user-guide/08-app-drawer-search.png)

Tap an app to open it. Use its overflow button, or long press its row, for **Add shortcut**, Android **App info**, **Uninstall**, or **Force stop**. Android owns the app-info screen and uninstall confirmation. Force stop needs root. Without root, Dikciz reports `root_unavailable` and does not pretend it worked.

![App actions](images/user-guide/09-app-actions.png)

### Add an app shortcut, Dikciz widget, or provider widget

Tap the Dikciz mark and choose **Add to this page**. New items use the next free grid rectangle, from left to right and then top to bottom. Move the item after it appears if you want another position.

![Launcher controls](images/user-guide/06-launcher-controls.png)

![Add-to-page picker](images/user-guide/21-widget-picker.png)

The **Dikciz** category contains bundled HTML packages, an empty HTML widget, and the native script dashboard. Choosing a package creates a new widget. It never adds HTML into an existing widget.

![Dikciz widget packages](images/user-guide/22-html-package-library.png)

Apps that publish Android widgets have their own category. Dikciz hosts their real Android widget. It does not redraw it or put it inside a WebView.

![Third-party provider widgets](images/user-guide/23-provider-widgets.png)

If the page has no free grid rectangle, Dikciz shows a refusal. It does not place a widget off-screen, on top of another widget, or outside the page.

![Full-page refusal](images/user-guide/24-page-full.png)

### Move, resize, group, lock, or remove an item

Drag an app shortcut to move it. Long press a widget for **Move**, **Resize**, **Edit**, **Appearance**, **Fit content**, **Lock**, **Delete**, and Z-order actions. HTML widgets keep the top 24Dp for moving, while their content keeps normal tap behaviour.

![Widget action sheet](images/user-guide/11-widget-actions.png)

While moving or resizing, the grid stays visible. Drops and resize handles snap to whole cells. A collision is rejected and does not change the saved layout.

![Move grid](images/user-guide/12-move-grid.png)

![Resize grid](images/user-guide/13-resize-grid.png)

Drop one app shortcut on another to make an app group. Open the group to launch a member. Long press a member to remove it. A group with one member becomes a normal shortcut again.

![App group](images/user-guide/10-app-group.png)

**Delete** removes the widget and its Dikciz-owned public file. Use it only when you want to remove that widget.

![Widget removed from the page](images/user-guide/15-widget-delete.png)

## Build an HTML dashboard

### Edit an HTML widget

Long press an HTML widget, choose **Edit**, then change its title, HTML, CSS, JavaScript, or JSON state. The editor contains the source that widget runs.

![HTML editor](images/user-guide/16-widget-editor.png)

Save the change. Dikciz validates it and updates the running widget in place.

![Saved HTML rendered in the widget](images/user-guide/33-html-saved.png)

Use **Appearance** to set its background, border, corner radius, spacing, and font. An inherited field continues to follow the active theme.

![Appearance editor](images/user-guide/17-appearance-editor.png)

### Add a ready-made block to one HTML widget

Long press the HTML widget you want to change, select **Add block**, choose a block, then confirm. Dikciz appends marked HTML source to that widget. It does not use another native grid cell or reload the page.

![HTML block picker](images/user-guide/18-html-block-picker.png)

The new section appears at the bottom of the widget that owns it.

![Appended HTML block](images/user-guide/34-html-block-appended.png)

Inside an HTML widget, use `selfWidget` to refer to the widget itself even after you move it. Use `dikciz.pages()` when you need the page map or another widget. The [scripting tutorial](scripting-tutorial.md) explains bridge APIs, state, events, and cross-widget work. [Automation control](automation-control.md) is the protocol reference.

## Run scripts and see what they did

### Open, enable, and run a script

Lua scripts are background automation, not widgets. Open **Settings**, then **Scripts**. The script dashboard shows each script's status, most recent run, enable state, and logs.

![Script manager](images/user-guide/25-script-manager.png)

Open the bundled `hourly-clock` script to view its source, policy, state, and enable control. A policy blocks an action that is missing from its capability list. Dikciz records `capability_denied` in the script log rather than silently ignoring it.

![Bundled clock script](images/user-guide/26-script-editor.png)

### Grant the Android access an automation needs

Open **Settings**, then **Automation**, then **Set up full device access**. The access sheet tells you which Android permissions and special accesses are active, then opens the matching Android panels when you choose an access. Android makes the final decision.

![Automation access](images/user-guide/31-automation-access.png)

The bundled clock script posts an Android notification after notification access is granted. Pull down the notification shade to see it.

![Clock script notification](images/user-guide/35-script-notification.png)

Open the dashboard on Notes and select the script logs to see its outcome, time, details, and failures.

![Clock script log](images/user-guide/36-script-log.png)

The [scripting tutorial](scripting-tutorial.md) walks through subscriptions, policies, custom events, HTML bridge use, typed actions, and logs.

### React to a notification event

The bundled notification digest listens for unread-notification events and updates the command deck. It is event driven, not a timer that polls the widget's text.

![Notification event updated the command deck](images/user-guide/37-notification-digest.png)

## Change themes, recover, and connect another machine

### Change theme or inspect launcher settings

Settings contains the grid controls, themes, remote-control settings, diagnostics, script tools, reset, and safe mode.

![Dikciz settings](images/user-guide/27-settings.png)

Choose a theme to apply its saved colors, fonts, and style defaults. Widget fields you have explicitly overridden stay overridden.

![Themes](images/user-guide/29-themes.png)

### Recover from a broken configuration

**Reset Dikciz home** replaces the current Dikciz home with the bundled home, scripts, and policies. **Safe mode** starts the bundled home without overwriting your files. Use safe mode when a broken configuration stops the normal home from opening, then repair or replace the files below `/sdcard/Dikciz/`.

![Reset and safe mode](images/user-guide/30-safe-mode.png)

### Connect Dikciz to a computer

In **Settings**, you can enable or disable the WebSocket and MCP control interfaces and set, replace, or clear their optional bearer password. The password protects remote connections only. Changing or clearing it on the phone does not require knowing the old password.

The interfaces listen only on the phone's loopback address. You choose how to connect another machine, such as an ADB forward. The technical commands, protocol, event stream, remote authentication, logs, screenshots, UI inspection, and configuration actions are documented in [automation control](automation-control.md).

## Edit the public files directly

All user-editable files live below `/sdcard/Dikciz/`. `config.json` is the root. Pages, widgets, themes, fonts, wallpapers, HTML packages, scripts, policies, remote-auth settings, and logs each have their own directory. Valid file changes reload automatically. An invalid file leaves the last accepted home running and adds the reason to the local log.

Use a file manager on the phone, an ADB connection, or a remote-control client to edit those files. The [configuration guide](dikciz-configuration.md) lists every path, field, validation rule, and recovery path.
