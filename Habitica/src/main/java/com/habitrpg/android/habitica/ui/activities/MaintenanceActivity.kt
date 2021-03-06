package com.habitrpg.android.habitica.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.api.MaintenanceApiService
import com.habitrpg.android.habitica.components.AppComponent
import com.habitrpg.android.habitica.data.ApiClient
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.ui.helpers.MarkdownParser
import com.habitrpg.android.habitica.ui.helpers.bindView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import net.pherth.android.emoji_library.EmojiTextView
import javax.inject.Inject

class MaintenanceActivity : BaseActivity() {

    @Inject
    lateinit var maintenanceService: MaintenanceApiService

    @Inject
    lateinit var apiClient: ApiClient

    internal val titleTextView: TextView by bindView(R.id.titleTextView)
    internal val imageView: SimpleDraweeView by bindView(R.id.imageView)
    internal val descriptionTextView: EmojiTextView by bindView(R.id.descriptionTextView)
    internal val playStoreButton: Button by bindView(R.id.playStoreButton)
    private var isDeprecationNotice: Boolean = false

    override fun getLayoutResId(): Int {
        return R.layout.activity_maintenance
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.extras

        this.titleTextView.text = data?.getString("title")

        @Suppress("DEPRECATION")
        imageView.setImageURI(Uri.parse(data.getString("imageUrl")))
        this.descriptionTextView.text = MarkdownParser.parseMarkdown(data.getString("description"))
        this.descriptionTextView.movementMethod = LinkMovementMethod.getInstance()

        isDeprecationNotice = data.getBoolean("deprecationNotice")
        if (isDeprecationNotice) {
            this.playStoreButton.visibility = View.VISIBLE
        } else {
            this.playStoreButton.visibility = View.GONE
        }

        playStoreButton.setOnClickListener { openInPlayStore() }
    }

    override fun injectActivity(component: AppComponent?) {
        component?.inject(this)
    }

    override fun onResume() {
        super.onResume()
        if (!isDeprecationNotice) {
            this.maintenanceService.maintenanceStatus
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(Consumer { maintenanceResponse ->
                        if (!maintenanceResponse.activeMaintenance) {
                            finish()
                        }
                    }, RxErrorHandler.handleEmptyError())
        }
    }

    private fun openInPlayStore() {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (anfe: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }

    }
}
