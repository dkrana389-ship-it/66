package com.deepinnovations.call_history

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PopupOverlay {
    private var rootView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    // Removes the popup immediately.
    fun dismiss(ctx: Context) {
        stopTicker()
        val v = rootView ?: return
        rootView = null
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    // Slides the card down, then removes the popup.
    private fun dismissAnimated(ctx: Context, root: View, card: View) {
        stopTicker()
        if (rootView !== root) return
        card.animate()
            .translationY(card.height.toFloat() + 300f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (rootView === root) dismiss(ctx)
            }
            .start()
    }

    private fun fmt(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
    }

    // Light red colour spreads from the centre like a drop.
    private fun reveal(tint: View) {
        tint.visibility = View.VISIBLE
        try {
            val cx = tint.width / 2
            val cy = tint.height / 2
            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            val anim = ViewAnimationUtils.createCircularReveal(tint, cx, cy, 0f, radius)
            anim.duration = 700
            anim.start()
        } catch (_: Exception) {
            tint.visibility = View.VISIBLE
        }
    }

    // TEST ADS: pehle true rakho aur ad aaye to check karo. Phir false karke naya build banao (real ads ke liye).
    private const val TEST_ADS = true
    private const val START_APP_ID = "210504322"

    private var currentNative: StartAppNativeAd? = null
    private var sdkReady = false
    private var sdkStarting = false
    private val pending = mutableListOf<() -> Unit>()

    // SDK ready hone ke baad hi banner banta hai (Start.io ka official tareeka).
    private fun whenAdsReady(app: Context, action: () -> Unit) {
        if (sdkReady) {
            action()
            return
        }
        pending.add(action)
        if (sdkStarting) return
        sdkStarting = true
        try {
            if (TEST_ADS) StartAppSDK.setTestAdsEnabled(true)
            StartAppSDK.initParams(app, START_APP_ID)
                .setReturnAdsEnabled(false)
                .setCallback {
                    handler.post {
                        sdkReady = true
                        val list = pending.toList()
                        pending.clear()
                        for (r in list) {
                            try {
                                r()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
                .init()
        } catch (_: Throwable) {
            sdkStarting = false
        }
    }

    @Suppress("DEPRECATION")
    fun show(
        ctx: Context,
        name: String?,
        number: String?,
        type: String,
        durSec: Long,
        today: CallStore.Today
    ) {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        dismiss(app)

        val dp = app.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun tv(t: String, sp: Float, bold: Boolean, color: String): TextView {
            val x = TextView(app)
            x.text = t
            x.textSize = sp
            x.setTextColor(Color.parseColor(color))
            if (bold) x.setTypeface(x.typeface, Typeface.BOLD)
            return x
        }

        // Saved contact -> show name. Not saved -> show number.
        val nameText = name.orEmpty().trim()
        val numberText = number.orEmpty().trim()
        val title = when {
            nameText.isNotEmpty() -> nameText
            numberText.isNotEmpty() -> numberText
            else -> "Unknown number"
        }
        val subtitle = if (nameText.isNotEmpty() && numberText.isNotEmpty()) numberText else ""
        val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

        // ---- Card: white background, light-red layer on top of it, content on top of both
        val card = FrameLayout(app)
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = px(20).toFloat()
        card.background = bg
        card.clipToOutline = true
        card.elevation = px(8).toFloat()

        val tint = View(app)
        tint.setBackgroundColor(Color.parseColor("#FFCDD2"))
        tint.visibility = View.INVISIBLE
        // Height 0 on purpose: a plain View with MATCH_PARENT would stretch the card to full screen.
        // Its height is set to the content height right after layout (see listener below).
        card.addView(
            tint,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        )

        val content = LinearLayout(app)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(px(18), px(16), px(18), px(16))
        content.alpha = 0f
        card.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        content.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = bottom - top
            val lp = tint.layoutParams
            if (h > 0 && lp.height != h) {
                lp.height = h
                tint.layoutParams = lp
            }
        }

        val header = LinearLayout(app)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val headTv = tv("CALL ENDED", 12f, true, "#7A5560")
        header.addView(headTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val cross = tv("\u2715", 20f, true, "#44546A")
        cross.gravity = Gravity.CENTER
        cross.setPadding(px(12), px(4), px(4), px(4))
        cross.setOnClickListener { dismiss(app) }
        header.addView(cross, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(header)
        content.addView(tv(title, 24f, true, "#15233D"))
        if (subtitle.isNotEmpty()) content.addView(tv(subtitle, 14f, false, "#44546A"))
        content.addView(tv("$type  •  ${fmt(durSec)}  •  $date", 14f, false, "#44546A"))

        // Today's report
        val row = LinearLayout(app)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, px(14), 0, px(6))
        fun stat(value: String, label: String) {
            val col = LinearLayout(app)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.addView(tv(value, 20f, true, "#15233D"))
            col.addView(tv(label, 12f, false, "#5B6B82"))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        stat("${today.count}", "Calls today")
        stat("${today.missed}", "Missed")
        stat(fmt(today.totalSec), "Total talk")
        content.addView(row)

        val gap = View(app)
        content.addView(gap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(10)))

        // Native ad (Start.io): ad ka data (icon, title, text) hum apne views mein dikhate hain,
        // isliye overlay window mein bhi sahi dikhta hai (banner ka WebView yahan blank rehta tha).
        val adBox = LinearLayout(app)
        adBox.orientation = LinearLayout.HORIZONTAL
        adBox.gravity = Gravity.CENTER_VERTICAL
        adBox.setPadding(px(8), px(6), px(8), px(6))
        val adBg = GradientDrawable()
        adBg.setColor(Color.parseColor("#99FFFFFF"))
        adBg.cornerRadius = px(10).toFloat()
        adBox.background = adBg
        adBox.visibility = View.GONE

        val adIcon = ImageView(app)
        adIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        adBox.addView(adIcon, LinearLayout.LayoutParams(px(44), px(44)))

        val adTextCol = LinearLayout(app)
        adTextCol.orientation = LinearLayout.VERTICAL
        val adTitle = tv("", 14f, true, "#15233D")
        adTitle.maxLines = 1
        adTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        val adDesc = tv("", 11f, false, "#44546A")
        adDesc.maxLines = 2
        adDesc.ellipsize = android.text.TextUtils.TruncateAt.END
        adTextCol.addView(adTitle)
        adTextCol.addView(adDesc)
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = px(10)
        colLp.marginEnd = px(8)
        adBox.addView(adTextCol, colLp)

        val adCta = tv("Open", 12f, true, "#FFFFFF")
        adCta.gravity = Gravity.CENTER
        adCta.setPadding(px(12), px(8), px(12), px(8))
        val ctaBg = GradientDrawable()
        ctaBg.setColor(Color.parseColor("#1565C0"))
        ctaBg.cornerRadius = px(8).toFloat()
        adCta.background = ctaBg
        adBox.addView(adCta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Status line (sirf jaanch ke liye)
        val status = tv("Ad status: SDK start ho raha hai...", 11f, false, "#B3261E")
        status.setPadding(0, px(4), 0, 0)
        fun setStatus(t: String) {
            handler.post { status.text = t }
        }

        whenAdsReady(app) {
            setStatus("Ad status: SDK ready, ad maang rahe hain...")
            try {
                val nativeAd = StartAppNativeAd(app)
                currentNative = nativeAd
                val prefs = NativeAdPreferences()
                    .setAdsNumber(1)
                    .setAutoBitmapDownload(true)
                nativeAd.loadAd(prefs, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad?) {
                        handler.post {
                            try {
                                val list = nativeAd.nativeAds
                                if (list == null || list.isEmpty()) {
                                    status.text = "Ad status: ad list khali"
                                } else {
                                    val d = list[0]
                                    adTitle.text = d.title ?: ""
                                    adDesc.text = d.description ?: ""
                                    val cta = d.callToAction
                                    if (!cta.isNullOrEmpty()) adCta.text = cta
                                    val bmp = d.imageBitmap
                                    if (bmp != null) adIcon.setImageBitmap(bmp) else adIcon.visibility = View.GONE
                                    adBox.visibility = View.VISIBLE
                                    d.registerViewForInteraction(adBox)
                                    status.text = "Ad status: ad dikh raha hai"
                                }
                            } catch (t: Throwable) {
                                status.text = "Ad status: show error - " + (t.message ?: t.javaClass.simpleName)
                            }
                        }
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        val msg = try {
                            ad?.errorMessage
                        } catch (_: Throwable) {
                            null
                        }
                        setStatus("Ad status: ad nahi mila " + (msg ?: ""))
                    }
                })
            } catch (t: Throwable) {
                setStatus("Ad status: error - " + (t.message ?: t.javaClass.simpleName))
            }
        }
        content.addView(adBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(status)

        val btns = LinearLayout(app)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.setPadding(0, px(12), 0, 0)
        val close = Button(app)
        close.text = "Close"
        close.setOnClickListener { dismiss(app) }
        val callBack = Button(app)
        callBack.text = "Call Back"
        callBack.setOnClickListener {
            dismiss(app)
            if (numberText.isNotEmpty()) {
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numberText)))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(i)
            }
        }
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.marginEnd = px(6)
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp2.marginStart = px(6)
        btns.addView(close, lp1)
        btns.addView(callBack, lp2)
        content.addView(btns)

        val root = FrameLayout(app)
        root.setPadding(px(12), px(12), px(12), px(24))
        root.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val windowType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        // Start below the screen, then slide up.
        card.translationY = px(700).toFloat()
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(root, params)
            rootView = root
        } catch (_: Exception) {
            return
        }

        card.animate()
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (rootView === root) {
                    // 1) white card is up -> 2) data fades in -> 3) red drop spreads
                    content.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .withEndAction {
                            if (rootView === root) {
                                reveal(tint)
                            }
                        }
                        .start()
                }
            }
            .start()
    }
}
