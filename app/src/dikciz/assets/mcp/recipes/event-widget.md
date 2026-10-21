# Event pipeline HTML source

Put this in the widget's `html` field:

```html
<output id="event-status">Waiting</output>
```

Put this in its `javascript` field:

```javascript
const output = document.getElementById("event-status");
function show(running) {
  output.textContent = running ? "Running" : "Stopped";
}
window.addEventListener("dikciz-ready", () => {
  if (typeof selfWidget.state.running === "boolean") {
    show(selfWidget.state.running);
  }
  dikciz.onEvent(async event => {
    if (event.type !== "example.changed") return;
    const running = event.payload.running === true;
    show(running);
    try {
      await selfWidget.patchState({ running });
    } catch (error) {
      console.error("Event acknowledgement failed", error.message);
    }
  });
});
```

Set `eventSubscriptions` in the saved widget record to `[{"event":"example.changed","minimumIntervalMilliseconds":0}]`. The field is not part of the typed `patchWidget` allowlist. Save the widget JSON or use the complete assembled configuration while preserving unrelated data.

Keep the page selected so the document remains rendered. The visible text changes when the event arrives. The state patch is a persisted acknowledgement that can be read remotely through `widgetGet` or `configGet`. After rendering is recreated, the saved Boolean restores the display.

[Setup and verification](dikciz://docs/recipes/event-setup). [Lua handler](dikciz://docs/recipes/event-lua).
