package com.rork.ananego.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.rork.ananego.R
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Rounded elevated surface used for every content block in the app. */
@Composable
fun JungleCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, JungleOutline.copy(alpha = 0.6f)),
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = shape,
        border = border,
        content = content
    )
}

/** Official Añane Go logo badge (golden mototaxi + car over jungle leaves). */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = 38.dp) {
    Image(
        painter = painterResource(id = R.drawable.ananego_logo),
        contentDescription = "Logo de Añane Go",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
    )
}

/** App word-mark with tagline, used in top bars. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, showTagline: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Añane",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Go",
                    style = MaterialTheme.typography.titleLarge,
                    color = GoldAccent
                )
            }
            if (showTagline) {
                Text(
                    text = "Tu viaje, nuestra selva",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/** Circular avatar with initials, since profile photos are user-supplied. */
@Composable
fun InitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    accent: Color = GoldAccent,
    verified: Boolean = false
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.85f), JungleSurfaceHigh)
                    )
                )
                .border(1.dp, JungleOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp
            )
        }
        if (verified) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier
                    .size(size * 0.34f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

/** Small pill used for vehicle type, status and other metadata. */
@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SuccessGreen,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/** Star + rating + trip count row. */
@Composable
fun RatingRow(
    rating: Double,
    tripCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = GoldAccent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = if (tripCount != null) {
                "${"%.1f".format(rating)} ($tripCount viajes)"
            } else {
                "%.1f".format(rating)
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/** Section header with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        trailing?.invoke()
    }
}

/** Circular progress ring used by the subscription card. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringColor: Color = GoldAccent,
    trackColor: Color = JungleOutline,
    strokeWidth: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "ring"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(128.dp)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Simple animated bar chart used in driver stats. */
@Composable
fun MiniBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = SuccessGreen
) {
    Row(
        modifier = modifier.height(28.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        values.forEach { value ->
            val animated by animateFloatAsState(
                targetValue = value.coerceIn(0.15f, 1f),
                animationSpec = tween(700),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height((28 * animated).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** Shared no-content state. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

/** Clickable modifier for card surfaces, keeping ripple feedback. */
fun Modifier.clickableCard(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
