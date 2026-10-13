/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.theme.MaterialComponentsViewInflater
import com.owncloud.android.R

/**
 * The view inflater of Imagina's app themes (`viewInflaterClass` in `themes_imagina.xml` and `styles_imagina.xml`,
 * IMAGINA.md, «Botón flotante»). It is Material's, which every Nextcloud theme already uses, and it hands out an
 * [ImaginaFloatingActionButton] wherever a layout declares a Material floating action button. It changes no Nextcloud
 * file and takes effect only in the "imagina" flavor, whose themes name it.
 */
class ImaginaViewInflater : MaterialComponentsViewInflater() {

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? =
        if (name == FLOATING_ACTION_BUTTON) {
            ImaginaFloatingActionButton(context, attrs)
        } else {
            super.createView(context, name, attrs)
        }

    private companion object {
        const val FLOATING_ACTION_BUTTON = "com.google.android.material.floatingactionbutton.FloatingActionButton"
    }
}

/**
 * A floating action button that is always painted with the accent and its on-accent colour (#0F766E with white in the
 * light theme, #2DD4BF with #0B2B28 in the dark one: the design system's «bg-accent text-on-accent»).
 *
 * Nextcloud paints these buttons at run time with `viewThemeUtils.material.themeFAB(...)`, which uses Material's
 * `primaryContainer` and `onPrimaryContainer` (a pale mint with a dark icon), from nine call sites. Instead of editing
 * them, this button answers every tint it is given (that call's, or a layout's) with the accent. The tints keep
 * Nextcloud's disabled state: a grey button with a white icon, which is what `setFabEnabled(false)` relies on.
 *
 * The accent is `R.color.primary` and its text `R.color.primary_button_text_color`: the flavor points both at the
 * design system tokens (`colors_imagina.xml`), in both themes. With any other version (`imagina_auth_host` empty) the
 * button is Material's and takes the tints it is given.
 *
 * Layouts get it from [ImaginaViewInflater]; a button made in code needs [create].
 */
class ImaginaFloatingActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.floatingActionButtonStyle
) : FloatingActionButton(context, attrs, defStyleAttr) {

    init {
        applyAccent()
    }

    override fun setBackgroundTintList(tint: ColorStateList?) {
        super.setBackgroundTintList(if (isImagina()) enabledOrGrey(accent(), Color.GRAY) else tint)
    }

    override fun setImageTintList(tint: ColorStateList?) {
        super.setImageTintList(if (isImagina()) enabledOrGrey(onAccent(), Color.WHITE) else tint)
    }

    private fun applyAccent() {
        if (!isImagina()) {
            return
        }

        setBackgroundTintList(null)
        setImageTintList(null)
        setRippleColor(ColorUtils.setAlphaComponent(onAccent(), RIPPLE_ALPHA))
    }

    private fun isImagina(): Boolean = ImaginaSignIn.isEnabled(context)

    private fun accent(): Int = ContextCompat.getColor(context, R.color.primary)

    private fun onAccent(): Int = ContextCompat.getColor(context, R.color.primary_button_text_color)

    private fun enabledOrGrey(enabled: Int, disabled: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
        intArrayOf(enabled, disabled)
    )

    companion object {
        /** 12 %, the pressed state layer of Material 3. */
        private const val RIPPLE_ALPHA = 0x1F

        /** For a floating button made in code (the layouts get theirs from [ImaginaViewInflater]). */
        @JvmStatic
        fun create(context: Context): FloatingActionButton = ImaginaFloatingActionButton(context)
    }
}
