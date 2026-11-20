"""Check module packaging without installing or executing the APK.

Usage: python script/verify_module_apk.py path/to/app.apk
"""

import argparse
import struct
import zipfile
from pathlib import Path


def uleb(data, offset):
    value = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7


def dex_classes(data):
    """Read class definitions, superclasses, and declared method names."""
    def pair(offset):
        return struct.unpack_from("<II", data, offset)

    count, offset = pair(56)
    strings = []
    for index in range(count):
        string_offset = struct.unpack_from("<I", data, offset + 4 * index)[0]
        _, string_offset = uleb(data, string_offset)
        end = data.index(b"\0", string_offset)
        strings.append(data[string_offset:end].decode("utf-8", errors="replace"))
    count, offset = pair(64)
    types = [strings[struct.unpack_from("<I", data, offset + 4 * i)[0]] for i in range(count)]
    count, offset = pair(88)
    methods = [strings[struct.unpack_from("<I", data, offset + 8 * i + 4)[0]] for i in range(count)]
    count, offset = pair(96)
    for index in range(count):
        class_index, _, super_index, _, _, _, class_data, _ = struct.unpack_from("<8I", data, offset + 32 * index)
        names = []
        if class_data:
            sizes = []
            for _ in range(4):
                value, class_data = uleb(data, class_data)
                sizes.append(value)
            for _ in range(sizes[0] + sizes[1]):
                _, class_data = uleb(data, class_data)
                _, class_data = uleb(data, class_data)
            for size in sizes[2:]:
                method_index = 0
                for _ in range(size):
                    delta, class_data = uleb(data, class_data)
                    method_index += delta
                    _, class_data = uleb(data, class_data)
                    _, class_data = uleb(data, class_data)
                    names.append(methods[method_index])
        yield types[class_index], (None if super_index == 0xFFFFFFFF else types[super_index]), names


def verify(path):
    with zipfile.ZipFile(path) as apk:
        entries = apk.read("META-INF/xposed/java_init.list").decode().splitlines()
        assert entries == ["com.houvven.guise.hook.HookEntry"], entries
        properties = dict(line.split("=", 1) for line in apk.read("META-INF/xposed/module.prop").decode().splitlines() if "=" in line)
        assert properties["minApiVersion"] == properties["targetApiVersion"] == "102"
        assert properties["staticScope"] == "false"
        assert properties["autoHotReload"] == "false"
        classes = {}
        for name in apk.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                classes.update((name, (parent, methods)) for name, parent, methods in dex_classes(apk.read(name)))
        entry = classes["Lcom/houvven/guise/hook/HookEntry;"]
        assert entry[0] == "Lio/github/libxposed/api/XposedModule;", entry
        assert "<init>" in entry[1] and "onPackageReady" in entry[1], entry
        forbidden = ("Lio/github/libxposed/api/", "Lde/robv/android/xposed/", "Lcom/highcapable/yukihookapi/", "Lio/github/houvven/lservice/")
        assert not any(name.startswith(forbidden) for name in classes), "Framework API or legacy implementation was bundled"
        assert not any("manager.apk" in name or "mmkv" in name.lower() for name in apk.namelist())
        print(f"PASS {path}: API 102 metadata, entry constructor/callback retained, framework API excluded, legacy bridge absent")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="+")
    for path in parser.parse_args().apk:
        verify(path)
