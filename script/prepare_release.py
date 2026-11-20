"""Verify signed ABI APKs and prepare the GitHub Release assets. Does not run the app."""

import argparse
import hashlib
import json
import re
import shutil
import ssl
import struct
import subprocess
import zipfile
from pathlib import Path

from verify_module_apk import verify


ABIS = {"x86": (1, 3), "arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if line and not line.startswith("#") and "=" in line)


def tool(directory, name):
    for suffix in ("", ".exe", ".bat"):
        candidate = directory / (name + suffix)
        if candidate.is_file():
            return str(candidate)
    raise FileNotFoundError(name)


def inspect_apk(path, abi, version, code, fingerprint, tools):
    verify(path)
    signature = subprocess.check_output([
        tool(tools, "apksigner"), "verify", "--verbose", "--print-certs", str(path)
    ], text=True)
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
    require([value.lower() for value in certificates] == [fingerprint], "Unexpected signing certificate")
    for scheme in ("v2", "v3"):
        require(re.search(rf"Verified using {scheme} scheme.*: true", signature), f"Missing {scheme} signature")
    subprocess.run([tool(tools, "zipalign"), "-c", "-P", "16", "4", str(path)], check=True)
    badging = subprocess.check_output([tool(tools, "aapt2"), "dump", "badging", str(path)], text=True)
    require("package: name='com.houvven.guise'" in badging, "Unexpected application ID")
    require(f"versionName='{version}'" in badging and f"versionCode='{code}'" in badging, "Version mismatch")
    require(re.search(r"(?:minSdkVersion|sdkVersion):'26'", badging), "Unexpected minimum SDK")
    require("targetSdkVersion:'37'" in badging, "Unexpected target SDK")
    require("application-debuggable" not in badging, "Debuggable APK cannot be published")
    with zipfile.ZipFile(path) as apk:
        natives = [name for name in apk.namelist() if name.startswith("lib/") and name.endswith(".so")]
        require(natives and {name.split('/')[1] for name in natives} == {abi}, "APK contains the wrong ABI")
        for name in natives:
            elf = apk.read(name)
            require(elf[:4] == b"\x7fELF" and elf[5] == 1, "Invalid native library")
            require((elf[4], struct.unpack_from("<H", elf, 18)[0]) == ABIS[abi], "ELF architecture mismatch")
        dex_bytes = sum(info.file_size for info in apk.infolist() if info.filename.endswith(".dex"))
    return {"abi": abi, "bytes": path.stat().st_size, "dex_bytes": dex_bytes,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "certificate_sha256": fingerprint}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--certificate", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("build/release"))
    args = parser.parse_args()
    version_properties = properties(Path("app/version.properties"))
    version, code = version_properties["version.name"], version_properties["version.code"]
    require(re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version), "Invalid release version")
    fingerprint = Path(".github/release-signing-certificate.sha256").read_text().strip().lower()
    require(re.fullmatch(r"[0-9a-f]{64}", fingerprint), "Invalid expected certificate fingerprint")
    mapping = Path("app/build/outputs/mapping/release/mapping.txt")
    require(mapping.is_file() and " -> " in mapping.read_text(), "R8 mapping missing; refuse unoptimized release")
    source = Path("app/build/outputs/apk/release")
    metadata = json.loads((source / "output-metadata.json").read_text())
    require(len(metadata["elements"]) == 3, "Expected exactly three APKs")
    require(len(list(source.glob("*.apk"))) == 3, "Unexpected extra APKs in release output")
    args.output.mkdir(parents=True, exist_ok=True)
    reports = []
    seen = set()
    for element in metadata["elements"]:
        filters = element["filters"]
        require(len(filters) == 1 and filters[0]["filterType"] == "ABI", "Expected standalone ABI split")
        abi = filters[0]["value"]
        require(abi in ABIS and abi not in seen, "Unexpected or duplicate ABI")
        seen.add(abi)
        path = source / element["outputFile"]
        report = inspect_apk(path, abi, version, code, fingerprint, args.build_tools)
        name = f"Guise-{version}-{abi}.apk"
        shutil.copyfile(path, args.output / name)
        report["file"] = name
        reports.append(report)
    require(seen == set(ABIS), "Missing ABI")
    certificate = args.certificate.read_text()
    require("-----BEGIN CERTIFICATE-----" in certificate and "PRIVATE KEY" not in certificate, "Invalid public certificate")
    require(hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate)).hexdigest() == fingerprint,
            "Public certificate does not match APK signer")
    shutil.copyfile(args.certificate, args.output / "signing-certificate.pem")
    reports.sort(key=lambda report: report["abi"])
    (args.output / "release-manifest.json").write_text(json.dumps({
        "version": version, "version_code": int(code), "certificate_sha256": fingerprint,
        "r8": True, "resource_shrinking": True, "apks": reports,
    }, indent=2) + "\n")
    (args.output / "SHA256SUMS.txt").write_text("".join(
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
        for path in sorted(args.output.iterdir()) if path.suffix in {".apk", ".pem", ".json"}
    ))
    rows = "\n".join(f"| `{report['abi']}` | {report['bytes'] / 1024 / 1024:.2f} MiB |" for report in reports)
    notes = f"""Guise {version} — libxposed API 102 迁移版

### 下载

三个 APK 按架构独立打包，请选择适合设备的一个；x86 为 32 位版本。

| 架构 | APK 大小 |
| --- | --- |
{rows}

### 变化

- Kotlin / Compose / Material 3 与 Android 构建工具链升级。
- 迁移至 libxposed API 102，使用官方服务管理激活状态、配置共享和作用域请求。
- 启用 R8 代码优化、混淆与资源压缩；仅保留英文和简体中文界面资源。
- 使用独立的正式发布证书及 APK v2/v3 签名，提供公钥证书和 SHA-256 校验文件。

### 使用要求

- APK 最低 Android 8.0；还需要支持 libxposed API 102 和远程配置的框架，框架自身可能有更高的系统要求。
- 批准作用域请求后，重启目标应用使配置生效。
- 本版本使用新签名；若与已安装版本签名不同，需先备份配置、卸载旧版后安装。
- 已通过编译、Lint、签名、ABI、对齐和模块打包静态检查；尚未进行真机或模拟器运行测试。

签名证书 SHA-256：`{fingerprint}`
"""
    (args.output / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8")
    print(json.dumps(reports, indent=2))


if __name__ == "__main__":
    main()
