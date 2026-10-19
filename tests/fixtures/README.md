# Live home fixture

[`dikciz-ui-fixture.json`](dikciz-ui-fixture.json) is the version 4 configuration used by the [live suite](../live_control/README.md). It is an expanded control-plane document with inline pages and widgets, rather than the on-device reference tree in the [configuration guide](../../docs/dikciz-configuration.md).

The fixture has a 4 by 6 grid and three pages: `home` at the origin, `notes` to its right, and `activity` below it. HTML cards give tests stable IDs, visible labels, fixed cells, and state updates without an external provider. The `focus` checkbox persists state through `selfWidget.patch`; the detail card listens for state updates. Automation policies and script lists start empty.

`tests/live_control/configuration.py` loads this file. The autouse fixture replaces and reads back configuration before each scenario. Shared IDs and expected geometry live in `tests/live_control/constants.py`; update their assertions together when changing this document. The lab runner accepts only this checked-in fixture path.

Cleanup restores the bundled product starter home, so manual inspection after testing shows the launcher rather than fixture cards.
