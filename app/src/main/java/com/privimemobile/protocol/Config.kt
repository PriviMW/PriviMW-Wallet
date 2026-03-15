package com.privimemobile.protocol

/**
 * Protocol configuration constants.
 * Ports config.ts from the RN prototype.
 */
object Config {
    /** Default Beam mainnet node (load-balanced) */
    const val DEFAULT_NODE = "eu-nodes.mainnet.beam.mw:8100"
    const val US_NODE = "us-nodes.mainnet.beam.mw:8100"

    /** PriviMe mainnet production contract CID */
    const val PRIVIME_CID = "32c6e5836eb5d2d428acce7ca4e262c8bf9f615c142811f7cf4ee4717f8747a9"

    /** Message refresh interval (ms) */
    const val MSG_REFRESH_MS = 30_000L

    /** IPFS download timeout (ms) */
    const val IPFS_GET_TIMEOUT = 30_000

    /** DApp Store contract CID */
    const val DAPP_STORE_CID = "e2d24b686e8d31a0fe97eade9cd23281e7059b74b5757bdb96c820ef9e2af41c"

    /** Max inline message size before using IPFS (bytes) */
    const val MAX_INLINE_SIZE = 1024

    /** Image compression max dimension (px) */
    const val IMAGE_MAX_DIM = 1200

    /** Image compression quality (0-100) */
    const val IMAGE_QUALITY = 75
}
