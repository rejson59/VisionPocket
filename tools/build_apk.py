#!/usr/bin/env python3
"""
VisionPocket APK builder — no Gradle, no Android SDK download.

Pipeline:
  1. aapt2 compile + link      -> resources + binary manifest + R.java
  2. ECJ (Eclipse compiler)    -> .class files  (against android.jar)
  3. D8                        -> classes.dex
  4. custom zip packer         -> aligned APK (resources.arsc stored + 4-byte aligned)
  5. apksigner                 -> signed APK (v2 scheme)

Toolchain discovery order: environment variable, then well-known sandbox paths.
"""
import os
import sys
import glob
import struct
import zipfile
import zlib
import shutil
import subprocess
import argparse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app")
TOOLS_DEFAULT = "/tmp/vptools"

MIN_APK_TOOLS = os.path.join(TOOLS_DEFAULT, "node_modules/@drxiaozhi/minapk/tools")


def find_java():
    cand = os.environ.get("VP_JAVA")
    if cand and os.path.exists(cand):
        return cand
    try:
        import jdk4py
        return jdk4py.JAVA
    except Exception:
        pass
    p = shutil.which("java")
    if p:
        return p
    raise SystemExit("No java found — install jdk4py or set VP_JAVA")


def find_keytool(java_path):
    cand = os.path.join(os.path.dirname(java_path), "keytool")
    if os.path.exists(cand):
        return cand
    p = shutil.which("keytool")
    if p:
        return p
    raise SystemExit("No keytool found next to java")


def first_existing(paths):
    for p in paths:
        if os.path.exists(p):
            return p
    raise SystemExit("None of these exist: " + ", ".join(paths))


def run(cmd, desc, env=None):
    print(f"==> {desc}")
    print("    " + " ".join(str(c) for c in cmd[:12]) + (" ..." if len(cmd) > 12 else ""))
    r = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, env=env)
    if r.returncode != 0:
        print(r.stdout[-4000:])
        print(r.stderr[-4000:])
        raise SystemExit(f"FAILED: {desc}")
    if r.stdout.strip():
        print("    " + r.stdout.strip()[:300].replace("\n", "\n    "))
    return r


def make_zip(out_path, entries):
    """Minimal zip writer with STORED-entry alignment support.
    entries: list of (name, data, compress, align)"""
    with open(out_path, "wb") as f:
        central = []
        for name, data, compress, align in entries:
            name_bytes = name.encode("utf-8")
            crc = zlib.crc32(data) & 0xFFFFFFFF
            if compress:
                c = zlib.compressobj(9, zlib.DEFLATED, -15)
                payload = c.compress(data) + c.flush()
                method = 8
            else:
                payload = data
                method = 0
            extra = b""
            if align and method == 0:
                cur = f.tell() + 30 + len(name_bytes)
                pad = (align - (cur % align)) % align
                if pad < 4:
                    pad += 4
                extra = struct.pack("<HH", 0x0000, pad - 4) + b"\x00" * (pad - 4)
            offset = f.tell()
            f.write(struct.pack("<IHHHHHIIIHH", 0x04034b50, 20, 0, method, 0, 0,
                                crc, len(payload), len(data), len(name_bytes), len(extra)))
            f.write(name_bytes)
            f.write(extra)
            f.write(payload)
            central.append((name, offset, crc, len(payload), len(data), method))
        cd_start = f.tell()
        for name, offset, crc, csize, usize, method in central:
            nb = name.encode("utf-8")
            f.write(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014b50, 20, 20, 0, method, 0, 0,
                                crc, csize, usize, len(nb), 0, 0, 0, 0, 0, offset))
            f.write(nb)
        cd_end = f.tell()
        f.write(struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, len(central), len(central),
                            cd_end - cd_start, cd_start, 0))


def ensure_keystore(java, keytool, keystore):
    if os.path.exists(keystore):
        return keystore
    print("==> Generating debug keystore (PKCS12)")
    subprocess.run([
        keytool, "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12",
        "-alias", "visionpocket", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10950", "-storepass", "visionpocket", "-keypass", "visionpocket",
        "-dname", "CN=VisionPocket Debug, OU=Dev, O=VisionPocket, C=PL",
    ], check=True, capture_output=True)
    return keystore


def ensure_lambda_stubs(java, build, androidjar):
    """Compile-time-only stubs for java.lang.invoke (like AGP's core-lambda-stubs.jar)
    so javac 8 accepts lambdas when bootclasspath is android.jar."""
    jar = os.path.join(build, "core-lambda-stubs.jar")
    if os.path.exists(jar):
        return jar
    src = os.path.join(build, "lambdastubs")
    cls = os.path.join(build, "lambdastubclasses")
    os.makedirs(os.path.join(src, "java/lang/invoke"), exist_ok=True)
    os.makedirs(cls, exist_ok=True)
    common = """package java.lang.invoke;
public class %s {}
"""
    with open(os.path.join(src, "java/lang/invoke/MethodType.java"), "w") as f:
        f.write(common % "MethodType")
    with open(os.path.join(src, "java/lang/invoke/MethodHandle.java"), "w") as f:
        f.write(common % "MethodHandle")
    with open(os.path.join(src, "java/lang/invoke/CallSite.java"), "w") as f:
        f.write(common % "CallSite")
    with open(os.path.join(src, "java/lang/invoke/MethodHandles.java"), "w") as f:
        f.write("""package java.lang.invoke;
public class MethodHandles {
    public static final class Lookup {}
}
""")
    with open(os.path.join(src, "java/lang/invoke/LambdaMetafactory.java"), "w") as f:
        f.write("""package java.lang.invoke;
public class LambdaMetafactory {
    public static CallSite metafactory(MethodHandles.Lookup caller, String invokedName,
            MethodType invokedType, MethodType samMethodType, MethodHandle implMethod,
            MethodType instantiatedMethodType) { return null; }
}
""")
    stubs = glob.glob(os.path.join(src, "**/*.java"), recursive=True)
    r = subprocess.run([java, "-cp", os.environ.get("VP_TOOLS_JAR",
                            os.path.join(TOOLS_DEFAULT, "node_modules/dataslope-tools-jar/tools.jar")),
                        "com.sun.tools.javac.Main", "-source", "8", "-target", "8",
                        "-bootclasspath", androidjar, "-nowarn", "-d", cls] + stubs,
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit("lambda stub compile failed: " + r.stderr[-2000:])
    class_files = glob.glob(os.path.join(cls, "**/*.class"), recursive=True)
    with zipfile.ZipFile(jar, "w") as z:
        for cf in class_files:
            arc = os.path.relpath(cf, cls)
            z.write(cf, arc)
    return jar


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "release"))
    ap.add_argument("--build", default="/tmp/vpbuild")
    ap.add_argument("--version-name", default="1.0")
    ap.add_argument("--version-code", default="1", type=str)
    args = ap.parse_args()

    java = find_java()
    keytool = find_keytool(java)
    aapt2 = first_existing([
        os.environ.get("VP_AAPT2", ""),
        os.path.join(TOOLS_DEFAULT, "node_modules/aaptjs3/bin/x64/linux/aapt2"),
    ])
    d8jar = first_existing([
        os.path.join(MIN_APK_TOOLS, "d8.jar"),
    ])
    ecjjar = first_existing([
        os.path.join(MIN_APK_TOOLS, "ecj-3.45.0.jar"),
    ])
    apksignerjar = first_existing([
        os.path.join(MIN_APK_TOOLS, "apksigner.jar"),
    ])
    androidjar = first_existing([
        os.environ.get("VP_ANDROID_JAR", ""),
        os.path.join(TOOLS_DEFAULT, "android-platforms/android-33/android.jar"),
        os.path.join(MIN_APK_TOOLS, "android.jar"),
    ])
    toolsjar = first_existing([
        os.environ.get("VP_TOOLS_JAR", ""),
        os.path.join(TOOLS_DEFAULT, "node_modules/dataslope-tools-jar/tools.jar"),
    ])
    keystore = ensure_keystore(java, keytool, os.path.join(ROOT, "tools", "vp-debug.keystore"))

    build = args.build
    gen = os.path.join(build, "gen")
    classes = os.path.join(build, "classes")
    dexdir = os.path.join(build, "dex")
    for d in (gen, classes, dexdir):
        os.makedirs(d, exist_ok=True)

    print(f"java={java}\naapt2={aapt2}\nandroid.jar={androidjar}")

    # 1. resources
    res_zip = os.path.join(build, "res.zip")
    base_apk = os.path.join(build, "base.apk")
    run([aapt2, "compile", "--dir", os.path.join(APP, "res"), "-o", res_zip], "aapt2 compile")
    run([aapt2, "link", "-o", base_apk, "-I", androidjar,
         "--manifest", os.path.join(APP, "AndroidManifest.xml"),
         "-R", res_zip, "--java", gen,
         "--min-sdk-version", "26", "--target-sdk-version", "33",
         "--version-code", args.version_code, "--version-name", args.version_name,
         "--auto-add-overlay"], "aapt2 link")

    # 2. java sources — javac 8 from tools.jar, platform classes = android.jar
    stubs_jar = ensure_lambda_stubs(java, build, androidjar)
    sources = glob.glob(os.path.join(gen, "**/*.java"), recursive=True)
    sources += glob.glob(os.path.join(APP, "java", "**/*.java"), recursive=True)
    print(f"==> javac compile ({len(sources)} sources)")
    r = subprocess.run([java, "-cp", toolsjar, "com.sun.tools.javac.Main",
                        "-source", "8", "-target", "8",
                        "-bootclasspath", f"{androidjar}:{stubs_jar}",
                        "-classpath", f"{androidjar}:{stubs_jar}",
                        "-encoding", "UTF-8", "-nowarn",
                        "-d", classes] + sources,
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout[-8000:])
        print(r.stderr[-4000:])
        raise SystemExit("FAILED: javac compile")
    if r.stderr.strip():
        print("    (warnings) " + r.stderr.strip()[:500])
    class_files = glob.glob(os.path.join(classes, "**/*.class"), recursive=True)
    if not class_files:
        raise SystemExit("No .class files produced")
    print(f"    {len(class_files)} classes")

    # 3. dex
    dex_out = os.path.join(dexdir, "classes.dex")
    if os.path.exists(dex_out):
        os.remove(dex_out)
    run([java, "-Xmx1g", "-cp", d8jar, "com.android.tools.r8.D8",
         "--release", "--min-api", "26", "--lib", androidjar,
         "--output", dexdir] + class_files, "D8 dex")
    if not os.path.exists(dex_out):
        raise SystemExit("classes.dex missing")

    # 4. pack aligned APK
    aligned = os.path.join(build, "aligned.apk")
    zin = zipfile.ZipFile(base_apk)
    entries = []
    for info in zin.infolist():
        data = zin.read(info.filename)
        align = 4 if info.filename == "resources.arsc" else None
        entries.append((info.filename, data, info.filename != "resources.arsc", align))
    zin.close()
    entries.append(("classes.dex", open(dex_out, "rb").read(), True, None))
    print("==> Packing aligned APK")
    make_zip(aligned, entries)

    # 5. sign
    os.makedirs(args.out, exist_ok=True)
    final = os.path.join(args.out, f"VisionPocket-v{args.version_name}.apk")
    if os.path.exists(final):
        os.remove(final)
    run([java, "-jar", apksignerjar, "sign",
         "--ks", keystore, "--ks-key-alias", "visionpocket",
         "--ks-pass", "pass:visionpocket", "--key-pass", "pass:visionpocket",
         "--out", final, aligned], "apksigner sign")

    run([java, "-jar", apksignerjar, "verify", "--print-certs", final], "apksigner verify")

    size = os.path.getsize(final)
    print(f"\n✓ DONE: {final} ({size/1e6:.2f} MB)")


if __name__ == "__main__":
    main()
