"""Package lifecycle fixtures for the isolated Android emulator."""

from .constants import CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE


AUTOMATION_PACKAGE_LIFECYCLE_CAPABILITY = "appPackagesMetadata"
AUTOMATION_PACKAGE_LIFECYCLE_COALESCING_KEY = "package-lifecycle"
AUTOMATION_PACKAGE_LIFECYCLE_EVENT = "packageChanged"
AUTOMATION_PACKAGE_LIFECYCLE_INITIAL_STATE = {
    "changedSeen": False,
    "installedSeen": False,
    "packageChange": "waiting",
    "packageName": "",
    "removedSeen": False,
    "updatedSeen": False,
}
AUTOMATION_PACKAGE_LIFECYCLE_MCP_CONFIG_GET_REQUEST_ID = 69
PACKAGE_CHANGE_CHANGED = "changed"
PACKAGE_CHANGE_INSTALLED = "installed"
PACKAGE_CHANGE_REMOVED = "removed"
PACKAGE_CHANGE_UPDATED = "updated"
AUTOMATION_PACKAGE_LIFECYCLE_SOURCE = (
    "function on_event(event)\n"
    f'  if event.type ~= "{AUTOMATION_PACKAGE_LIFECYCLE_EVENT}" '
    f'or event.payload.packageName ~= "{CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE}" then return end\n'
    "  return { type = \"patchState\", values = { "
    f"changedSeen = context.state.changedSeen or event.payload.change == \"{PACKAGE_CHANGE_CHANGED}\", "
    f"installedSeen = context.state.installedSeen or event.payload.change == \"{PACKAGE_CHANGE_INSTALLED}\", "
    "packageChange = event.payload.change, packageName = event.payload.packageName, "
    f"removedSeen = context.state.removedSeen or event.payload.change == \"{PACKAGE_CHANGE_REMOVED}\", "
    f"updatedSeen = context.state.updatedSeen or event.payload.change == \"{PACKAGE_CHANGE_UPDATED}\""
    " } }\n"
    "end"
)
AUTOMATION_PACKAGE_LIFECYCLE_STATE_CHANGE_KEY = "packageChange"
AUTOMATION_PACKAGE_LIFECYCLE_STATE_CHANGED_SEEN_KEY = "changedSeen"
AUTOMATION_PACKAGE_LIFECYCLE_STATE_INSTALLED_SEEN_KEY = "installedSeen"
AUTOMATION_PACKAGE_LIFECYCLE_STATE_PACKAGE_KEY = "packageName"
AUTOMATION_PACKAGE_LIFECYCLE_STATE_REMOVED_SEEN_KEY = "removedSeen"
AUTOMATION_PACKAGE_LIFECYCLE_STATE_UPDATED_SEEN_KEY = "updatedSeen"
