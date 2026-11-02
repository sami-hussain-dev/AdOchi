package com.adochi.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.adochi.core.AppNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(150)

private fun loadIcon(context: Context, key: String): ImageBitmap? {
    iconCache.get(key)?.let { return it }
    if (!AppNames.isPackage(key)) return null
    return try {
        context.packageManager.getApplicationIcon(key).toBitmap(96, 96).asImageBitmap().also { iconCache.put(key, it) }
    } catch (e: Exception) {
        null
    }
}

/** Launcher icon of an app key from [AppNames]; a generic Android badge for system services. */
@Composable
fun AppIcon(key: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon by produceState(iconCache.get(key), key) {
        value = withContext(Dispatchers.IO) { loadIcon(context, key) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        IconBadge(Icons.Rounded.Android, MaterialTheme.colorScheme.onSurfaceVariant, size = size)
    }
}
