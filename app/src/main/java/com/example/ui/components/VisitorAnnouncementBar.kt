package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.GoldSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private const val MIN_VISITORS = 10_000
private const val MAX_VISITORS = 20_000
private const val MAX_CHANGE = 15

/**
 * Small live-looking announcement bar for the user application.
 * The displayed value is intentionally local/presentational: it remains between
 * 10,000 and 20,000 and each update changes it by no more than 15.
 */
@Composable
fun VisitorAnnouncementBar(modifier: Modifier = Modifier) {
    var visitorCount by rememberSaveable {
        mutableIntStateOf(Random.nextInt(MIN_VISITORS, MAX_VISITORS + 1))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(2_000L, 4_001L))
            val change = Random.nextInt(1, MAX_CHANGE + 1)
            val direction = if (Random.nextBoolean()) 1 else -1
            visitorCount = (visitorCount + direction * change)
                .coerceIn(MIN_VISITORS, MAX_VISITORS)
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "visitor_announcement_blink")
    val textAlpha by blinkTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visitor_announcement_alpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EmeraldDark,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لدينا الآن عدد زائر",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(textAlpha)
            )
            Text(
                text = " $visitorCount",
                color = GoldSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.alpha(textAlpha),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
