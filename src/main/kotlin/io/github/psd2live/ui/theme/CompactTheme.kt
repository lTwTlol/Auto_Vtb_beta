package io.github.psd2live.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Immutable
data class ToolColors(
	val windowBackground: Color = Color(0xFF12121A),
	val panelBackground: Color = Color(0xFF1B1B26),
	val panelElevated: Color = Color(0xFF232333),
	val inputBackground: Color = Color(0xFF12121A),
	val controlBackground: Color = Color(0xFF262636),
	val controlHover: Color = Color(0xFF2E2E42),
	val controlActive: Color = Color(0xFF36364E),
	val border: Color = Color(0xFF2A2A3A),
	val borderHover: Color = Color(0xFF4A4A66),
	val divider: Color = Color(0xFF23232F),
	val accent: Color = Color(0xFF7C5CFF),
	val accentHover: Color = Color(0xFF8F72FF),
	val accentText: Color = Color(0xFFFFFFFF),
	val selection: Color = Color(0xFF3A2E66),
	val selectionText: Color = Color(0xFFC4B5FD),
	val textPrimary: Color = Color(0xFFEDEDF2),
	val textMuted: Color = Color(0xFF9A96A8),
	val textDisabled: Color = Color(0xFF5A5766),
	val success: Color = Color(0xFF22D3EE),
	val warning: Color = Color(0xFFFBBF24),
	val error: Color = Color(0xFFF87171),
	val checkerLight: Color = Color(0xFF24242F),
	val checkerDark: Color = Color(0xFF1A1A24),
)

@Immutable
data class ToolTypography(
	val title: TextStyle = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.SemiBold,
		fontSize = 13.5.sp,
		color = Color(0xFFEDEDF2),
	),
	val header: TextStyle = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Medium,
		fontSize = 12.5.sp,
		color = Color(0xFFEDEDF2),
	),
	val body: TextStyle = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Normal,
		fontSize = 12.5.sp,
		color = Color(0xFFEDEDF2),
	),
	val caption: TextStyle = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Normal,
		fontSize = 11.5.sp,
		color = Color(0xFF9A96A8),
	),
	val mono: TextStyle = TextStyle(
		fontFamily = FontFamily.Monospace,
		fontWeight = FontWeight.Normal,
		fontSize = 11.5.sp,
		color = Color(0xFFEDEDF2),
	),
	val monoSmall: TextStyle = TextStyle(
		fontFamily = FontFamily.Monospace,
		fontWeight = FontWeight.Normal,
		fontSize = 10.5.sp,
		color = Color(0xFF9A96A8),
	),
)

val LocalToolColors = staticCompositionLocalOf { ToolColors() }
val LocalToolTypography = staticCompositionLocalOf { ToolTypography() }

@Composable
fun CompactToolTheme(
	colors: ToolColors = ToolColors(),
	typography: ToolTypography = ToolTypography(),
	uiScale: Float = 1.0f,
	fontScale: Float = 1.0f,
	content: @Composable () -> Unit,
) {
	val currentDensity = LocalDensity.current
	val effectiveDensity = remember(currentDensity, uiScale, fontScale) {
		Density(
			density = currentDensity.density * uiScale,
			fontScale = currentDensity.fontScale * fontScale,
		)
	}
	CompositionLocalProvider(
		LocalDensity provides effectiveDensity,
		LocalToolColors provides colors,
		LocalToolTypography provides typography,
		content = content,
	)
}


