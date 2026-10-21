# Event pipeline Lua source

Use this as `main.lua` for the enabled `example-events` script. Its policy must allow `patchState` and `emitEvent`, with `manualTrigger` for both custom subscriptions. See [setup](dikciz://docs/recipes/event-setup).

```lua
function on_event(event)
  if event.type ~= "example.start" and event.type ~= "example.stop" then
    return { status = "Waiting" }
  end
  local running = event.type == "example.start"
  return {
    status = running and "Running" or "Stopped",
    actions = {
      { type = "patchState", values = { running = running } },
      {
        type = "emitEvent",
        event = "example.changed",
        payload = { running = running }
      }
    }
  }
end
```

The first action persists script state. The second publishes the result for rendered HTML subscribers. Inspect both action outcomes and the final script run status. This handler does not emit either input event, so it cannot loop back into its own subscriptions.

[HTML receiver](dikciz://docs/recipes/event-widget).
