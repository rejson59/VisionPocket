# Building VisionPocket

This project builds a signed, installable APK **without** the Android SDK,
without Gradle and without any downloads from Google. The pipeline is a single
Python script:

```bash
python3 tools/build_apk.py
# -> release/VisionPocket-v1.0.apk
```

## Pipeline steps

| Step | Tool | What it does |
|---|---|---|
| 1 | `aapt2 compile` + `aapt2 link` | compiles `app/res`, links binary `AndroidManifest.xml`, generates `R.java` |
| 2 | `javac` (JDK 8 `tools.jar`) | compiles `app/java/**` + generated `R.java` against `android.jar` (`-bootclasspath`), with generated `core-lambda-stubs.jar` on the bootclasspath so lambdas are accepted |
| 3 | `D8` | converts `.class` files to `classes.dex` (desugars lambdas, `--min-api 26`) |
| 4 | custom zip packer | rebuilds the APK with `resources.arsc` **stored uncompressed and 4-byte aligned** (required for targetSdk ≥ 30) and adds `classes.dex` |
| 5 | `apksigner` | signs with the committed debug key (v1 + v2 schemes) and verifies |

## Toolchain discovery

`tools/build_apk.py` looks for tools in this order:

1. Environment overrides: `VP_JAVA`, `VP_AAPT2`, `VP_TOOLS_JAR`, `VP_ANDROID_JAR`.
2. `python3 -m pip install jdk4py` provides `java` + `keytool` (JRE 25, works fine).
3. Well-known sandbox paths under `/tmp/vptools`:
   - `aapt2` — from the npm package `aaptjs3` (`npm i aaptjs3`)
   - `d8.jar`, `apksigner.jar`, `ecj-3.45.0.jar`, fallback `android.jar` —
     from the npm package `@drxiaozhi/minapk`
   - `android.jar` (API 33) — via a partial git clone of
     `github.com/Sable/android-platforms` (`git sparse-checkout set android-33`)
   - `tools.jar` (JDK 8 javac) — from the npm package `dataslope-tools-jar`

On a machine with a normal Android SDK you can point `VP_ANDROID_JAR` at
`<sdk>/platforms/android-33/android.jar` and `VP_JAVA` at any JDK 11+ `java`.

## Signing

`tools/vp-debug.keystore` (PKCS12, alias `visionpocket`, password `visionpocket`)
is committed so that consecutive builds produce the same signature — you can
`adb install -r` rebuilds over an existing installation without uninstalling.

For production, generate your own release key and pass it via the script
(edit `ensure_keystore` / the `apksigner` invocation in `tools/build_apk.py`).

## Requirements

- Python 3.8+ (stdlib only)
- `java` on PATH or via `jdk4py`
- aapt2, D8, apksigner jars as described above (Linux x86_64 aapt2 binary)

## Notes on third-party tools

The build bundles/uses: AOSP `aapt2` (Apache 2.0), R8/D8 (Apache 2.0),
`apksigner` (Apache 2.0), JDK 8 `tools.jar` (GPLv2+CE) and Sable's copy of the
Android platform stubs (Apache 2.0). None of these are distributed inside the
built APK — the APK contains only VisionPocket code, resources and assets.
