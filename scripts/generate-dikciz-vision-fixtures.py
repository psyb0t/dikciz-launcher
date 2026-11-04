"""Regenerate deterministic text and QR image fixtures in the Android Lab tool image."""

import hashlib
import importlib.util
import logging
from pathlib import Path
import struct
import subprocess
import urllib.request
import zlib

LOGGER = logging.getLogger(__name__)
ROOT = Path(__file__).resolve().parent.parent
SCRATCH = ROOT / ".testing" / "vision-fixtures"
SOURCE_URL = "https://raw.githubusercontent.com/nayuki/QR-Code-generator/720f62bddb7226106071d4728c292cb1df519ceb/python/qrcodegen.py"
SOURCE_SHA256 = "b089855caf16185c61421ea4927c1b213cf9468940d71fa8ab11ef83662dcc84"
SOURCE_MAXIMUM_BYTES = 65536
PAYLOAD = "DIKCIZ-FIXTURE-428"
SCALE = 12
BORDER = 4


def qr_module():
    source = SCRATCH / "qrcodegen.py"
    if source.exists():
        data = source.read_bytes()
    else:
        with urllib.request.urlopen(SOURCE_URL, timeout=30) as response:
            data = response.read(SOURCE_MAXIMUM_BYTES + 1)
        if len(data) > SOURCE_MAXIMUM_BYTES:
            raise ValueError("QR fixture source exceeds the byte bound")
    if hashlib.sha256(data).hexdigest() != SOURCE_SHA256:
        raise ValueError("QR fixture source checksum mismatch")
    if not source.exists():
        source.write_bytes(data)
    specification = importlib.util.spec_from_file_location("dikciz_fixture_qr", source)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def generate_qr():
    module = qr_module()
    qr = module.QrCode.encode_text(PAYLOAD, module.QrCode.Ecc.MEDIUM)
    edge = (qr.get_size() + BORDER * 2) * SCALE
    pixels = bytearray()
    for y in range(edge):
        pixels.append(0)
        for x in range(edge):
            pixels.append(0 if qr.get_module(x // SCALE - BORDER, y // SCALE - BORDER) else 255)
    header = struct.pack(">IIBBBBB", edge, edge, 8, 0, 0, 0, 0)
    content = b"\x89PNG\r\n\x1a\n"
    content += png_chunk(b"IHDR", header) + png_chunk(b"IDAT", zlib.compress(bytes(pixels), 9)) + png_chunk(b"IEND", b"")
    (ROOT / "tests/fixtures/companion-vision-qr.png").write_bytes(content)


def main():
    SCRATCH.mkdir(parents=True, exist_ok=True)
    classes = SCRATCH / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run(["javac", "-d", str(classes), str(ROOT / "tests/fixtures/VisionImageFixture.java")], check=True)
    subprocess.run(["java", "-Djava.awt.headless=true", "-cp", str(classes), "VisionImageFixture",
                    str(ROOT / "tests/fixtures/companion-vision-text.png")], check=True)
    generate_qr()
    LOGGER.info("Generated text and QR vision fixtures")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
