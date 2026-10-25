package org.pcbu.libreclient.ui.appearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.BgCard
import org.pcbu.libreclient.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged

// Ported verbatim from Donova's ui/WheelPicker.kt — snapping LazyRow, BoxWithConstraints
// centering so the first/last item can still reach the center slot, haptic tick on every
// centered-item change. Used here for the "Effet verre" blur slider (see ConfigDialog.kt) since
// a plain Slider's drag consistently lost the gesture to Material3's AlertDialog scroll
// container on-device.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalWheelPicker(
    value: Int,
    values: List<Int>,
    displayStr: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BgCard,
) {
    val itemWidth = 64.dp
    val currentIdx = values.indexOf(value).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIdx)
    val snapBehavior = rememberSnapFlingBehavior(listState)

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val centeredIdx by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf currentIdx
            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - vpCenter) }
                ?.index ?: currentIdx
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { centeredIdx }
            .distinctUntilChanged()
            .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (wasScrolling && !scrolling) {
                    val newVal = values.getOrNull(centeredIdx) ?: currentValue
                    if (newVal != currentValue) {
                        currentOnValueChange(newVal)
                        currentOnRelease()
                    }
                }
                wasScrolling = scrolling
            }
    }

    LaunchedEffect(value) {
        val idx = values.indexOf(value)
        if (idx >= 0 && !listState.isScrollInProgress) {
            listState.scrollToItem(idx)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPadding = (maxWidth - itemWidth) / 2

        LazyRow(
            state          = listState,
            flingBehavior  = snapBehavior,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier       = Modifier.fillMaxWidth(),
        ) {
            items(values.size) { idx ->
                val isSelected = idx == centeredIdx

                val textColor by animateColorAsState(
                    targetValue   = if (isSelected) Accent else TextSecondary.copy(alpha = 0.35f),
                    animationSpec = tween(80),
                    label         = "wheelColor",
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .width(itemWidth)
                        .height(58.dp),
                ) {
                    Text(
                        text       = displayStr(values[idx]),
                        color      = textColor,
                        fontSize   = if (isSelected) 22.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(width = itemWidth, height = 46.dp)
                .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
        )

    }
}
