package com.privimemobile.ui.chat.chrome

import android.util.Log
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatEmojiStickerState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.chat.PendingFile
import com.privimemobile.ui.chat.StickerMeta
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatEmojiStickerPanel(
    emoji: ChatEmojiStickerState,
    input: ChatInputState,
    scope: CoroutineScope,
    messages: List<ChatMessage>,
    isGroupMode: Boolean,
    groupId: String?,
    handle: String,
    convId: Long,
    convKey: String,
    resolvedSbbsAddress: String?,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    ChatViewStickerPackDialog(emoji = emoji, messages = messages)

    if (!emoji.showEmojiPicker) return

    // Track recent emojis in SharedPreferences
    val recentPrefs = context.getSharedPreferences("emoji_recent", Context.MODE_PRIVATE)
    val recentEmojis = remember {
        mutableStateListOf<String>().apply {
            addAll(recentPrefs.getString("recent", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList())
        }
    }
    fun addRecent(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 32) recentEmojis.removeRange(32, recentEmojis.size)
        recentPrefs.edit().putString("recent", recentEmojis.joinToString(",")).apply()
    }
    fun insertEmoji(emoji: String) {
        val current = input.inputText.text
        val sel = input.inputText.selection.start
        input.setInputText(current.substring(0, sel) + emoji + current.substring(sel))
        addRecent(emoji)
    }

    // All emoji categories
    val allCategories = listOf(
        stringResource(R.string.chat_emoji_cat_people) to listOf(
            "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06", "\uD83D\uDE05", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE0A",
            "\uD83D\uDE07", "\uD83D\uDE42", "\uD83D\uDE43", "\uD83D\uDE09", "\uD83D\uDE0C", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE18", "\uD83D\uDE17",
            "\uD83D\uDE19", "\uD83D\uDE1A", "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83E\uDD2A", "\uD83D\uDE1D", "\uD83E\uDD11", "\uD83E\uDD17",
            "\uD83E\uDD2D", "\uD83E\uDD2B", "\uD83E\uDD14", "\uD83E\uDD10", "\uD83E\uDD28", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE36", "\uD83D\uDE0F",
            "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE2C", "\uD83E\uDD25", "\uD83D\uDE0E", "\uD83E\uDD13", "\uD83E\uDD78", "\uD83E\uDD21", "\uD83D\uDE34",
            "\uD83D\uDE2A", "\uD83D\uDE31", "\uD83D\uDE28", "\uD83D\uDE30", "\uD83D\uDE25", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE24", "\uD83D\uDE21",
            "\uD83D\uDE20", "\uD83E\uDD2F", "\uD83D\uDE33", "\uD83E\uDD75", "\uD83E\uDD76", "\uD83D\uDE31", "\uD83D\uDE28", "\uD83E\uDD2E", "\uD83E\uDD27",
            "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD15", "\uD83D\uDE35", "\uD83E\uDD74", "\uD83E\uDD22", "\uD83D\uDC7F", "\uD83D\uDC79", "\uD83D\uDC7A",
            "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7D", "\uD83E\uDD16", "\uD83D\uDCA9", "\uD83D\uDE3A", "\uD83D\uDE38", "\uD83D\uDE39", "\uD83D\uDE3B",
        ),
        stringResource(R.string.chat_emoji_cat_gestures) to listOf(
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\u270A", "\uD83D\uDC4A", "\uD83E\uDD1B", "\uD83E\uDD1C", "\uD83D\uDC4F", "\uD83D\uDE4C", "\uD83D\uDC50",
            "\uD83E\uDD32", "\uD83E\uDD1D", "\uD83D\uDE4F", "\u270D\uFE0F", "\uD83D\uDC85", "\uD83E\uDD33", "\uD83D\uDCAA", "\uD83D\uDC4B", "\uD83E\uDD1A",
            "\u270B", "\uD83D\uDC4C", "\uD83E\uDD0F", "\u270C\uFE0F", "\uD83E\uDD1E", "\uD83E\uDD1F", "\uD83E\uDD18", "\uD83E\uDD19", "\uD83D\uDC46",
            "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49", "\uD83D\uDD95", "\uD83D\uDC4B", "\uD83E\uDEF6", "\uD83E\uDEF1", "\uD83E\uDEF2", "\uD83E\uDEF3",
        ),
        stringResource(R.string.chat_emoji_cat_hearts) to listOf(
            "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83D\uDC94",
            "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96", "\uD83D\uDC98", "\uD83D\uDC9D", "\u2B50", "\uD83C\uDF1F",
            "\uD83D\uDCAB", "\u26A1", "\uD83D\uDD25", "\uD83D\uDCA5", "\uD83C\uDF89", "\uD83C\uDF8A", "\uD83C\uDFC6", "\uD83E\uDD47", "\uD83E\uDD48",
            "\uD83E\uDD49", "\uD83D\uDCAF", "\uD83D\uDC8B", "\uD83D\uDCA4", "\uD83D\uDCA8", "\uD83C\uDF08", "\u2600\uFE0F", "\uD83C\uDF19", "\u2744\uFE0F",
        ),
        stringResource(R.string.chat_emoji_cat_animals) to listOf(
            "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30", "\uD83E\uDD8A", "\uD83D\uDC3B", "\uD83D\uDC28",
            "\uD83D\uDC2F", "\uD83E\uDD81", "\uD83D\uDC2E", "\uD83D\uDC37", "\uD83D\uDC38", "\uD83D\uDC35", "\uD83D\uDE48", "\uD83D\uDE49",
            "\uD83D\uDE4A", "\uD83D\uDC27", "\uD83D\uDC26", "\uD83E\uDD85", "\uD83E\uDD86", "\uD83E\uDD89", "\uD83D\uDC3C", "\uD83D\uDC22",
            "\uD83D\uDC0D", "\uD83E\uDD96", "\uD83D\uDC33", "\uD83D\uDC2C", "\uD83E\uDD8B", "\uD83C\uDF3A", "\uD83C\uDF39", "\uD83C\uDF3B",
            "\uD83C\uDF3C", "\uD83C\uDF37", "\uD83C\uDF34", "\uD83C\uDF35", "\uD83C\uDF32", "\uD83C\uDF33", "\uD83C\uDF40", "\uD83C\uDF3F",
        ),
        stringResource(R.string.chat_emoji_cat_food) to listOf(
            "\uD83C\uDF4E", "\uD83C\uDF4A", "\uD83C\uDF4B", "\uD83C\uDF4C", "\uD83C\uDF49", "\uD83C\uDF47", "\uD83C\uDF53", "\uD83C\uDF48",
            "\uD83C\uDF55", "\uD83C\uDF54", "\uD83C\uDF5F", "\uD83C\uDF2D", "\uD83C\uDF2E", "\uD83C\uDF2F", "\uD83C\uDF73", "\uD83C\uDF5E",
            "\u2615", "\uD83C\uDF75", "\uD83C\uDF7A", "\uD83C\uDF77", "\uD83E\uDD42", "\uD83C\uDF78", "\uD83E\uDD64", "\uD83C\uDF70",
            "\uD83C\uDF82", "\uD83C\uDF6B", "\uD83C\uDF6C", "\uD83C\uDF6D", "\uD83C\uDF6E", "\uD83C\uDF6F", "\uD83C\uDF7E", "\uD83E\uDD43",
        ),
        stringResource(R.string.chat_emoji_cat_objects) to listOf(
            "\uD83D\uDCF1", "\uD83D\uDCBB", "\uD83D\uDCF7", "\uD83C\uDFB5", "\uD83C\uDFB6", "\uD83C\uDFA4", "\uD83C\uDFAC", "\uD83D\uDCDA",
            "\uD83D\uDD13", "\uD83D\uDD12", "\uD83D\uDD11", "\uD83D\uDCA1", "\uD83D\uDD0B", "\uD83D\uDCE7", "\uD83D\uDCE6", "\uD83D\uDCB0",
            "\uD83D\uDCB3", "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE8C", "\u2708\uFE0F", "\uD83D\uDE80", "\uD83C\uDFE0", "\uD83C\uDFEB",
            "\uD83C\uDFE5", "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFBE", "\uD83C\uDFAF", "\uD83C\uDFF3\uFE0F", "\uD83C\uDFF4", "\uD83C\uDDE6\uD83C\uDDFA",
        ),
    )

    // Category tab icons (matching Telegram)
    val categoryIcons = listOf("\uD83D\uDD53", "\uD83D\uDE00", "\uD83D\uDC4B", "\u2764\uFE0F", "\uD83D\uDC3B", "\uD83C\uDF54", "\uD83D\uDCF1", "\uD83C\uDFAD")
    // emoji.emojiMainTab declared at screen level for IME detection access
    val emojiGridState = rememberLazyGridState()

    // Pre-compute grid indices for each category header (for tab scrolling)
    val categoryGridIndices = remember(recentEmojis.size, allCategories) {
        val indices = mutableListOf<Int>()
        var idx = 0
        // Recent
        idx++ // "Recent" header
        idx += recentEmojis.size
        // Each category
        allCategories.forEachIndexed { catIdx, (_, emojis) ->
            indices.add(idx) // category header position
            idx++ // header
            idx += emojis.size
        }
        indices
    }

    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels
    val panelHeight = (screenHeight * 0.40f / LocalContext.current.resources.displayMetrics.density).dp

    Surface(color = C.card) {
        Column(modifier = Modifier.height(panelHeight)) {
            // Main tabs: Emoji | Stickers
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                // Category icon tabs (Telegram-style)
                var activeTabIdx by remember { mutableStateOf(0) }
                categoryIcons.forEachIndexed { idx, icon ->
                    val isActive = if (idx == categoryIcons.size - 1) emoji.emojiMainTab == 1 else emoji.emojiMainTab == 0 && activeTabIdx == idx
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isActive) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                if (idx == categoryIcons.size - 1) {
                                    emoji.emojiMainTab = 1 // Stickers tab
                                } else {
                                    emoji.emojiMainTab = 0
                                    activeTabIdx = idx
                                    if (idx == 0) {
                                        // Recent — scroll to top
                                        scope.launch { emojiGridState.animateScrollToItem(0) }
                                    } else if (idx - 1 < categoryGridIndices.size) {
                                        // Scroll to category header
                                        scope.launch { emojiGridState.animateScrollToItem(categoryGridIndices[idx - 1]) }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, fontSize = 20.sp)
                    }
                }
            }

            HorizontalDivider(color = C.border.copy(alpha = 0.3f))

            if (emoji.emojiMainTab == 0) {
                // Emoji tab — single scrollable list with recent + all categories
                LazyVerticalGrid(
                    columns = GridCells.Fixed(9),
                    state = emojiGridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    // Recent emojis
                    if (recentEmojis.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                            Text(stringResource(R.string.chat_recent), color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                        }
                        items(recentEmojis.size) { idx ->
                            Text(
                                recentEmojis[idx], fontSize = 28.sp,
                                modifier = Modifier.clickable { insertEmoji(recentEmojis[idx]) }.padding(4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    // All categories with headers
                    allCategories.forEach { (categoryName, emojis) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                            Text(categoryName, color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
                        }
                        items(emojis.size) { idx ->
                            Text(
                                emojis[idx], fontSize = 28.sp,
                                modifier = Modifier.clickable { insertEmoji(emojis[idx]) }.padding(4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                // Stickers tab — pack-based system
                val stickersRoot = remember { java.io.File(context.filesDir, "stickers").also { it.mkdirs() } }
                fun loadPacks(): List<Pair<String, List<java.io.File>>> {
                    val dirs = stickersRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
                    return dirs.map { dir ->
                        dir.name to (dir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList())
                    }
                }
                var packs by remember { mutableStateOf(loadPacks()) }
                var activePackIdx by remember { mutableStateOf(0) }
                var newPackName by remember { mutableStateOf("") }

                // Helper to save a bitmap as sticker WebP
                fun saveStickerBitmap(bmp: android.graphics.Bitmap, packDir: java.io.File, suffix: String = "") {
                    val maxSz = 512
                    val scale = minOf(maxSz.toFloat() / bmp.width, maxSz.toFloat() / bmp.height, 1f)
                    val w = (bmp.width * scale).toInt()
                    val h = (bmp.height * scale).toInt()
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    val file = java.io.File(packDir, "sticker_${System.currentTimeMillis()}$suffix.webp")
                    file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, it) }
                }

                // Multi-image picker for adding stickers to a pack
                val addToPackLauncher = rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    if (uris.isNotEmpty() && packs.isNotEmpty() && activePackIdx < packs.size) {
                        val packDir = java.io.File(stickersRoot, packs[activePackIdx].first)
                        for ((i, uri) in uris.withIndex()) {
                            try {
                                val input = context.contentResolver.openInputStream(uri)
                                val bmp = android.graphics.BitmapFactory.decodeStream(input)
                                input?.close()
                                if (bmp != null) saveStickerBitmap(bmp, packDir, "_$i")
                            } catch (_: Exception) {}
                        }
                        packs = loadPacks()
                        Toast.makeText(context, context.getString(R.string.toast_stickers_added, uris.size), Toast.LENGTH_SHORT).show()
                    }
                }

                // ZIP file picker for importing sticker packs
                val zipImportLauncher = rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null && packs.isNotEmpty() && activePackIdx < packs.size) {
                        val packDir = java.io.File(stickersRoot, packs[activePackIdx].first)
                        var count = 0
                        try {
                            val input = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                            val zip = java.util.zip.ZipInputStream(input)
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name.lowercase()
                                if (!entry.isDirectory && (name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tgs") || name.endsWith(".json"))) {
                                    val bytes = zip.readBytes()
                                    if (name.endsWith(".tgs")) {
                                        // TGS animated sticker — save directly
                                        val dest = java.io.File(packDir, "sticker_${System.currentTimeMillis()}_z$count.tgs")
                                        dest.writeBytes(bytes)
                                        count++
                                    } else if (name.endsWith(".json")) {
                                        // Lottie JSON — compress to TGS (gzip)
                                        val dest = java.io.File(packDir, "sticker_${System.currentTimeMillis()}_z$count.tgs")
                                        java.util.zip.GZIPOutputStream(dest.outputStream()).use { it.write(bytes) }
                                        count++
                                    } else {
                                        // Static image — decode and save as WebP
                                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) {
                                            saveStickerBitmap(bmp, packDir, "_z$count")
                                            count++
                                        }
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                            zip.close()
                            input.close()
                        } catch (_: Exception) {}
                        packs = loadPacks()
                        if (count > 0) {
                            Toast.makeText(context, context.getString(R.string.toast_stickers_imported, count), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.toast_no_images_in_zip, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Pack tabs row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // + button to create new pack
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(C.accent.copy(alpha = 0.15f))
                                .clickable { emoji.showCreateStickerPack = true },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(4.dp))

                        // Pack tabs (scrollable) — only render when packs exist
                        // Pack tabs as simple scrollable Row (avoids ScrollableTabRow index crash)
                        Row(
                            modifier = Modifier.weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            packs.forEachIndexed { idx, (name, _) ->
                                val selected = idx == activePackIdx.coerceIn(0, maxOf(packs.size - 1, 0))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { activePackIdx = idx }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        name, fontSize = 12.sp, maxLines = 1,
                                        color = if (selected) C.accent else C.textSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }

                    if (packs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("\uD83C\uDFAD", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.chat_no_sticker_packs), color = C.textSecondary, fontSize = 14.sp)
                                Text(stringResource(R.string.chat_tap_create_pack), color = C.textMuted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        val currentPack = packs.getOrNull(activePackIdx)
                        if (currentPack != null) {
                            // Pack header with add + delete buttons
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(currentPack.first, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(" (${currentPack.second.size})", color = C.textMuted, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { zipImportLauncher.launch("application/zip") }) {
                                    Text(stringResource(R.string.chat_zip_label), color = C.accent, fontSize = 12.sp)
                                }
                                TextButton(onClick = { addToPackLauncher.launch("image/*") }) {
                                    Text(stringResource(R.string.chat_add_sticker), color = C.accent, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    // Share entire pack as ZIP
                                    val packFiles = currentPack?.second ?: emptyList()
                                    if (packFiles.isEmpty()) {
                                        Toast.makeText(context, R.string.toast_pack_empty, Toast.LENGTH_SHORT).show()
                                    } else if (!isGroupMode && resolvedSbbsAddress.isNullOrEmpty()) {
                                        Toast.makeText(context, R.string.toast_resolving_address, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val pName = currentPack!!.first
                                        val pId = pName.hashCode().toString(16)
                                        val pTotal = packFiles.size
                                        emoji.showEmojiPicker = false
                                        Toast.makeText(context, context.getString(R.string.toast_packaging_stickers, pTotal), Toast.LENGTH_SHORT).show()
                                        com.privimemobile.chat.ChatService.scope.launch {
                                            try {
                                                // Build ZIP of all stickers (try 512px first, then 256px if too large)
                                                fun buildZip(maxPx: Int, quality: Int): java.io.File {
                                                    val zipFile = java.io.File(context.cacheDir, "pack_${pId}_${System.currentTimeMillis()}.zip")
                                                    java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
                                                        packFiles.forEachIndexed { idx, file ->
                                                            if (file.name.endsWith(".tgs", ignoreCase = true)) {
                                                                // TGS animated sticker — include as-is
                                                                val entry = java.util.zip.ZipEntry("sticker_${idx}.tgs")
                                                                zos.putNextEntry(entry)
                                                                file.inputStream().use { it.copyTo(zos) }
                                                                zos.closeEntry()
                                                            } else {
                                                                // Static sticker — resize and compress
                                                                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                                                                val scale = minOf(maxPx.toFloat() / bmp.width, maxPx.toFloat() / bmp.height, 1f)
                                                                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                                                                val entry = java.util.zip.ZipEntry("sticker_${idx}.webp")
                                                                zos.putNextEntry(entry)
                                                                scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, quality, zos)
                                                                zos.closeEntry()
                                                            }
                                                        }
                                                    }
                                                    return zipFile
                                                }

                                                var zipFile = buildZip(512, 80)
                                                if (zipFile.length() > 500_000) {
                                                    zipFile.delete()
                                                    zipFile = buildZip(256, 50)  // More aggressive compression
                                                }

                                                if (zipFile.length() > 700_000) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, context.getString(R.string.toast_pack_too_large, zipFile.length() / 1024), Toast.LENGTH_LONG).show()
                                                    }
                                                    zipFile.delete()
                                                    return@launch
                                                }

                                                // Send ZIP as sticker_pack message
                                                val uri = android.net.Uri.fromFile(zipFile)
                                                val fileMeta = com.privimemobile.chat.transport.IpfsTransport.prepareFile(
                                                    context, uri, "${pName}.zip", zipFile.length(), "application/zip"
                                                )
                                                if (fileMeta != null) {
                                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get() ?: return@launch
                                                    val myHandle = state.myHandle ?: return@launch
                                                    val ts = System.currentTimeMillis() / 1000
                                                    val payload = mutableMapOf<String, Any?>(
                                                        "v" to 1, "t" to "sticker_pack", "ts" to ts,
                                                        "from" to myHandle, "to" to (if (isGroupMode) groupId!! else handle),
                                                        "dn" to (state.myDisplayName ?: ""),
                                                        "file" to fileMeta,
                                                        "pack_name" to pName,
                                                        "pack_id" to pId,
                                                        "pack_total" to pTotal,
                                                    )
                                                    val cid = fileMeta["cid"] as? String ?: ""
                                                    val stkConvId = if (isGroupMode) convId else {
                                                        val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                                                        if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                                                        conv.id
                                                    }
                                                    val dedupKey = "$ts:sticker_pack:$cid:true"
                                                    val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                        conversationId = stkConvId, text = "\uD83D\uDCE6 Sticker pack: $pName ($pTotal stickers)",
                                                        timestamp = ts, sent = true, type = "sticker_pack",
                                                        senderHandle = myHandle, sbbsDedupKey = dedupKey,
                                                        stickerPackName = pName, stickerPackId = pId, stickerPackTotal = pTotal,
                                                    )
                                                    val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                    if (msgId > 0 && cid.isNotEmpty()) {
                                                        com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                            com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                messageId = msgId, conversationId = stkConvId,
                                                                ipfsCid = cid, encryptionKey = fileMeta["key"] as? String ?: "",
                                                                encryptionIv = fileMeta["iv"] as? String ?: "",
                                                                fileName = "${pName}.zip", fileSize = zipFile.length(),
                                                                mimeType = "application/zip", inlineData = fileMeta["data"] as? String,
                                                                downloadStatus = "done",
                                                            )
                                                        )
                                                    }
                                                    if (isGroupMode && groupId != null) {
                                                        // Update preview BEFORE network send \u2014 survives early navigation
                                                        val youLabel = context.getString(R.string.chat_sender_you)
                                                        com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: \uD83D\uDCE6 Sticker pack: $pName")
                                                        com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                                    } else {
                                                        com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(stkConvId, ts, "\uD83D\uDCE6 Sticker pack: $pName")
                                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedSbbsAddress!!, payload)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, context.getString(R.string.toast_pack_shared, pTotal, zipFile.length() / 1024), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                zipFile.delete()
                                            } catch (e: Exception) {
                                                Log.e("ChatScreen", "Share pack error: ${e.message}")
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, context.getString(R.string.toast_share_failed, e.message), Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.chat_label_share), color = C.accent, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    val dir = java.io.File(stickersRoot, currentPack.first)
                                    dir.deleteRecursively()
                                    packs = loadPacks()
                                    activePackIdx = 0
                                    Toast.makeText(context, R.string.toast_pack_deleted, Toast.LENGTH_SHORT).show()
                                }) {
                                    Text(stringResource(R.string.chat_label_delete), color = C.error, fontSize = 12.sp)
                                }
                            }

                            // Sticker grid
                            if (currentPack.second.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.chat_empty_pack_hint), color = C.textMuted, fontSize = 13.sp)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    contentPadding = PaddingValues(4.dp),
                                ) {
                                    items(currentPack.second.size) { idx ->
                                        val file = currentPack.second[idx]
                                        val isTgs = file.name.endsWith(".tgs", ignoreCase = true)
                                        val stickerMod = Modifier
                                            .aspectRatio(1f)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .pointerInput(file.absolutePath) {
                                                detectTapGestures(
                                                    onTap = {
                                                        val mime = if (isTgs) "application/x-tgsticker" else "image/webp"
                                                        val ext = if (isTgs) "tgs" else "webp"
                                                        val cached = java.io.File(context.cacheDir, "sticker_send_${System.currentTimeMillis()}.$ext")
                                                        file.copyTo(cached, overwrite = true)
                                                        input.pendingFile = PendingFile(uri = android.net.Uri.fromFile(cached), name = file.name, size = cached.length(), mimeType = mime)
                                                        val pName = currentPack?.first ?: context.getString(R.string.chat_sticker_pack_label)
                                                        input.pendingStickerMeta = StickerMeta(pName, pName.hashCode().toString(16), currentPack?.second?.size ?: 0)
                                                        emoji.showEmojiPicker = false
                                                        onSend()
                                                    },
                                                    onLongPress = {
                                                        file.delete()
                                                        packs = loadPacks()
                                                        Toast.makeText(context, R.string.toast_sticker_removed, Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            }

                                        if (isTgs) {
                                            // Animated TGS sticker — decompress and render with Lottie
                                            val lottieJson = remember(file.absolutePath, file.lastModified()) {
                                                try {
                                                    java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().readText()
                                                } catch (_: Exception) { null }
                                            }
                                            if (lottieJson != null) {
                                                val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                                    com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                                                )
                                                com.airbnb.lottie.compose.LottieAnimation(
                                                    composition = composition,
                                                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                                    modifier = stickerMod,
                                                )
                                            }
                                        } else {
                                            // Static sticker (WebP/PNG)
                                            val bmp = remember(file.absolutePath, file.lastModified()) {
                                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                            }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = stringResource(R.string.chat_sticker_pack_label),
                                                    modifier = stickerMod,
                                                    contentScale = ContentScale.Fit,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Create pack dialog
                if (emoji.showCreateStickerPack) {
                    AlertDialog(
                        onDismissRequest = { emoji.showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() },
                        containerColor = C.card,
                        title = { Text(stringResource(R.string.chat_new_sticker_pack), color = C.text) },
                        text = {
                            OutlinedTextField(
                                value = newPackName,
                                onValueChange = { newPackName = it.take(20) },
                                placeholder = { Text(stringResource(R.string.chat_pack_name), color = C.textMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = C.text, unfocusedTextColor = C.text,
                                    focusedBorderColor = C.accent, cursorColor = C.accent,
                                ),
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val name = newPackName.trim()
                                if (name.isNotEmpty()) {
                                    java.io.File(stickersRoot, name).mkdirs()
                                    packs = loadPacks()
                                    activePackIdx = packs.indexOfFirst { it.first == name }.coerceAtLeast(0)
                                    emoji.showCreateStickerPack = false; newPackName = ""
                                    focusManager.clearFocus(); keyboardController?.hide()
                                }
                            }) { Text(stringResource(R.string.chat_create), color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { emoji.showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() }) {
                                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatViewStickerPackDialog(
    emoji: ChatEmojiStickerState,
    messages: List<ChatMessage>,
) {
    val context = LocalContext.current
    if (emoji.viewPackId != null) {
    // Get pack name from the sticker message that triggered this
    val packName = messages.firstOrNull { it.stickerPackId == emoji.viewPackId }?.stickerPackName ?: stringResource(R.string.chat_sticker_pack_label)
    val stickersRoot = java.io.File(context.filesDir, "stickers")
    val localPackDir = java.io.File(stickersRoot, packName)
    val localFiles = remember(emoji.viewPackId) {
        if (localPackDir.exists()) localPackDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        else emptyList()
    }

    AlertDialog(
        onDismissRequest = { emoji.viewPackId = null },
        containerColor = C.card,
        title = {
            Column {
                Text(packName, color = C.text, fontWeight = FontWeight.SemiBold)
                if (localFiles.isNotEmpty()) {
                    Text(stringResource(R.string.chat_stickers_count, localFiles.size), color = C.accent, fontSize = 12.sp)
                } else {
                    Text(stringResource(R.string.chat_pack_not_saved), color = C.textSecondary, fontSize = 12.sp)
                }
            }
        },
        text = {
            if (localFiles.isEmpty()) {
                Text(stringResource(R.string.chat_dont_have_pack), color = C.textMuted, fontSize = 13.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 300.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    items(localFiles.size) { idx ->
                        val file = localFiles[idx]
                        val isTgs = file.name.endsWith(".tgs", ignoreCase = true)
                        if (isTgs) {
                            val lottieJson = remember(file.absolutePath) {
                                try { java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().readText() }
                                catch (_: Exception) { null }
                            }
                            if (lottieJson != null) {
                                val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                    com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                                )
                                com.airbnb.lottie.compose.LottieAnimation(
                                    composition = composition,
                                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                    modifier = Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                        } else {
                            val bmp = remember(file.absolutePath, file.lastModified()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.chat_sticker_pack_label),
                                    modifier = Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { emoji.viewPackId = null }) { Text(stringResource(R.string.general_ok), color = C.accent) }
        },
        dismissButton = {},
    )
    }
}
