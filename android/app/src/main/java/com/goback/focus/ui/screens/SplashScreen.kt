package com.goback.focus.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goback.focus.R
import com.goback.focus.ui.components.M3SquigglyProgress
import kotlinx.coroutines.delay

private val SplashBackgroundColor = Color(0xFF081B14)
private val SplashAccentLime = Color(0xFFC7F485)
private val SplashTextWhite = Color(0xFFF2F9F4)

/**
 * Material 3 Expressive Splash Screen.
 * Background color strictly matches @color/ic_launcher_background (#081B14)
 * ensuring a 100% seamless blend with zero flash on app launch.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1200)
        onSplashFinished()
    }

    val transition = rememberInfiniteTransition(label = "splash_motion")
    val pulseScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "fade_in"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .alpha(contentAlpha)
        ) {
            // App Icon with breathing pulse
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SplashAccentLime.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Go Back Logo",
                    modifier = Modifier.size(92.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Go Back",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.2).sp,
                    color = SplashTextWhite
                )
                Text(
                    text = ".",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = SplashAccentLime
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tagline
            Text(
                text = "YOUR TIME. YOUR TERMS.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp,
                color = SplashAccentLime.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Squiggly Progress Indicator
            Box(modifier = Modifier.width(160.dp)) {
                M3SquigglyProgress(
                    indeterminate = true,
                    height = 14.dp,
                    strokeWidth = 3.5.dp,
                    amplitude = 3.5.dp,
                    color = SplashAccentLime,
                    trackColor = SplashAccentLime.copy(alpha = 0.25f)
                )
            }
        }
    }
}
