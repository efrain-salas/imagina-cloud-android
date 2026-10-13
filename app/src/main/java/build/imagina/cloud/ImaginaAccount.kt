/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.view.View
import android.widget.TextView
import com.nextcloud.client.account.User
import com.nextcloud.client.account.UserAccountManager
import com.owncloud.android.MainApp

/**
 * How an account shows to the person. Nextcloud names accounts «user@host» and shows that name in many places;
 * Imagina's Nextcloud users are numbered (Imagina's user id), so «1@cloud.imagina.build» means nothing to a person.
 * Where the Imagina version has these hooks it shows the person's name (the one Nextcloud got when the account was
 * created) or nothing. The account name itself does not change: Nextcloud uses it as the key of everything the account
 * has.
 *
 * With any other version (imagina_auth_host empty) Nextcloud's own text is shown.
 */
object ImaginaAccount {

    @JvmStatic
    fun isEnabled(): Boolean = ImaginaSignIn.isEnabled(MainApp.getAppContext())

    /**
     * What names the account where there is no name next to it (a notification, the root of the Files app of Android):
     * the person's name, or null when there is none.
     */
    @JvmStatic
    fun label(user: User): String? = if (isEnabled()) {
        UserAccountManager.getDisplayName(user)?.takeIf { it.isNotBlank() }
    } else {
        user.accountName
    }

    /**
     * The line under the person's name in the account screens (the list of accounts, the chooser, the profile, the
     * dialog to remove the account): Nextcloud's «user@host». The name above already says who it is, so Imagina's
     * version shows nothing there.
     */
    @JvmStatic
    fun setAccountLine(view: TextView, accountName: CharSequence?) {
        if (isEnabled()) {
            view.text = ""
            view.visibility = View.GONE
        } else {
            view.text = accountName
        }
    }
}
