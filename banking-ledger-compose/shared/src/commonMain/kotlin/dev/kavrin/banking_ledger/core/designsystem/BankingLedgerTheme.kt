package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bankingledger.shared.generated.resources.Res
import bankingledger.shared.generated.resources.poppins_bold
import bankingledger.shared.generated.resources.poppins_medium
import bankingledger.shared.generated.resources.poppins_regular
import bankingledger.shared.generated.resources.poppins_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class BankingColors(
    val backgroundApp: Color,
    val backgroundPanel: Color,
    val surfaceDefault: Color,
    val surfaceRaised: Color,
    val surfaceSelected: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryStrong: Color,
    val primaryContainer: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

@Immutable
data class BankingTypography(
    val mobileScreenTitle: TextStyle,
    val mobileSectionTitle: TextStyle,
    val desktopPageTitle: TextStyle,
    val metricNumber: TextStyle,
    val tableBody: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

val DarkBankingColors = BankingColors(
    backgroundApp = Color(0xFF050B0D),
    backgroundPanel = Color(0xFF081010),
    surfaceDefault = Color(0xFF101818),
    surfaceRaised = Color(0xFF101F1D),
    surfaceSelected = Color(0xFF0E3A2B),
    borderSubtle = Color(0xFF243235),
    borderStrong = Color(0xFF3A4A4D),
    textPrimary = Color(0xFFF4F7F5),
    textSecondary = Color(0xFFB5C0BE),
    textMuted = Color(0xFF7F8D8A),
    primary = Color(0xFF48C090),
    primaryStrong = Color(0xFF189860),
    primaryContainer = Color(0xFF103828),
    success = Color(0xFF48C090),
    warning = Color(0xFFF0B429),
    error = Color(0xFFFF6B63),
    info = Color(0xFF8AA4FF),
)

val DarkBankingTypography = BankingTypography(
    mobileScreenTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    mobileSectionTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    desktopPageTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    metricNumber = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 48.sp,
    ),
    tableBody = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    label = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

object BankingLedgerColors {
    val BackgroundApp = DarkBankingColors.backgroundApp
    val BackgroundPanel = DarkBankingColors.backgroundPanel
    val SurfaceDefault = DarkBankingColors.surfaceDefault
    val SurfaceRaised = DarkBankingColors.surfaceRaised
    val SurfaceSelected = DarkBankingColors.surfaceSelected
    val BorderSubtle = DarkBankingColors.borderSubtle
    val BorderStrong = DarkBankingColors.borderStrong
    val TextPrimary = DarkBankingColors.textPrimary
    val TextSecondary = DarkBankingColors.textSecondary
    val TextMuted = DarkBankingColors.textMuted
    val Primary = DarkBankingColors.primary
    val PrimaryStrong = DarkBankingColors.primaryStrong
    val PrimaryContainer = DarkBankingColors.primaryContainer
    val Success = DarkBankingColors.success
    val Warning = DarkBankingColors.warning
    val Error = DarkBankingColors.error
    val Info = DarkBankingColors.info
}

@Immutable
data class BankingLedgerStatusColors(
    val content: Color,
    val container: Color,
    val border: Color,
)

object BankingLedgerStatusDefaults {
    val Completed = BankingLedgerStatusColors(
        content = DarkBankingColors.success,
        container = Color(0xFF103828),
        border = DarkBankingColors.primaryStrong,
    )
    val Sent = Completed
    val Pending = BankingLedgerStatusColors(
        content = DarkBankingColors.warning,
        container = Color(0xFF33280A),
        border = Color(0xFFA97900),
    )
    val Mismatch = Pending
    val Failed = BankingLedgerStatusColors(
        content = DarkBankingColors.error,
        container = Color(0xFF321514),
        border = Color(0xFFA83A35),
    )
    val Error = Failed
    val Info = BankingLedgerStatusColors(
        content = DarkBankingColors.info,
        container = Color(0xFF111B3D),
        border = Color(0xFF405BB8),
    )
}

@Immutable
data class BankingLedgerSpacing(
    val grid: Dp = 4.dp,
    val mobileScreenX: Dp = 24.dp,
    val desktopContent: Dp = 24.dp,
    val componentSm: Dp = 12.dp,
    val componentMd: Dp = 16.dp,
    val componentLg: Dp = 24.dp,
    val mobileRowMinHeight: Dp = 64.dp,
    val desktopRowMinHeight: Dp = 48.dp,
)

val LocalBankingLedgerSpacing = staticCompositionLocalOf { BankingLedgerSpacing() }
val LocalBankingColors = staticCompositionLocalOf { DarkBankingColors }
val LocalBankingTypography = staticCompositionLocalOf { DarkBankingTypography }

val MaterialTheme.bankingColors: BankingColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBankingColors.current

val MaterialTheme.bankingTypography: BankingTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalBankingTypography.current

object BankingLedgerTheme {
    val spacing: BankingLedgerSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalBankingLedgerSpacing.current

    val colors: BankingColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBankingColors.current

    val typography: BankingTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBankingTypography.current
}

private fun bankingDarkColorScheme(colors: BankingColors): ColorScheme = darkColorScheme(
    primary = colors.primary,
    onPrimary = colors.backgroundApp,
    primaryContainer = colors.surfaceSelected,
    onPrimaryContainer = colors.textPrimary,
    secondary = colors.primaryStrong,
    onSecondary = colors.textPrimary,
    background = colors.backgroundApp,
    onBackground = colors.textPrimary,
    surface = colors.backgroundPanel,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceDefault,
    onSurfaceVariant = colors.textSecondary,
    outline = colors.borderStrong,
    outlineVariant = colors.borderSubtle,
    error = colors.error,
    onError = colors.backgroundApp,
    errorContainer = Color(0xFF321514),
    onErrorContainer = colors.error,
)

@Composable
private fun poppinsFontFamily(): FontFamily = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold),
)

private fun darkBankingTypography(fontFamily: FontFamily): BankingTypography = BankingTypography(
    mobileScreenTitle = DarkBankingTypography.mobileScreenTitle.copy(fontFamily = fontFamily),
    mobileSectionTitle = DarkBankingTypography.mobileSectionTitle.copy(fontFamily = fontFamily),
    desktopPageTitle = DarkBankingTypography.desktopPageTitle.copy(fontFamily = fontFamily),
    metricNumber = DarkBankingTypography.metricNumber.copy(fontFamily = fontFamily),
    tableBody = DarkBankingTypography.tableBody.copy(fontFamily = fontFamily),
    body = DarkBankingTypography.body.copy(fontFamily = fontFamily),
    label = DarkBankingTypography.label.copy(fontFamily = fontFamily),
)

private fun materialBankingTypography(fontFamily: FontFamily): Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val BankingLedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun BankingLedgerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val bankingColors = when {
        darkTheme -> DarkBankingColors
        else -> DarkBankingColors
    }
    val fontFamily = poppinsFontFamily()
    val bankingTypography = darkBankingTypography(fontFamily)

    CompositionLocalProvider(
        LocalBankingColors provides bankingColors,
        LocalBankingTypography provides bankingTypography,
    ) {
        MaterialTheme(
            colorScheme = bankingDarkColorScheme(bankingColors),
            typography = materialBankingTypography(fontFamily),
            shapes = BankingLedgerShapes,
            content = content,
        )
    }
}
