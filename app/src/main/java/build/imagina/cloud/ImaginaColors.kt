/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.content.Context
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import dynamiccolor.DynamicScheme
import hct.Hct
import palettes.TonalPalette
import scheme.SchemeTonalSpot
import java.util.Optional

/**
 * The colour schemes Nextcloud paints its views with (`ViewThemeUtils`: the search bar, the bottom bar, the floating
 * button, sheets, the selected drawer item), with Imagina's neutrals. Nextcloud derives every tone of the scheme from
 * the accent (the server's colour, Imagina's teal), so its surfaces come out mint; here the accent keeps giving the
 * primary, secondary and tertiary tones, as in Nextcloud, but the surfaces, the text and the outlines take the hue and
 * chroma of Imagina's canvas and ink (`colors_imagina.xml`): an almost grey cream in the light theme and the cool dark
 * of Imagina's dark theme. Off unless the "imagina" flavor is on (`ImaginaSignIn.isEnabled`).
 */
object ImaginaColors {
    /** canvas #F8F7F4: the light theme's surfaces. */
    private val LIGHT_NEUTRAL = Hct.fromInt(0xFFF8F7F4.toInt())

    /** canvas #111219: the dark theme's surfaces. */
    private val DARK_NEUTRAL = Hct.fromInt(0xFF111219.toInt())

    /** ink-secondary #474B57: the secondary text and the outlines, in both themes. */
    private val NEUTRAL_VARIANT = Hct.fromInt(0xFF474B57.toInt())

    @JvmStatic
    fun schemes(context: Context, accent: Int): MaterialSchemes? {
        if (!ImaginaSignIn.isEnabled(context)) {
            return null
        }

        return object : MaterialSchemes {
            override val lightScheme: DynamicScheme = scheme(accent, isDark = false)
            override val darkScheme: DynamicScheme = scheme(accent, isDark = true)
        }
    }

    /** Nextcloud's scheme (tonal spot from the accent) with Imagina's neutral palettes. */
    private fun scheme(accent: Int, isDark: Boolean): DynamicScheme {
        val base = SchemeTonalSpot(Hct.fromInt(accent), isDark, 0.0)
        val neutral = if (isDark) DARK_NEUTRAL else LIGHT_NEUTRAL

        return DynamicScheme(
            base.sourceColorHct,
            base.variant,
            base.isDark,
            base.contrastLevel,
            base.platform,
            base.specVersion,
            base.primaryPalette,
            base.secondaryPalette,
            base.tertiaryPalette,
            TonalPalette.fromHueAndChroma(neutral.hue, neutral.chroma),
            TonalPalette.fromHueAndChroma(NEUTRAL_VARIANT.hue, NEUTRAL_VARIANT.chroma),
            Optional.of(base.errorPalette)
        )
    }
}
