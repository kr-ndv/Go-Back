package com.goback.focus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val M3ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp) // M3 Expressive Card standard
)

val CardShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(percent = 50)
val ModalShape = RoundedCornerShape(32.dp)
val ChipShape = RoundedCornerShape(16.dp)
