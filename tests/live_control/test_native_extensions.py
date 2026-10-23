"""Saved speech, USB host validation and local-binding parity."""

import base64
from . import *
from .test_native_data import native_call, PROTOCOLS


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_extensions_synthesis_and_usb(websocket_control, protocol):
    result = native_call(protocol, "speechSynthesize", {"text":"Saved speech fixture", "ratePercent":150})
    assert result["state"] == "synthesized" and result["sizeBytes"] > 44
    try:
        data = base64.b64decode(native_call(protocol, "fileRead", {"path":result["path"],"limit":64})["dataBase64"])
        assert data[:4] == b"RIFF" and data[8:12] == b"WAVE"
    finally:
        native_call(protocol, "fileManage", {"action":"delete","path":result["path"]})
    native_call(protocol, "speechSynthesize", {"text":"Test","voiceName":"missing voice"}, error="native_record_not_found")
    native_call(protocol, "speechSynthesize", {"text":" "}, error="validation_failed")
    devices = native_call(protocol, "usbDevices")
    assert devices["total"] == len(devices["items"])
    missing = 2147483647
    assert all(device["deviceId"] != missing for device in devices["items"])
    native_call(protocol, "usbRequestAccess", {"deviceId":missing}, error="native_record_not_found")
    control = {"deviceId":missing,"kind":"control","requestType":128,"request":0,"value":0,"index":0,"length":16}
    native_call(protocol, "usbTransfer", control, error="native_record_not_found")
    for fields in ({"length":65537},{"timeoutMilliseconds":0},{"kind":"arbitrary"},{"dataBase64":"bad"},{"interfaceIndex":0}):
        native_call(protocol, "usbTransfer", {**control,**fields}, error="validation_failed")
    native_call(protocol, "usbTransfer", {"deviceId":missing,"kind":"bulk","interfaceIndex":0,"endpointAddress":1,"dataBase64":"%%%"}, error="validation_failed")


def test_native_extensions_bindings_html(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
    javascript = """window.addEventListener('dikciz-ready', async () => {
      const speech = await dikciz.command('speechRecognitionStatus', {});
      const file = await dikciz.command('fileManage', {action:'stat',path:'/sdcard/Dikciz/config.json'});
      const jobs = await dikciz.command('jobsList', {limit:1});
      const usb = await dikciz.command('usbDevices', {});
      const audio = await dikciz.command('audioPlayback', {action:'status'});
      const microphone = await dikciz.command('microphoneSession', {action:'status'});
      let invalidGatt;
      try { await dikciz.command('bluetoothGatt', {action:'discover',address:'invalid'}); }
      catch (error) { invalidGatt = error.code; }
      await selfWidget.patchState({parityResponse:{speech,file,jobs,usb,audio,microphone,invalidGatt}});
    });"""
    try:
        AUTOMATION.request(websocket_control,"configReplace",config=parity_html_configuration(original,javascript))
        result = wait_for_parity_response(websocket_control)
        assert result["speech"]["defaultMode"] == "onDevice"
        assert result["file"]["kind"] == "file"
        assert result["jobs"]["limit"] == 1 and isinstance(result["usb"]["total"],int)
        assert result["audio"]["state"] in ("idle","stopped","completed","expired")
        assert result["microphone"]["state"] in ("idle","completed","cancelled")
        assert result["invalidGatt"] == "validation_failed"
    finally:
        AUTOMATION.request(websocket_control,"configReplace",config=original)


def test_native_extensions_bindings_lua(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
    source = """function on_event(event)
      if event.type ~= 'fixture.extensions' then return end
      local speech = context.requestJson({type='speechRecognitionStatus'})
      local file = context.requestJson({type='fileManage',action='stat',path='/sdcard/Dikciz/config.json'})
      local jobs = context.requestJson({type='jobsList',limit=1})
      local usb = context.requestJson({type='usbDevices'})
      local audio = context.requestJson({type='audioPlayback',action='status'})
      local microphone = context.requestJson({type='microphoneSession',action='status'})
      local invalidGatt = context.requestJson({type='bluetoothGatt',action='discover',address='invalid'})
      return {actions={{type='patchState',values={speech=speech,file=file,jobs=jobs,usb=usb,audio=audio,microphone=microphone,invalidGatt=invalidGatt}}}}
    end"""
    configured = automation_configuration(original,source,{},["manualTrigger"],["patchState"],[{"event":"fixture.extensions","minimumIntervalMilliseconds":0}])
    try:
        AUTOMATION.request(websocket_control,"configReplace",config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        AUTOMATION.request(websocket_control,"automationDispatch",sourceWidgetAddress="1H1V-welcome",actions=[{"type":"emitEvent","event":"fixture.extensions","payload":{}}])
        deadline=time.monotonic()+20
        while True:
            current=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
            script=next(item for item in current["scripts"] if item["id"]==AUTOMATION_LIVE_SCRIPT_ID)
            if "speech" in script["state"]:
                for key in ("speech","file","jobs","usb","audio","microphone"):
                    response=json.loads(script["state"][key])
                    assert response["type"]=="result",response
                assert json.loads(script["state"]["file"])["result"]["kind"]=="file"
                assert json.loads(script["state"]["invalidGatt"])["code"]=="validation_failed"
                break
            assert time.monotonic()<deadline,script
            time.sleep(.2)
    finally:
        AUTOMATION.request(websocket_control,"configReplace",config=original)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_extensions_location(websocket_control, protocol):
    from .test_native_data import grant_permissions
    from .test_capability_expansion import wait_native_control
    from .test_mcp_interoperability import rpc
    grant_permissions(("android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION"))
    restore_system_home()
    wait_native_control(protocol)
    status = native_call(protocol,"gnssStatus",{"timeoutMilliseconds":100,"limit":1})
    assert isinstance(status["available"],bool)
    if status["available"]:
        assert len(status["items"]) <= 1 and status["total"] >= len(status["items"])
    else:
        assert status["reason"] in ("no_gps_provider","registration_rejected","no_status_during_window")
    for fields in ({"action":"reverse","latitude":91,"longitude":0},{"action":"reverse","latitude":0,"longitude":181},{"action":"reverse","latitude":"0","longitude":0},{"action":"forward","query":""},{"action":"forward","query":"Test","latitude":0},{"action":"reverse","latitude":0},{"action":"forward","query":"Test","language":"../bad"}):
        native_call(protocol,"geocode",fields,error="validation_failed")
    status,_,body=rpc("tools/call",{"name":"dikciz_geocode","arguments":{"action":"reverse","latitude":0,"longitude":0,"timeoutMilliseconds":1}})
    assert status==200,body
    response=body["result"]
    if response.get("isError"):
        assert response["structuredContent"]["code"] in ("geocoder_unavailable","geocoder_timeout","geocoder_failed")
    else:
        assert isinstance(response["structuredContent"]["items"],list)
