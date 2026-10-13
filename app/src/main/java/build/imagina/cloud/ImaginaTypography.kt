/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The typography of the screens Nextcloud draws with Jetpack Compose (the assistant, `ComposeActivity`): Instrument
 * Sans, the typeface of the rest of the app. Compose does not read the themes the Views use, so without it those
 * screens show the system font.
 *
 * The family is the one in `app/src/imagina/res/font/`, which only the "imagina" flavor has, so its files are looked up
 * by name: with any other version (no such font) it is Material's own typography. Text sizes, weights and spacing stay
 * Material's.
 */
object ImaginaTypography {

    @JvmStatic
    fun typography(context: Context): Typography {
        val material = Typography()
        val family = instrumentSans(context) ?: return material

        return material.copy(
            displayLarge = material.displayLarge.copy(fontFamily = family),
            displayMedium = material.displayMedium.copy(fontFamily = family),
            displaySmall = material.displaySmall.copy(fontFamily = family),
            headlineLarge = material.headlineLarge.copy(fontFamily = family),
            headlineMedium = material.headlineMedium.copy(fontFamily = family),
            headlineSmall = material.headlineSmall.copy(fontFamily = family),
            titleLarge = material.titleLarge.copy(fontFamily = family),
            titleMedium = material.titleMedium.copy(fontFamily = family),
            titleSmall = material.titleSmall.copy(fontFamily = family),
            bodyLarge = material.bodyLarge.copy(fontFamily = family),
            bodyMedium = material.bodyMedium.copy(fontFamily = family),
            bodySmall = material.bodySmall.copy(fontFamily = family),
            labelLarge = material.labelLarge.copy(fontFamily = family),
            labelMedium = material.labelMedium.copy(fontFamily = family),
            labelSmall = material.labelSmall.copy(fontFamily = family)
        )
    }

    @SuppressLint("DiscouragedApi")
    private fun instrumentSans(context: Context): FontFamily? {
        val weights = listOf(
            "instrument_sans_regular" to FontWeight.Normal,
            "instrument_sans_medium" to FontWeight.Medium,
            "instrument_sans_semibold" to FontWeight.SemiBold,
            "instrument_sans_bold" to FontWeight.Bold
        )
        val fonts = weights.mapNotNull { (name, weight) ->
            val id = context.resources.getIdentifier(name, "font", context.packageName)
            if (id == 0) null else Font(id, weight)
        }

        return if (fonts.size == weights.size) FontFamily(fonts) else null
    }
}
