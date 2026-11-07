# Screens and guided workflows

Use the [main tutorial](tutorial.md) for the complete first-run path and the [home user guide](user-guide.md) for pages, drawers, widgets, scripts, themes, logging and Android access. This guide covers the app, assistant and management screens. Each image links to its full capture. Images use private emulator data; model replies and catalogs use a demonstration API. Android-owned permission and hardware results depend on the device.

## Contents

- [Startup and version](#startup-and-version)
- [Apps and complete bundles](#apps-and-complete-bundles)
- [Assistant and conversations](#assistant-and-conversations)
- [Providers, models and access](#providers-models-and-access)
- [Remote access tokens](#remote-access-tokens)
- [Activity, resources and rules](#activity-resources-and-rules)
- [Backups and recovery](#backups-and-recovery)
- [Places, trips and device connections](#places-trips-and-device-connections)
- [Reset the home](#reset-the-home)
- [Urgent alerts and cancellation](#urgent-alerts-and-cancellation)
- [Widget options](#widget-options)
- [Permission setup and logs](#permission-setup-and-logs)
- [Script creation and transfer](#script-creation-and-transfer)

## Startup and version

The cold-start splash shows the running version and build type at its bottom. Tap it to continue early. The launcher controls also show the serving version, so release and debug installs can be distinguished. Installation and shared-storage setup are in the [home user guide](user-guide.md#install-dikciz-and-make-it-your-home-screen).

<details>
<summary>View the screens</summary>

<p><a href="images/screens/startup-version.png"><img src="images/screens/startup-version.png" alt="Cold-start version and build label" width="280"></a><br>Cold-start version and build label.</p>

<p><a href="images/screens/first-run-storage.png"><img src="images/screens/first-run-storage.png" alt="First-run shared-storage handoff" width="280"></a><br>First-run shared-storage handoff.</p>

<p><a href="images/screens/first-run-storage-android.png"><img src="images/screens/first-run-storage-android.png" alt="Android shared-storage access control" width="280"></a><br>Android shared-storage access control.</p>

</details>

## Apps and complete bundles

Open the Dikciz mark, then Apps. Tap a title to open its interface. Home preserves its task; Recents returns to it. The overflow beside each title contains Edit bundle, execution controls, source export and removal. Editing opens one source file at a time. Switching files retains the other drafts; Save bundle writes the complete package. Back asks before discarding edits. Import expects a source archive path and keeps an invalid path available for correction. Read [custom apps](custom-apps.md) and the [GPS todo tutorial](todo-app-tutorial.md) for source, state and events.

### Use a bundled app

| App | Walkthrough |
| --- | --- |
| SOS and check-ins | Add exact recipients, save an inactive plan and preview it. Arm a missed check-in or explicitly start the countdown, then cancel pending work when needed. Follow the [phone setup steps](safety-and-journeys.md#on-the-phone). |
| Voice notes | Tap Record voice note, allow the microphone if asked, then Stop and save. Use the saved recording's playback control. Leaving the interface stops recording; reopen it to complete a pending save. |
| Agenda and alarms | Allow calendar access, then Refresh agenda. Open calendar and Open alarms hand off to Android's apps. The agenda reads upcoming events; it does not create them. |
| Device status | Read the battery gauge, process CPU history and memory/storage meters. Expand Device details for additional readings. Protected system counters remain explicit. |
| Travel todo | Add or check tasks manually. Review location access and choose Watch New York only when you want its background arrival rule. Follow the [GPS and custom-event tutorial](todo-app-tutorial.md). |

<details>
<summary>View the screens</summary>

<p><a href="images/screens/apps.png"><img src="images/screens/apps.png" alt="App list" width="280"></a><br>App list.</p>

<p><a href="images/screens/app-actions.png"><img src="images/screens/app-actions.png" alt="App management overflow" width="280"></a><br>App management overflow.</p>

<p><a href="images/screens/recents.png"><img src="images/screens/recents.png" alt="App in Android Recents" width="280"></a><br>App in Android Recents.</p>

<p><a href="images/screens/app-editor.png"><img src="images/screens/app-editor.png" alt="Bundle editor and file selector" width="280"></a><br>Bundle editor and file selector.</p>

<p><a href="images/screens/app-editor-keyboard.png"><img src="images/screens/app-editor-keyboard.png" alt="Save remains above the keyboard" width="280"></a><br>Save remains above the keyboard.</p>

<p><a href="images/screens/app-discard.png"><img src="images/screens/app-discard.png" alt="Discard unsaved changes" width="280"></a><br>Discard unsaved changes.</p>

<p><a href="images/screens/app-import-error.png"><img src="images/screens/app-import-error.png" alt="Import failure and retry" width="280"></a><br>Import failure and retry.</p>

<p><a href="images/screens/app-create.png"><img src="images/screens/app-create.png" alt="Create a complete app" width="280"></a><br>Create a complete app.</p>

<p><a href="images/screens/app-file-picker.png"><img src="images/screens/app-file-picker.png" alt="Choose a source file" width="280"></a><br>Choose a source file.</p>

<p><a href="images/screens/app-import.png"><img src="images/screens/app-import.png" alt="Import a source archive" width="280"></a><br>Import a source archive.</p>

<p><a href="images/screens/app-remove.png"><img src="images/screens/app-remove.png" alt="Remove an app and its data" width="280"></a><br>Remove an app and its data.</p>

<p><a href="images/screens/custom-app-sos.png"><img src="images/screens/custom-app-sos.png" alt="SOS and check-ins" width="280"></a><br>SOS and check-ins.</p>

<p><a href="images/screens/custom-app-voice-notes.png"><img src="images/screens/custom-app-voice-notes.png" alt="Voice notes" width="280"></a><br>Voice notes.</p>

<p><a href="images/screens/custom-app-agenda.png"><img src="images/screens/custom-app-agenda.png" alt="Agenda and alarms" width="280"></a><br>Agenda and alarms.</p>

<p><a href="images/screens/custom-app-device-status.png"><img src="images/screens/custom-app-device-status.png" alt="Device status" width="280"></a><br>Device status.</p>

<p><a href="images/screens/custom-app-travel-todo.png"><img src="images/screens/custom-app-travel-todo.png" alt="Travel todo" width="280"></a><br>Travel todo.</p>

<p><a href="images/screens/custom-app-editor-travel-todo-manifest.png"><img src="images/screens/custom-app-editor-travel-todo-manifest.png" alt="App identity and subscriptions" width="280"></a><br>App identity and subscriptions.</p>

<p><a href="images/screens/custom-app-editor-travel-todo-html.png"><img src="images/screens/custom-app-editor-travel-todo-html.png" alt="HTML document" width="280"></a><br>HTML document.</p>

<p><a href="images/screens/custom-app-editor-travel-todo-css.png"><img src="images/screens/custom-app-editor-travel-todo-css.png" alt="Stylesheet" width="280"></a><br>Stylesheet.</p>

<p><a href="images/screens/custom-app-editor-travel-todo-javascript.png"><img src="images/screens/custom-app-editor-travel-todo-javascript.png" alt="UI JavaScript" width="280"></a><br>UI JavaScript.</p>

<p><a href="images/screens/custom-app-editor-travel-todo-backgroundSource.png"><img src="images/screens/custom-app-editor-travel-todo-backgroundSource.png" alt="Background handler" width="280"></a><br>Background handler.</p>

<p><a href="images/screens/custom-app-voice-saved.png"><img src="images/screens/custom-app-voice-saved.png" alt="Saved voice note with playback controls" width="280"></a><br>Record voice note starts microphone capture. Stop and save retains it in this app. Leaving the interface stops recording; reopening completes the pending save and exposes playback.</p>

</details>

## Assistant and conversations

Tap the cheese beside the home controls. Use the resize action for half or full height. The composer stays below the message viewport and above the keyboard. Scroll up to read earlier messages; Latest resumes following the stream. Tap the conversation title for saved conversations, Events and Manage. Manage offers Rename, Change provider, Delete and Run a routine. Deletion confirms removal of the transcript and event rules. Closing chat leaves a turn running; the working notification reopens it and Stop cancels remaining work. See [embedded chat](embedded-chat.md) for tool execution and cancellation limits.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/chat-sheet-empty.png"><img src="images/screens/chat-sheet-empty.png" alt="Empty assistant" width="280"></a><br>Empty assistant.</p>

<p><a href="images/screens/chat-sheet-half.png"><img src="images/screens/chat-sheet-half.png" alt="Half-height streamed Markdown" width="280"></a><br>Half-height streamed Markdown.</p>

<p><a href="images/screens/chat-sheet-full.png"><img src="images/screens/chat-sheet-full.png" alt="Full-height assistant" width="280"></a><br>Full-height assistant.</p>

<p><a href="images/screens/chat-sheet-keyboard.png"><img src="images/screens/chat-sheet-keyboard.png" alt="Typing with the keyboard" width="280"></a><br>Typing with the keyboard.</p>

<p><a href="images/screens/chat-progress-notification.png"><img src="images/screens/chat-progress-notification.png" alt="Working notification and Stop" width="280"></a><br>Working notification and Stop.</p>

<p><a href="images/screens/conversations.png"><img src="images/screens/conversations.png" alt="Conversation picker" width="280"></a><br>Conversation picker.</p>

<p><a href="images/screens/conversation-actions.png"><img src="images/screens/conversation-actions.png" alt="Conversation management" width="280"></a><br>Conversation management.</p>

<p><a href="images/screens/conversation-rename.png"><img src="images/screens/conversation-rename.png" alt="Rename a conversation" width="280"></a><br>Rename a conversation.</p>

<p><a href="images/screens/conversation-provider.png"><img src="images/screens/conversation-provider.png" alt="Change provider" width="280"></a><br>Change provider.</p>

<p><a href="images/screens/conversation-delete.png"><img src="images/screens/conversation-delete.png" alt="Delete confirmation" width="280"></a><br>Delete confirmation.</p>

<p><a href="images/screens/new-conversation.png"><img src="images/screens/new-conversation.png" alt="Choose a provider for a new conversation" width="280"></a><br>Choose a provider for a new conversation.</p>

<p><a href="images/screens/routines.png"><img src="images/screens/routines.png" alt="Read-only routines" width="280"></a><br>Read-only routines.</p>

<p><a href="images/screens/event-rules.png"><img src="images/screens/event-rules.png" alt="Conversation event subscriptions" width="280"></a><br>Conversation event subscriptions.</p>

<p><a href="images/screens/routine-confirmation.png"><img src="images/screens/routine-confirmation.png" alt="Review a routine before running it" width="280"></a><br>Review a routine before running it.</p>

<p><a href="images/screens/widget-request.png"><img src="images/screens/widget-request.png" alt="Describe a widget before creating it" width="280"></a><br>Describe a widget before creating it.</p>

<p><a href="images/screens/chat-phone-tool.png"><img src="images/screens/chat-phone-tool.png" alt="Phone tool call in the transcript" width="280"></a><br>Phone tool call in the transcript. The provider is a demonstration endpoint; the tool reads the emulator's actual Android SDK result.</p>

<p><a href="images/screens/chat-tool-result.png"><img src="images/screens/chat-tool-result.png" alt="Expanded tool result" width="280"></a><br>Expanded tool result. The provider is a demonstration endpoint; the tool reads the emulator's actual Android SDK result.</p>

</details>

## Providers, models and access

Open the chat settings icon and choose Add provider. OpenAI and Anthropic templates fill the protocol and base URL; Custom accepts your compatible host. Enter your own API key and save the connection. Select a model from the model menu, then use Tune for that conversation's context/output limits, compaction and reasoning settings. Read-only access hides write tools and blocks direct execution of writes. Context size includes the reserved output budget. Provider defaults and per-conversation overrides are separate; see [provider setup](embedded-chat.md#connect-a-provider).

<details>
<summary>View the screens</summary>

<p><a href="images/screens/provider-list.png"><img src="images/screens/provider-list.png" alt="Saved provider connections" width="280"></a><br>Saved provider connections.</p>

<p><a href="images/screens/provider-templates.png"><img src="images/screens/provider-templates.png" alt="Provider template picker" width="280"></a><br>Provider template picker.</p>

<p><a href="images/screens/provider-openai.png"><img src="images/screens/provider-openai.png" alt="OpenAI connection" width="280"></a><br>OpenAI connection.</p>

<p><a href="images/screens/provider-anthropic.png"><img src="images/screens/provider-anthropic.png" alt="Anthropic connection" width="280"></a><br>Anthropic connection.</p>

<p><a href="images/screens/provider-custom.png"><img src="images/screens/provider-custom.png" alt="Custom API connection" width="280"></a><br>Custom API connection.</p>

<p><a href="images/screens/chat-model-dropdown.png"><img src="images/screens/chat-model-dropdown.png" alt="Model discovery and selection" width="280"></a><br>Model discovery and selection.</p>

<p><a href="images/screens/chat-model-settings.png"><img src="images/screens/chat-model-settings.png" alt="Context, output and read-only settings" width="280"></a><br>Context, output and read-only settings.</p>

<p><a href="images/screens/chat-model-lower.png"><img src="images/screens/chat-model-lower.png" alt="Tool rounds, timeout, safety margin and system instructions" width="280"></a><br>Scroll the model form for tool rounds, turn timeout, context safety margin and additional system instructions.</p>

<p><a href="images/screens/chat-compaction.png"><img src="images/screens/chat-compaction.png" alt="Context compaction policies" width="280"></a><br>Choose drop-oldest to discard earlier messages or summarize to retain a summary within the configured budget.</p>

</details>

## Remote access tokens

Open Settings, Automation, Configure remote bearer authentication, then Access tokens. Create a named token with only the grants the client needs. Review expiry, request limits and optional SMS address restrictions. Copy the secret once into the client. Manage reveals editing, enable/disable, rotation and revocation. Rotation replaces the secret; revocation is permanent. Secrets are intentionally excluded from this gallery. The [token guide](remote-access-tokens.md) explains transport authentication and grant boundaries.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/access-tokens-list.png"><img src="images/screens/access-tokens-list.png" alt="Token identities and grants" width="280"></a><br>Token identities and grants.</p>

<p><a href="images/screens/access-token-create.png"><img src="images/screens/access-token-create.png" alt="Create a scoped token" width="280"></a><br>Create a scoped token.</p>

<p><a href="images/screens/access-token-manage.png"><img src="images/screens/access-token-manage.png" alt="Manage a token" width="280"></a><br>Manage a token.</p>

<p><a href="images/screens/access-token-edit.png"><img src="images/screens/access-token-edit.png" alt="Edit grants and restrictions" width="280"></a><br>Edit grants and restrictions.</p>

<p><a href="images/screens/access-token-revoke.png"><img src="images/screens/access-token-revoke.png" alt="Revoke confirmation" width="280"></a><br>Revoke confirmation.</p>

<p><a href="images/screens/access-token-rotate.png"><img src="images/screens/access-token-rotate.png" alt="Rotate confirmation" width="280"></a><br>Rotate confirmation. Cancel keeps the current credential.</p>

<p><a href="images/screens/remote-authentication.png"><img src="images/screens/remote-authentication.png" alt="Remote authentication and token administration" width="280"></a><br>Set or replace the owner password on the phone. Enable authentication before exposing a listener beyond loopback, then create scoped tokens for clients. The password fields remain masked.</p>

</details>

## Activity, resources and rules

Open Settings, Activity & resources. Choose the activity filter to inspect completed work as well as problems. A result records what the command reported; check the operation's own observed outcome when needed. Resources shows measured process usage and supported work that can be paused. Review the exact target before confirming. Open Settings, Automation rules to create a trigger, condition and action, preview its result, then save it. See [operations](operations.md) for measurement scope and rule behavior.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/activity.png"><img src="images/screens/activity.png" alt="Activity history" width="280"></a><br>Activity history.</p>

<p><a href="images/screens/activity-all.png"><img src="images/screens/activity-all.png" alt="Activity with the all-outcomes filter" width="280"></a><br>Activity with the all-outcomes filter.</p>

<p><a href="images/screens/resources.png"><img src="images/screens/resources.png" alt="Resource measurements" width="280"></a><br>Resource measurements.</p>

<p><a href="images/screens/resource-confirmation.png"><img src="images/screens/resource-confirmation.png" alt="Pause confirmation" width="280"></a><br>Pause confirmation.</p>

<p><a href="images/screens/rule-list.png"><img src="images/screens/rule-list.png" alt="Saved visual rules" width="280"></a><br>Saved visual rules.</p>

<p><a href="images/screens/rule-editor-top.png"><img src="images/screens/rule-editor-top.png" alt="Rule trigger and condition" width="280"></a><br>Rule trigger and condition.</p>

<p><a href="images/screens/rule-editor-test.png"><img src="images/screens/rule-editor-test.png" alt="Rule preview" width="280"></a><br>Rule preview.</p>

<p><a href="images/screens/activity-filter.png"><img src="images/screens/activity-filter.png" alt="Activity outcome filters" width="280"></a><br>Activity outcome filters.</p>

</details>

## Backups and recovery

Open Settings, Backup and restore. Enter a passphrase of at least 12 characters, choose whether encrypted provider keys should be included, and create the archive. Preserve both archive and passphrase. To restore, choose the archive, enter its passphrase and preview it. Inspect the counts before confirming replacement. Cancel leaves the current setup intact. Scripts and event rules remain disabled unless explicitly activated. Custom app data and remote credentials are excluded; see [backup scope](backups.md).

<details>
<summary>View the screens</summary>

<p><a href="images/screens/backup-form.png"><img src="images/screens/backup-form.png" alt="Create or select an encrypted backup" width="280"></a><br>Create or select an encrypted backup.</p>

<p><a href="images/screens/backup-saved.png"><img src="images/screens/backup-saved.png" alt="Saved archive" width="280"></a><br>Saved archive.</p>

<p><a href="images/screens/backup-preview.png"><img src="images/screens/backup-preview.png" alt="Validated restore preview" width="280"></a><br>Validated restore preview.</p>

</details>

## Places, trips and device connections

Open Settings, Safety, journeys and devices. Safety opens the editable SOS app. Places creates a location boundary with radius and optional dwell time; enable it only when location events are wanted. Trips starts route recording and exposes retained routes. Devices opens a connection form for RFCOMM, GATT, USB or NFC. Each kind has different required fields. A form is configuration, not proof that hardware connected. Scan hosts image/document recognition; Health hosts Health Connect selection. Wi-Fi asks Android to save or approve a network. See [companion workflows](companion.md) for the Android handoffs and supported hardware.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/companion-safety.png"><img src="images/screens/companion-safety.png" alt="Safety entry point" width="280"></a><br>Safety entry point.</p>

<p><a href="images/screens/companion-places.png"><img src="images/screens/companion-places.png" alt="Saved places" width="280"></a><br>Saved places.</p>

<p><a href="images/screens/place-editor.png"><img src="images/screens/place-editor.png" alt="Place coordinates, radius and dwell" width="280"></a><br>Place coordinates, radius and dwell.</p>

<p><a href="images/screens/companion-trips.png"><img src="images/screens/companion-trips.png" alt="Recorded trips" width="280"></a><br>Recorded trips.</p>

<p><a href="images/screens/companion-devices.png"><img src="images/screens/companion-devices.png" alt="Device connections" width="280"></a><br>Device connections.</p>

<p><a href="images/screens/connection-rfcomm.png"><img src="images/screens/connection-rfcomm.png" alt="Bluetooth RFCOMM form" width="280"></a><br>Bluetooth RFCOMM form.</p>

<p><a href="images/screens/connection-gatt.png"><img src="images/screens/connection-gatt.png" alt="Bluetooth GATT form" width="280"></a><br>Bluetooth GATT form.</p>

<p><a href="images/screens/connection-usb.png"><img src="images/screens/connection-usb.png" alt="USB interface form" width="280"></a><br>USB interface form.</p>

<p><a href="images/screens/connection-nfc.png"><img src="images/screens/connection-nfc.png" alt="NFC technology form" width="280"></a><br>NFC technology form.</p>

<p><a href="images/screens/companion-scan.png"><img src="images/screens/companion-scan.png" alt="Image and document recognition" width="280"></a><br>Image and document recognition.</p>

<p><a href="images/screens/companion-health.png"><img src="images/screens/companion-health.png" alt="Health Connect controls" width="280"></a><br>Health Connect controls.</p>

<p><a href="images/screens/companion-wi-fi.png"><img src="images/screens/companion-wi-fi.png" alt="Wi-Fi controls" width="280"></a><br>Wi-Fi controls.</p>

<p><a href="images/screens/wifi-editor.png"><img src="images/screens/wifi-editor.png" alt="Network configuration" width="280"></a><br>Network configuration.</p>

<p><a href="images/screens/wifi-password.png"><img src="images/screens/wifi-password.png" alt="Secured network configuration" width="280"></a><br>Secured network configuration.</p>

<p><a href="images/screens/health-review.png"><img src="images/screens/health-review.png" alt="Health record permission review" width="280"></a><br>Health record permission review. Review opens Android's permission or network flow; Cancel/Close returns without completing this review.</p>

<p><a href="images/screens/wifi-approval.png"><img src="images/screens/wifi-approval.png" alt="Saved-network approval" width="280"></a><br>Saved-network approval. Review opens Android's permission or network flow; Cancel/Close returns without completing this review.</p>

<p><a href="images/screens/route-preview.png"><img src="images/screens/route-preview.png" alt="Recorded route preview" width="280"></a><br>Recorded route preview. Coordinates and route samples come from the private emulator.</p>

<p><a href="images/screens/place-editor-saved.png"><img src="images/screens/place-editor-saved.png" alt="Edit a saved place" width="280"></a><br>Edit a saved place. Coordinates and route samples come from the private emulator.</p>

In Scan, choose one or more local images through Android's document picker. Read first image shows recognized text and decoded barcodes without opening decoded links. Create cleaned PDF saves an image-based document and exposes its path; it does not add a searchable text layer. Clear selection releases the current selection. These captures use the repository's printed-text fixture and the real on-device recognition model.

<p><a href="images/screens/scan-selected.png"><img src="images/screens/scan-selected.png" alt="Selected local image" width="280"></a><br>Selected local image.</p>

<p><a href="images/screens/scan-result.png"><img src="images/screens/scan-result.png" alt="On-device text recognition" width="280"></a><br>On-device text recognition.</p>

<p><a href="images/screens/scan-pdf.png"><img src="images/screens/scan-pdf.png" alt="Saved cleaned PDF" width="280"></a><br>Saved cleaned PDF.</p>

</details>

## Reset the home

Open Settings and choose Reset Dikciz home. Read the confirmation before continuing. Cancel leaves the current home intact. Reset changes the bundled home configuration; it does not grant Android permissions or erase chat history. Export app source and preserve any data you need separately.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/reset-confirmation.png"><img src="images/screens/reset-confirmation.png" alt="Reset home confirmation" width="280"></a><br>Reset home confirmation.</p>

</details>

## Urgent alerts and cancellation

SOS opens a dimmed modal with the pending action and a large cancellation control. Review the message and countdown, then cancel before the deadline to prevent the pending send. A warning can request sound, vibration and speech; Android volume, notification and lock-screen settings still apply. The example uses a private emulator and was cancelled without sending. Read [SOS setup and outcomes](safety-and-journeys.md) before arming a real plan.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/sos-countdown.png"><img src="images/screens/sos-countdown.png" alt="SOS countdown and cancellation" width="280"></a><br>SOS countdown and cancellation.</p>

<p><a href="images/screens/custom-app-modal.png"><img src="images/screens/custom-app-modal.png" alt="An app-defined urgent alert in the shared native host" width="280"></a><br>The same dimmed host supports app-defined urgent alerts through customAppAlert. The app owns the pending work and its cancellation handler; see <a href="custom-apps.md#timers-and-alerts">timers and alerts</a>.</p>

</details>

## Widget options

Long press an HTML widget and choose Appearance to change its native wrapper. Pick opens the color palette; the font control opens bundled and imported choices. Leave a field empty to inherit it. For page-wide editing locks, open Manage this page, Change widget locks. Locking blocks edits while normal widget actions remain available. In Add to this page, expand Dikciz and choose a template to preview it before Load creates an independent copy. See the [widget editing guide](user-guide.md#edit-an-html-widget).

<details>
<summary>View the screens</summary>

<p><a href="images/screens/color-picker.png"><img src="images/screens/color-picker.png" alt="Wrapper color palette" width="280"></a><br>Wrapper color palette.</p>

<p><a href="images/screens/font-picker.png"><img src="images/screens/font-picker.png" alt="Font selection" width="280"></a><br>Font selection.</p>

<p><a href="images/screens/widget-locks.png"><img src="images/screens/widget-locks.png" alt="Per-widget locks" width="280"></a><br>Per-widget locks.</p>

<p><a href="images/screens/template-preview.png"><img src="images/screens/template-preview.png" alt="Complete template preview" width="280"></a><br>Complete template preview.</p>

In Edit, scroll past HTML to CSS, JavaScript and the independent JSON state. Changes remain in the form until Save. The lower controls choose content-height sizing and a reusable library ID. Save to library copies the complete source package; it excludes live instance state. Cancel leaves the placed widget unchanged.

<p><a href="images/screens/widget-editor-css.png"><img src="images/screens/widget-editor-css.png" alt="Widget stylesheet" width="280"></a><br>Widget stylesheet.</p>

<p><a href="images/screens/widget-editor-javascript.png"><img src="images/screens/widget-editor-javascript.png" alt="Widget JavaScript" width="280"></a><br>Widget JavaScript.</p>

<p><a href="images/screens/widget-editor-state.png"><img src="images/screens/widget-editor-state.png" alt="Instance JSON state" width="280"></a><br>Instance JSON state.</p>

<p><a href="images/screens/widget-editor-save-to-library.png"><img src="images/screens/widget-editor-save-to-library.png" alt="Content sizing and reusable source export" width="280"></a><br>Content sizing and reusable source export.</p>

</details>

## Permission setup and logs

Open Settings, Automation, Set up full device access. The screen lists granted, missing, on-demand and unavailable capabilities. The highlighted next action advances setup; individual controls let you review a particular capability. Return after each Android handoff and continue. Scroll to the lower entries for special access and package installation. Grant only what you intend to use; a permission does not create missing hardware or root access. Open Settings, View logs to inspect retained logs, and Configure logging for level and retention. See [Android access](android-access.md) and [logging](user-guide.md#choose-log-detail-and-retention).

<details>
<summary>View the screens</summary>

<p><a href="images/screens/device-access-middle.png"><img src="images/screens/device-access-middle.png" alt="Special Android access controls" width="280"></a><br>Special Android access controls.</p>

<p><a href="images/screens/device-access-bottom.png"><img src="images/screens/device-access-bottom.png" alt="Lower setup controls" width="280"></a><br>Lower setup controls.</p>

<p><a href="images/screens/log-viewer.png"><img src="images/screens/log-viewer.png" alt="Retained log viewer" width="280"></a><br>Retained log viewer.</p>

<p><a href="images/screens/permission-notification.png"><img src="images/screens/permission-notification.png" alt="Owner review opened from the notification" width="280"></a><br>Owner review opened from the notification. Continue with Allow access when you approve the requested capability.</p>

<p><a href="images/screens/permission-notification-shade.png"><img src="images/screens/permission-notification-shade.png" alt="Permission request in the notification shade" width="280"></a><br>Permission request in the notification shade. Tap the notification action to continue the owner review on the phone.</p>

</details>

## Script creation and transfer

Open Settings, Manage scripts. Choose New Lua script or New JavaScript script, enter a title, edit the source and JSON state, then Save. Scripts and event subscriptions are separate: saving source does not grant permissions or subscribe to every event. An existing script can be exported as a reusable archive. Copy the archive to the imports directory, choose Import script archive and inspect the confirmation before importing. Delete script has its own confirmation; Cancel returns to the editor. See [background JavaScript](background-javascript.md) and the [scripting tutorial](scripting-tutorial.md) for event registration, policy and state.

<details>
<summary>View the screens</summary>

<p><a href="images/screens/javascript-create.png"><img src="images/screens/javascript-create.png" alt="Create a JavaScript handler" width="280"></a><br>Create a JavaScript handler.</p>

<p><a href="images/screens/script-delete.png"><img src="images/screens/script-delete.png" alt="Delete script confirmation" width="280"></a><br>Delete script confirmation.</p>

<p><a href="images/screens/script-import-picker.png"><img src="images/screens/script-import-picker.png" alt="Choose a script archive" width="280"></a><br>Choose a script archive.</p>

<p><a href="images/screens/script-import-confirm.png"><img src="images/screens/script-import-confirm.png" alt="Review an archive before importing" width="280"></a><br>Review an archive before importing.</p>

</details>
