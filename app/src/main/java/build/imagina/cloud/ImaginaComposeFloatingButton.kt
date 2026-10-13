/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.owncloud.android.R
import androidx.compose.material3.FloatingActionButton as MaterialFloatingActionButton

/**
 * The floating action button of the screens Nextcloud draws with Jetpack Compose (the assistant: new translation,
 * translate, new conversation), in the accent with its on-accent colour: #0F766E with white in the light theme, #2DD4BF
 * with #0B2B28 in the dark one (IMAGINA.md, «Botón flotante»). Material's own button takes the scheme's
 * `primaryContainer`, a pale mint with a dark icon.
 *
 * Those three screens import this function in place of `androidx.compose.material3.FloatingActionButton` (one import
 * each, and their calls stay as they are), which is the whole hook: the scheme is not touched, because
 * `SimpleAlertDialog` writes its text in `onPrimaryContainer` over the surface and would turn white on white.
 *
 * The colours are `R.color.primary` and `R.color.primary_button_text_color`, which the flavor points at the design
 * system tokens in both themes (`colors_imagina.xml`), the same ones [ImaginaFloatingActionButton] uses for the Views'
 * buttons. With any other version (`imagina_auth_host` empty) this is Material's button, untouched.
 */
@Composable
fun FloatingActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val imagina = ImaginaSignIn.isEnabled(LocalContext.current)
    val container: Color =
        if (imagina) colorResource(R.color.primary) else FloatingActionButtonDefaults.containerColor
    val onContainer: Color =
        if (imagina) colorResource(R.color.primary_button_text_color) else contentColorFor(container)

    MaterialFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = container,
        contentColor = onContainer,
        content = content
    )
}
