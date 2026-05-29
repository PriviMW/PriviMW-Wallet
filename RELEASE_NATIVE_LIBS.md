# GitHub release — native libraries block

Use this when creating a **PriviMW-Wallet** GitHub release.

## Always include

- Release APK file + **APK SHA-256**

## When `app/src/main/jniLibs/**` changed

Add a **Native libraries** section. Copy from the committed files at the release tag:

- `app/src/main/jniLibs/arm64-v8a/PROVENANCE.json`
- `app/src/main/jniLibs/arm64-v8a/SHA256SUMS`

### Template

```markdown
### APK
- SHA-256: `<apk-sha256>`

### Native libraries
- Beam core: [PriviMW/beam](https://github.com/PriviMW/beam) @ `<beam_commit>`
- JNI release: `<beam_release_tag>` — https://github.com/PriviMW/beam/releases/tag/<beam_release_tag>
- `libwallet-jni.so`: `<hash from SHA256SUMS>`
- `libipfs-bindings.so`: `<hash from SHA256SUMS>`
- Provenance: `app/src/main/jniLibs/arm64-v8a/PROVENANCE.json` at wallet tag `<wallet-tag>`
```

## Kotlin/UI only (jniLibs unchanged)

```markdown
### Native libraries
Unchanged since vX.Y.Z (see that release for beam commit and .so hashes).
```

## Current pin (update when jniLibs are re-pinned)

| Field | Value |
|-------|--------|
| Beam commit | `a7ece39dd8e108d275974fe7a2369e40eca42bef` |
| JNI release | [`jni-7.5.14518-a7ece39dd8e108d275974fe7a2369e40eca42bef`](https://github.com/PriviMW/beam/releases/tag/jni-7.5.14518-a7ece39dd8e108d275974fe7a2369e40eca42bef) |
| libwallet-jni.so | `09541d25ea93e5f31cb20b9c13b61a6d29fe5d41e9aa1d6f3232b696a4e7fd52` |
| libipfs-bindings.so | `affcf7ced3130df50a0fe184f9ea7e5c1caea811c1d6d4b43be4341ca6afae9e` |
| Wallet commit (first pin) | `a390653` |
