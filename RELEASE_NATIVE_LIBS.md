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
| Beam commit | `26cc57bb00b718f22e55f5a915b32c770552455e` (HF6 hard fork, Boost 1.90) |
| asio-ipfs commit | `e3a1637cdcc83cdf9b57c1e4810cb20ad1d87f42` (PriviMW/asio-ipfs, Boost 1.90 port) |
| JNI release | [`jni-7.5.14592-26cc57bb00b718f22e55f5a915b32c770552455e`](https://github.com/PriviMW/beam/releases/tag/jni-7.5.14592-26cc57bb00b718f22e55f5a915b32c770552455e) |
| libwallet-jni.so | `c7da7b8064a9cde0b45d45843310f9a735eccef62e91edc283fee96152568f8f` |
| libipfs-bindings.so | `2309738b3d403c0e7960729eee22a136a3358bec6d25b6188435ef23c100404d` |
| Wallet commit (first pin) | `a390653` (originally v1.7.4 / jni-7.5.14518); re-pinned for HF6 at v1.8.0 |
