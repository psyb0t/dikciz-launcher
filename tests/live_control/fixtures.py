"""Isolated launcher fixtures for live domain tests."""

from .interactions import *


@pytest.fixture
def websocket_control() -> Generator[socket.socket, None, None]:
    with connected_websocket_control() as connection:
        original = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        try:
            yield connection
        finally:
            with connected_websocket_control() as restoration_connection:
                restored = AUTOMATION.request(
                    restoration_connection,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
                assert AUTOMATION.require_configuration(restored) == original


@pytest.fixture
def installed_configurable_provider_fixture() -> Generator[None, None, None]:
    package_name = install_configurable_provider_fixture()
    try:
        yield
    finally:
        uninstall_configurable_provider_fixture(package_name)


@pytest.fixture(autouse=True)
def isolated_launcher_fixture() -> Generator[None, None, None]:
    fixture = fixture_document()
    with connected_websocket_control() as connection:
        expected = AUTOMATION.require_configuration(
            AUTOMATION.request(
                connection,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=fixture,
            ),
        )
        current = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        if current != expected:
            drifted = sorted(
                key
                for key in set(current) | set(expected)
                if current.get(key) != expected.get(key)
            )
            raise AssertionError(
                f"configuration drifted after the replace in {drifted}: "
                f"expected={ {key: expected.get(key) for key in drifted} } "
                f"current={ {key: current.get(key) for key in drifted} }",
            )
    wait_for_physical_description_bounds(FIXTURE_WIDGET_TITLES["welcome"])
    try:
        yield
    finally:
        # The device must be left on the bundled starter home, never on this test
        # fixture, so manual inspection of the shared phone shows the real product.
        with connected_websocket_control() as connection:
            AUTOMATION.require_configuration(
                AUTOMATION.request(connection, AUTOMATION.TYPE_RESET),
            )
        restore_system_home()
        wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
