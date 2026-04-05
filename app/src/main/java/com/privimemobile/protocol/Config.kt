package com.privimemobile.protocol

/**
 * Protocol configuration constants.
 * Ports config.ts from the RN build — 1:1 match.
 */
object Config {
    /** Beam mainnet node pool (both EU and US, same as RN MAINNET_NODES) */
    val MAINNET_NODES = listOf(
        "eu-nodes.mainnet.beam.mw:8100",
        "us-nodes.mainnet.beam.mw:8100",
    )
    /** Default node (first in pool) */
    val DEFAULT_NODE = MAINNET_NODES[0]

    /** PriviMe contract CID — auto-set per build type (debug=staging, release=production) */
    val PRIVIME_CID: String = com.privimemobile.BuildConfig.PRIVIME_CID

    /** Unit conversion */
    const val GROTH_PER_BEAM = 100_000_000L

    /** Max chars per SBBS message (1024 byte limit with JSON overhead) */
    const val MAX_MSG_CHARS = 950

    /** Message refresh interval (ms) — fallback poll, ev_txs_changed is primary trigger */
    const val MSG_REFRESH_MS = 30_000L

    /** Conversation storage version — bump to force re-import on format change */
    const val CONV_VERSION = 2

    // === File sharing ===

    /** Max file size for upload (15 MB — limited by mobile memory for IPFS JSON encoding) */
    const val MAX_FILE_SIZE = 15 * 1024 * 1024

    /** Max inline file size — embed in SBBS message.
     *  BBS max body = 1MB (proto::Bbs::s_MaxMsgSize). After base64 (~33% overhead), ~750KB raw fits. */
    const val MAX_INLINE_SIZE = 750 * 1024

    /** Max voice message duration (seconds) — conservative limit to stay under MAX_INLINE_SIZE.
     *  32kbps Opus: ~42KB/sec after base64. 750KB / 42KB = ~17sec, but waveform + overhead needs margin.
     *  Conservative 2-minute limit ensures safe delivery via SBBS. */
    const val MAX_VOICE_DURATION_SEC = 120

    /** IPFS upload timeout (ms) */
    const val IPFS_ADD_TIMEOUT = 60_000

    /** IPFS download timeout (ms) — needs to be long for relay-based NAT traversal */
    const val IPFS_GET_TIMEOUT = 180_000

    /** Max filename display length */
    const val MAX_FILENAME_LEN = 60

    /** Auto-download images under this size (2 MB) */
    const val AUTO_DL_MAX_SIZE = 2 * 1024 * 1024

    /** Allowed MIME types for file sharing */
    val ALLOWED_MIME_TYPES = listOf(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "video/mp4", "video/webm", "video/3gpp",
        "audio/mpeg", "audio/ogg", "audio/wav",
        "application/pdf", "text/plain",
    )

    // === Image compression before upload ===

    /** Max dimension for image compression (px) */
    const val IMAGE_MAX_DIM = 1200

    /** Image compression quality (0-100) — RN uses 0.82 = 82% */
    const val IMAGE_QUALITY = 82

    /** Min file size to trigger compression (100 KB) */
    const val COMPRESS_MIN_SIZE = 100 * 1024

    /** DApp Store contract CID */
    const val DAPP_STORE_CID = "e2d24b686e8d31a0fe97eade9cd23281e7059b74b5757bdb96c820ef9e2af41c"
}
