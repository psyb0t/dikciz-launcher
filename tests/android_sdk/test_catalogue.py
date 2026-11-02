import gzip
import hashlib
import importlib.util
import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("android_sdk_generator", ROOT / "scripts/generate-dikciz-android-sdk.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)

FIXTURE = r'''package android {
  public final class Manifest.permission {
    field public static final String READ_FIXTURE = "android.permission.READ_FIXTURE";
  }
}
package java.lang {
  public class Object {
    method public boolean equals(@Nullable Object);
  }
  public final class String {
    method public int length();
  }
}
package android.fixture {
  public class Parent<T extends java.lang.Number> {
    method public T value();
  }
  public class Example extends android.fixture.Parent<java.lang.Integer> implements java.lang.Runnable java.io.Closeable {
    ctor public Example();
    method @NonNull @RequiresPermission(android.Manifest.permission.READ_FIXTURE) public java.util.List<java.lang.String> read(@Nullable java.lang.String, @NonNull java.util.Map<java.lang.String, @Nullable java.lang.Integer>);
    method public <T extends java.lang.Number> T typed(T);
    method public void write(int, java.lang.String...);
    field public static final java.util.Map<java.lang.String, java.lang.Integer> VALUES;
  }
}
'''


def fixture_config():
    source = FIXTURE.encode()
    return source, {"sdkInt": 36, "sha256": hashlib.sha256(source).hexdigest()}, {
        "members": {"android.fixture.Example": ["read(java.lang.String,java.util.Map)"]},
        "fields": {}, "blockedClasses": [], "blockedMembers": {},
    }


def test_signature_contracts_and_generics():
    classes = GENERATOR.parse_signatures(FIXTURE)
    example = classes["android.fixture.Example"]
    assert example["parents"] == ["android.fixture.Parent", "java.lang.Runnable", "java.io.Closeable"]
    members = {m["id"]: m for m in example["members"]}
    read = members["read(java.lang.String,java.util.Map)"]
    assert read["returns"] == "java.util.List"
    assert [p["nullable"] for p in read["parameters"]] == [True, False]
    assert members["typed(java.lang.Number)"]["returns"] == "java.lang.Number"
    assert members["write(int,java.lang.String[])"]["parameters"][1]["type"] == "java.lang.String[]"
    assert members["field:VALUES"]["type"] == "java.util.Map"
    assert classes["android.Manifest.permission"]["binaryName"] == "android.Manifest$permission"


def test_generation_integrity_permissions_policy_and_repeatability(tmp_path, monkeypatch):
    monkeypatch.setattr(GENERATOR, "OUTPUT", tmp_path)
    source, config, policy = fixture_config()
    metadata = GENERATOR.generate(source, config, policy)
    first = {p.name: p.read_bytes() for p in tmp_path.iterdir()}
    assert GENERATOR.generate(source, config, policy) == metadata
    assert {p.name: p.read_bytes() for p in tmp_path.iterdir()} == first
    data = gzip.decompress(first["members.bin"])
    assert hashlib.sha256(data).hexdigest() == metadata["dataSha256"]
    assert len(data) == metadata["dataBytes"]
    for entry in metadata["classes"]:
        document = json.loads(data[entry["offset"]:entry["offset"] + entry["bytes"]])
        assert document["name"] == entry["name"]
        if document["name"] == "android.fixture.Example":
            read = next(m for m in document["members"] if m["name"] == "read")
            assert read["readOnly"] is True
            assert read["permissions"][0]["permissions"] == ["android.permission.READ_FIXTURE"]
            assert all(not m["readOnly"] for m in document["members"] if m["name"] != "read")


@pytest.mark.parametrize("failure", ["hash", "class", "member"])
def test_generation_fails_closed_before_output(tmp_path, monkeypatch, failure):
    monkeypatch.setattr(GENERATOR, "OUTPUT", tmp_path / "generated")
    source, config, policy = fixture_config()
    if failure == "hash":
        config["sha256"] = "0" * 64
    elif failure == "class":
        policy["members"]["android.Missing"] = ["read()"]
    else:
        policy["members"]["android.fixture.Example"] = ["getDoesNotExist()"]
    with pytest.raises(ValueError):
        GENERATOR.generate(source, config, policy)
    assert not GENERATOR.OUTPUT.exists()


def test_committed_catalogue_complete_index_and_type_resolution():
    metadata = json.loads((ROOT / "app/src/dikciz/assets/android-sdk/index.json").read_text())
    data = gzip.decompress((ROOT / "app/src/dikciz/assets/android-sdk/members.bin").read_bytes())
    assert hashlib.sha256(data).hexdigest() == metadata["dataSha256"]
    assert len(data) == metadata["dataBytes"]
    assert metadata["classCount"] == len(metadata["classes"]) > 5000
    names = {c["name"] for c in metadata["classes"]} | {"void", "boolean", "byte", "char", "short", "int", "long", "float", "double"}
    member_count = 0
    offset = 0
    for entry in metadata["classes"]:
        assert entry["offset"] == offset
        offset += entry["bytes"]
        document = json.loads(data[entry["offset"]:offset])
        assert document["name"] == entry["name"]
        ids = [member["id"] for member in document["members"]]
        assert len(ids) == len(set(ids)), document["name"]
        member_count += len(ids)
        for member in document["members"]:
            types = [p["type"] for p in member.get("parameters", [])]
            types += [member.get("returns", member.get("type"))]
            for value in types:
                while value.endswith("[]"):
                    value = value[:-2]
                assert value in names, (document["name"], member["id"], value)
    assert member_count == metadata["memberCount"]
    assert offset == len(data)


def test_nested_annotation_quotes_and_opaque_permission_requirements():
    signature = 'java.util.Map<String, Integer>, @Example(value="a,<)[}") String, int'
    assert GENERATOR.split_types(signature) == ['java.util.Map<String, Integer>', '@Example(value="a,<)[}") String', 'int']
    declaration = '@RequiresPermission(anyOf={android.Manifest.permission.READ_FIXTURE, "carrier privileges"}) public void read();'
    group = GENERATOR.permission_annotations(declaration)[0]
    assert group["mode"] == "any"
    assert group["unresolvedRequirements"] == ["carrier privileges"]
    assert GENERATOR.permission_annotations('@RequiresPermission(value="android.permission.READ_FIXTURE", apis="..22") public void read();')[0]["apis"] == "..22"
