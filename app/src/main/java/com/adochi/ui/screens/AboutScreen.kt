package com.adochi.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adochi.BuildConfig
import com.adochi.Credits
import com.adochi.R
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.components.SettingRow

/** The x8code gradient (blue to purple to pink), taken from its logo. */
private val X8_COLORS = listOf(Color(0xFF3B8BFF), Color(0xFF8B5CF6), Color(0xFFEC4899))

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.x8code_logo),
                contentDescription = "x8code logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(2.dp, Brush.linearGradient(X8_COLORS), CircleShape),
            )
            Spacer(Modifier.height(14.dp))
            Text("AdOchi by ${Credits.COMPANY}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} · Free forever",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "\"${Credits.TAGLINE}\"",
                style = MaterialTheme.typography.bodyMedium.copy(brush = Brush.linearGradient(X8_COLORS)),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }

        SectionTitle("Made by")
        AppCard(onClick = { onOpenUrl(Credits.ABOUT_URL) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.x8code_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(Credits.FOUNDER, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Credits.FOUNDER_ROLE}, ${Credits.COMPANY}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${Credits.COMPANY} is a software house that builds dependable web systems, ERPs and apps. " +
                    "AdOchi is its free gift to everyone: keep ads, trackers and scammers away from your personal and bank details.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(Credits.LOCATION, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }

        SectionTitle("Get in touch")
        AppCard {
            val chevron: @Composable () -> Unit = {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            SettingRow(Icons.Rounded.Language, "Website", Credits.WEBSITE_LABEL, tint = X8_COLORS[0], onClick = { onOpenUrl(Credits.WEBSITE) }, trailing = chevron)
            SettingRow(Icons.Rounded.Person, "About the founder", "${Credits.WEBSITE_LABEL}/about", tint = X8_COLORS[1], onClick = { onOpenUrl(Credits.ABOUT_URL) }, trailing = chevron)
            SettingRow(Icons.Rounded.Email, "Email", Credits.EMAIL, tint = X8_COLORS[2], onClick = { sendEmail(context) }, trailing = chevron)
            SettingRow(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", "Message ${Credits.COMPANY}", tint = colors.primary, onClick = { openExternal(context, Credits.WHATSAPP) }, trailing = chevron)
            SettingRow(Icons.Rounded.Code, "GitHub", Credits.GITHUB.removePrefix("https://"), tint = colors.secondary, onClick = { onOpenUrl(Credits.GITHUB) }, trailing = chevron)
        }

        SectionTitle("Thanks")
        AppCard {
            Text(
                "Blocklists by HaGeZi, OISD, AdGuard, StevenBlack, Phishing Army and malware-filter. " +
                    "No account, no servers, no ads, no data collection. Everything runs on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Text(
            "© ${Credits.COMPANY} · ${Credits.WEBSITE_LABEL}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
    }
}

private fun sendEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Credits.EMAIL}"))
        .putExtra(Intent.EXTRA_SUBJECT, "AdOchi ${BuildConfig.VERSION_NAME}")
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No email app.
    }
}

/** WhatsApp links go to the WhatsApp app, not the Clean Browser. */
private fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing can open it.
    }
}
