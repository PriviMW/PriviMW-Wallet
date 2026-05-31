package com.privimemobile.ui.chat.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Scroll-aware FABs for New Chat and Create Group. */
@Composable
internal fun ChatsFabs(
    chatListState: LazyListState,
    onNewChat: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fabVisible by remember { mutableStateOf(true) }
    val prevFirstVisible = remember { mutableIntStateOf(0) }
    val prevScrollOffset = remember { mutableIntStateOf(0) }
    LaunchedEffect(chatListState.firstVisibleItemIndex, chatListState.firstVisibleItemScrollOffset) {
        val currentFirst = chatListState.firstVisibleItemIndex
        val currentOffset = chatListState.firstVisibleItemScrollOffset
        if (currentFirst > prevFirstVisible.intValue || (currentFirst == prevFirstVisible.intValue && currentOffset > prevScrollOffset.intValue + 20)) {
            fabVisible = false // scrolling down
        } else if (currentFirst < prevFirstVisible.intValue || (currentFirst == prevFirstVisible.intValue && currentOffset < prevScrollOffset.intValue - 20)) {
            fabVisible = true // scrolling up
        }
        prevFirstVisible.intValue = currentFirst
        prevScrollOffset.intValue = currentOffset
    }
    val fabScale by animateFloatAsState(
        targetValue = if (fabVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "fabScale",
    )
    val fabAlpha by animateFloatAsState(
        targetValue = if (fabVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "fabAlpha",
    )
    Column(
        modifier = modifier.padding(16.dp)
            .graphicsLayer { scaleX = fabScale; scaleY = fabScale; alpha = fabAlpha },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Small FAB - Create Group
        SmallFloatingActionButton(
            onClick = onCreateGroup,
            containerColor = C.card,
            contentColor = C.accent,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.Group, contentDescription = stringResource(R.string.chats_create_group_desc), modifier = Modifier.size(20.dp))
        }
        // Main FAB - New Chat
        FloatingActionButton(
            onClick = onNewChat,
            containerColor = C.accent,
            contentColor = C.textDark,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.chats_fab_new_chat))
        }
    }
}