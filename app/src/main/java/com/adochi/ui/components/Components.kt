package com.adochi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, border),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Rounded square with a tinted background, used to lead rows and cards. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            IconBadge(icon, accent, size = 34.dp)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

enum class SocialSite(val label: String, val url: String, val colors: List<Color>, val icon: ImageVector?, val glyph: String?) {
    FACEBOOK("Facebook", "https://m.facebook.com/", listOf(Color(0xFF1877F2), Color(0xFF0A5BD8)), null, "f"),
    YOUTUBE("YouTube", "https://m.youtube.com/", listOf(Color(0xFFFF2D2D), Color(0xFFCC0000)), Icons.Rounded.PlayArrow, null),
    INSTAGRAM(
        "Instagram", "https://www.instagram.com/",
        listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF)), Icons.Rounded.PhotoCamera, null,
    ),
    TIKTOK("TikTok", "https://www.tiktok.com/", listOf(Color(0xFF25F4EE), Color(0xFF111111), Color(0xFFFE2C55)), Icons.Rounded.MusicNote, null),
    X("X", "https://x.com/", listOf(Color(0xFF2A2A2A), Color(0xFF000000)), null, "X"),
}

@Composable
fun SiteBadge(site: SocialSite, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(site.colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (site.icon != null) {
            Icon(site.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        } else {
            Text(site.glyph.orEmpty(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.45f).sp)
        }
    }
}

@Composable
fun SiteButton(site: SocialSite, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Five of these share one row, so they shrink to fit narrow phones instead of spilling over.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SiteBadge(site, size = minOf(maxWidth - 8.dp, 52.dp))
        }
        Text(
            site.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}
