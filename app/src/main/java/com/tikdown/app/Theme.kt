package com.tikdown.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF5B3BF0)
val BrandPink = Color(0xFFFE2C55)
val BrandCyan = Color(0xFF25F4EE)
val BrandBrush = Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B2CF5)))

@Composable
fun TikTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9D8CFF),
            background = Color(0xFF12101F),
            surface = Color(0xFF1C1930),
            surfaceVariant = Color(0xFF2A2547),
            onSurfaceVariant = Color(0xFFC9C3E6)
        )
    } else {
        lightColorScheme(
            primary = Brand,
            background = Color(0xFFF7F5FF),
            surface = Color.White,
            surfaceVariant = Color(0xFFEDE9FE),
            onSurfaceVariant = Color(0xFF5B5675)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppLogo(size: Dp) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Color.Black)
            .border(size * 0.04f, Brush.linearGradient(listOf(BrandCyan, BrandPink)), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) BrandBrush else Brush.horizontalGradient(listOf(Color(0xFFB9B4D6), Color(0xFFB9B4D6))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) BrandBrush
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
