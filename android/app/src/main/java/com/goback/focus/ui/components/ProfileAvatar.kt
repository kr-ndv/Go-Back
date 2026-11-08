package com.goback.focus.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

@Composable
fun ProfileAvatar(
    name: String,
    imageUri: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Profile photo"
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            val value = imageUri?.trim().orEmpty()
            if (value.isBlank()) return@withContext null
            runCatching {
                val uri = Uri.parse(value)
                val openStream: () -> InputStream? = {
                    if (uri.scheme == "file") uri.path?.let { File(it).inputStream() }
                    else context.contentResolver.openInputStream(uri)
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                openStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (name.isNotBlank()) {
            Text(name.trim().take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Filled.Person, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
