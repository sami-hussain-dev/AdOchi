package com.adochi.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adochi.AdOchiApp
import com.adochi.BuildConfig
import com.adochi.Credits
import com.adochi.R
import com.adochi.core.AppNames
import com.adochi.core.FilterEngine
import com.adochi.core.MessageGuard
import com.adochi.core.PrivateDns
import com.adochi.core.Stats
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.components.SiteButton
import com.adochi.ui.components.SocialSite
import com.adochi.ui.components.StatTile
import com.adochi.ui.formatCount
import com.adochi.ui.theme.warning
import com.adochi.vpn.QuickTile
import com.adochi.vpn.ShieldState
import com.adochi.vpn.VpnController

@Composable
fun HomeScreen(
    onToggleShield: () -> Unit,
    onOpenSite: (String) -> Unit,
    onCheckLink: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGuard: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val shield by VpnController.state.collectAsStateWithLifecycle()
    val stats by Stats.state.collectAsStateWithLifecycle()
    val totals by FilterEngine.totals.collectAsStateWithLifecycle()
    val guardTick by AdOchiApp.instance.settings.changes.collectAsStateWithLifecycle()
    val guardOn = remember(guardTick, shield) {
        BuildConfig.MESSAGE_GUARD && MessageGuard.hasAccess(context) && AdOchiApp.instance.settings.messageGuard
    }
    val topApp = stats.appsToday.firstOrNull { it.blocked > 0 && AppNames.isPackage(it.app) }
    val privateDns = remember(shield) { PrivateDns.strictHost(context) }
    val colors = MaterialTheme.colorScheme

    // The first time the shield comes on, offer the Quick Settings tile (one tap, Android 13+).
    LaunchedEffect(shield) {
        val settings = AdOchiApp.instance.settings
        if (shield == ShieldState.RUNNING && !settings.tilePrompted && QuickTile.canRequest) {
            settings.tilePrompted = true
            QuickTile.request(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text("AdOchi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Ad blocker & scam shield", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            ShieldButton(shield, onToggleShield)
        }

        val (title, subtitle) = when (shield) {
            ShieldState.RUNNING -> "You're protected" to "Ads, trackers and scam sites are blocked in every app and browser."
            ShieldState.STARTING -> "Starting shield…" to "Getting everything ready."
            ShieldState.STOPPED -> "Protection is off" to "Tap the button to block ads and keep your personal and bank details safe."
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        if (privateDns != null) {
            val light = PrivateDns.isAdBlocking(privateDns)
            val tint = if (light) colors.primary else colors.warning
            Spacer(Modifier.height(16.dp))
            AppCard(border = tint.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (light) Icons.Rounded.Shield else Icons.Rounded.Warning, tint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (light) "Light mode is on" else "Private DNS is bypassing AdOchi",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (light) {
                                "Ads are blocked by $privateDns without a VPN. For scam alerts and AdOchi's own lists, " +
                                    "set Private DNS to \"Automatic\" and turn the shield on."
                            } else {
                                "Set Private DNS to \"Off\" or \"Automatic\" so AdOchi can filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { openWirelessSettings(context) }) { Text("Open settings") }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Blocked today", formatCount(stats.blockedToday), Icons.Rounded.Block, colors.primary, Modifier.weight(1f))
            StatTile("Scams stopped", formatCount(stats.threatsToday), Icons.Rounded.GppBad, colors.error, Modifier.weight(1f))
            StatTile("All time", formatCount(stats.blockedTotal), Icons.Rounded.Shield, colors.secondary, Modifier.weight(1f))
        }

        SectionTitle("Ad-free social media")
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SocialSite.entries.forEach { site ->
                    SiteButton(site, onClick = { onOpenSite(site.url) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Opens in AdOchi's Clean Browser: no sponsored posts, no video ads, no trackers.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        SectionTitle("Stay safe")
        ActionCard(
            icon = Icons.Rounded.Radar,
            tint = colors.error,
            title = "Scan phone for spy & scam apps",
            subtitle = "Finds apps that can read your OTPs, control your screen or steal bank logins.",
            onClick = onOpenScanner,
        )
        Spacer(Modifier.height(10.dp))
        if (BuildConfig.MESSAGE_GUARD) {
            ActionCard(
                icon = Icons.Rounded.MarkChatRead,
                tint = if (guardOn) colors.primary else colors.warning,
                title = if (guardOn) "Message Guard is on" else "Turn on Message Guard",
                subtitle = if (stats.scamMessages > 0) {
                    "${formatCount(stats.scamMessages)} scam links caught in your chats and SMS."
                } else {
                    "Get warned the moment a scam link arrives on WhatsApp or SMS."
                },
                onClick = onOpenGuard,
            )
        } else {
            ActionCard(
                icon = Icons.Rounded.MarkChatRead,
                tint = colors.primary,
                title = "Check a WhatsApp or SMS message",
                subtitle = "Share any message to AdOchi to find out if its link is a scam.",
                onClick = onOpenGuard,
            )
        }
        Spacer(Modifier.height(10.dp))
        ActionCard(
            icon = Icons.Rounded.Insights,
            tint = colors.secondary,
            title = "Privacy report",
            subtitle = if (topApp != null) {
                "${AppNames.label(context, topApp.app)} tried to track you ${formatCount(topApp.blocked)} times today."
            } else {
                "See which apps try to track you the most."
            },
            onClick = onOpenReport,
        )
        Spacer(Modifier.height(10.dp))
        ActionCard(
            icon = Icons.Rounded.Link,
            tint = colors.secondary,
            title = "Check a suspicious link",
            subtitle = "Paste any link before you open it.",
            onClick = onCheckLink,
        )
        Spacer(Modifier.height(10.dp))
        ActionCard(
            icon = Icons.Rounded.Shield,
            tint = colors.primary,
            title = "Protection lists",
            subtitle = if (totals.loaded) {
                "${formatCount(totals.ads)} ad & tracker domains · ${formatCount(totals.threats)} scam & malware domains"
            } else {
                "Loading filters…"
            },
            onClick = onOpenFilters,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Runs 100% on your phone. No account, no servers, no data collected.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 24.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenAbout)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.x8code_logo),
                contentDescription = null,
                modifier = Modifier.size(18.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Made by ${Credits.COMPANY} · ${Credits.WEBSITE_LABEL}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShieldButton(state: ShieldState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = state == ShieldState.RUNNING
    val accent by animateColorAsState(
        targetValue = when (state) {
            ShieldState.RUNNING -> colors.primary
            ShieldState.STARTING -> colors.warning
            ShieldState.STOPPED -> colors.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(500),
        label = "accent",
    )
    val transition = rememberInfiniteTransition(label = "shield")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (active) 6000 else 1500, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = if (active) 0.32f else 0.10f), Color.Transparent),
                    center = center,
                    radius = radius * if (active) pulse else 0.9f,
                ),
                radius = radius,
            )
            drawCircle(accent.copy(alpha = 0.18f), radius = radius * 0.78f, style = Stroke(1.5.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.10f), radius = radius * 0.92f, style = Stroke(1.dp.toPx()))
        }
        if (state != ShieldState.STOPPED) {
            Canvas(Modifier.size(214.dp).rotate(rotation)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, accent, Color.Transparent), center = Offset(size.width / 2, size.height / 2)),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(2.dp, accent.copy(alpha = 0.8f)),
            shadowElevation = 16.dp,
            modifier = Modifier.size(160.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Toggle protection", tint = accent, modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    when (state) {
                        ShieldState.RUNNING -> "ON"
                        ShieldState.STARTING -> "…"
                        ShieldState.STOPPED -> "OFF"
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

private fun openWirelessSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
