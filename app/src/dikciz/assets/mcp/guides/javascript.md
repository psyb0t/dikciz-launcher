# Background JavaScript

Choose language javascript in a script record; omitted language stays lua. Public scripts/<id>/script.json metadata has id/title/enabled/apiVersion/language. Source is main.js, state.json holds explicit state, run-status.json reports completion. Native Settings > Scripts > New JavaScript script creates one. Expanded configGet/configReplace includes language/source/state. File watching reloads valid main.js changes.

Implement synchronous function onEvent(event). It runs in an embedded interpreter on the existing automation worker, without a WebView. event has type/source/timestampMilliseconds/coalescingKey/payload/time.hour24/time.minute. context has scriptId/state/widgets/androidAccess and optional accessibility. Inputs are read-only and globals reset each invocation.

Return {status:"short text",actions:[{type:"patchState",values:{count:1}},{type:"emitEvent",event:"example.counter.updated",payload:{count:1}}]}. Typed actions and policy checks match Lua. An enabled script needs an enabled policy and subscription. For custom subscriptions set event to the actual name, not custom plus customEventName. manual needs manualTrigger; alarm uses capability alarm and alarmIntervalMilliseconds >=60000. Existing Android background restrictions apply.

context.request(objectOrJsonString) returns the canonical command envelope; context.requestJson returns JSON text. Inspect errors, permissionRequest and actual operation outcomes. Direct commands have owner authority, like Lua; returned-action policies do not sandbox those commands. Enable only trusted code. Network entrypoints and read-only chat retain their normal mutation checks.

No DOM, browser timers, fetch, Node.js or Promise completion. Returning a Promise is invalid_result. Use events and commands. emitEvent reaches subscribed Lua scripts and currently rendered HTML widgets. Closing a page does not stop a background JS script. This does not keep widget WebViews running in the background.

Limits: 50000 observed instructions, setup CPU250ms, handler CPU50ms, observed memory delta2MiB, interpreter stack128, serialized result1MiB. Host command CPU is excluded. These are runtime checks, not a separate heap/process sandbox. Java class access is blocked. Syntax/execution errors record invalid_javascript; invalid returns record invalid_result; unexpected engine failures record runtime_unavailable. Other limits use shared failure statuses. Poll script state/run status and logs after a trigger.

Archives use .dikciz-script.zip with kind dikciz-javascript-script and exactly archive.json/script.json/main.js/state.json. Lua archives keep their old kind/source. Imported scripts still need subscriptions. See guides/lua and recipes/event-setup for shared policy and event wiring.
