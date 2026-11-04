"""Generate a signed base/config-split pair using the pinned lab SDK tools."""

from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / ".testing/split-fixture"
SOURCE = ROOT / "tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk"
MANIFEST = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="eu.psyb0t.dikciz.fixture" android:versionCode="1" split="config.fixture">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
 <application android:hasCode="false"/>
</manifest>
"""


def main():
    if not SOURCE.is_file():
        raise FileNotFoundError("Build the configurable provider fixture first")
    tools = list(Path("/opt/android-sdk/build-tools").iterdir())
    if len(tools) != 1:
        raise ValueError("Expected exactly the pinned Android Lab build-tools version")
    tools = tools[0]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = OUTPUT / "AndroidManifest.xml"
    manifest.write_text(MANIFEST)
    key = OUTPUT / "fixture.jks"
    if not key.exists():
        subprocess.run(["keytool", "-genkeypair", "-keystore", str(key),
                        "-storepass", "EXAMPLE-DO-NOT-USE", "-keypass", "EXAMPLE-DO-NOT-USE",
                        "-alias", "fixture", "-dname", "CN=Dikciz emulator fixture",
                        "-keyalg", "RSA", "-keysize", "2048", "-validity", "30"], check=True)
    unsigned = OUTPUT / "config-unsigned.apk"
    subprocess.run([str(tools / "aapt2"), "link", "--manifest", str(manifest),
                    "-I", "/opt/android-sdk/platforms/android-36/android.jar",
                    "-o", str(unsigned)], check=True)
    for source, name in ((SOURCE, "base.apk"), (unsigned, "config.apk")):
        aligned = OUTPUT / ("aligned-" + name)
        target = OUTPUT / name
        subprocess.run([str(tools / "zipalign"), "-f", "4", str(source), str(aligned)], check=True)
        subprocess.run([str(tools / "apksigner"), "sign", "--ks", str(key),
                        "--ks-key-alias", "fixture", "--ks-pass", "pass:EXAMPLE-DO-NOT-USE",
                        "--key-pass", "pass:EXAMPLE-DO-NOT-USE", "--out", str(target), str(aligned)], check=True)
        subprocess.run([str(tools / "apksigner"), "verify", str(target)], check=True)
    print("Generated a signed base/config-split fixture pair")


if __name__ == "__main__":
    main()
