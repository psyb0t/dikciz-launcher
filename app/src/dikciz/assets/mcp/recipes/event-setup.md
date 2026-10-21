# Two-input event pipeline

Goal: `example.start` and `example.stop` drive a Lua script. It persists a Boolean, emits `example.changed`, and a rendered HTML widget displays and acknowledges the result.

Read [Lua source](dikciz://docs/recipes/event-lua) and [HTML source](dikciz://docs/recipes/event-widget). Install the script as `example-events` and one HTML widget. Use their real IDs and address. Follow [script installation](dikciz://docs/recipes/install-script) and [widget installation](dikciz://docs/recipes/install-widget); preserve existing configuration.

Create an enabled automation policy:

```json
{"id":"example-events","title":"Example events","enabled":true,"capabilities":["manualTrigger"],"actions":["patchState","emitEvent"]}
```

Create its enabled subscription record:

```json
{"scriptId":"example-events","policyId":"example-events","enabled":true,"subscriptions":[{"event":"example.start","minimumIntervalMilliseconds":0},{"event":"example.stop","minimumIntervalMilliseconds":0}]}
```

Set the widget record's subscription field:

```json
[{"event":"example.changed","minimumIntervalMilliseconds":0}]
```

Select the widget's page and wait for its rendered document. Dispatch through `dikciz_automation_dispatch`:

```json
{"sourceWidgetAddress":"1H1V-example-widget","actions":[{"type":"emitEvent","event":"example.start","payload":{}}]}
```

Replace the example address with the widget's actual full address. Read the Lua run status and saved `state.running: true`. Wait for the widget's saved `state.running: true` and visible `Running`. Send `example.stop`, then verify both states become false and the widget shows `Stopped`.

Publication is asynchronous. An `executed` action alone does not prove either receiver ran. A hidden HTML widget does not receive events; Lua handles durable background work. Avoid resetting configuration to install this example.
