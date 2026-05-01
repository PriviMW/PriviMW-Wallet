package com.privimemobile.dapp

import android.app.Dialog
import com.privimemobile.R
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricPrompt as BiometricPromptApi
import android.os.CancellationSignal
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Android TX approval dialog — bypasses React Native bridge entirely.
 *
 * The RN JS thread gets starved for 15-20s when a DApp WebView runs heavy JavaScript
 * (e.g., AMM swap calculations). No RN bridge mechanism can deliver events during this
 * time. This native dialog shows instantly from the UI thread.
 */
object NativeTxApprovalDialog {
    private const val TAG = "NativeTxApproval"

    // Colors matching the app theme
    private const val BG_COLOR = 0xFF0A2540.toInt()       // card background
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()      // primary text
    private const val TEXT_SECONDARY = 0xFF8DA1AD.toInt()  // secondary text
    private const val ACCENT_COLOR = 0xFF00F6D2.toInt()    // teal accent
    private const val SEND_COLOR = 0xFFDA68F5.toInt()      // purple (outgoing)
    private const val RECEIVE_COLOR = 0xFF0BCCF7.toInt()   // cyan (incoming)
    private const val ERROR_COLOR = 0xFFFF625C.toInt()     // red
    private const val BTN_REJECT_BG = 0xFF1A3A5C.toInt()   // dark button
    private const val OVERLAY_COLOR = 0xB3000000.toInt()   // 70% black overlay

    // DAppActivity reference — set when DAppActivity is in the foreground
    @Volatile
    var dappActivity: android.app.Activity? = null

    fun show(request: String, infoJson: String, amountsJson: String, isSend: Boolean) {
        // Prefer DAppActivity (if running), otherwise fall back to main activity
        val activity = dappActivity ?: WalletManager.currentActivity
        if (activity == null) {
            Log.w(TAG, "No activity — rejecting TX for safety")
            reject(request)
            return
        }

        val info = try { JSONObject(infoJson) } catch (_: Exception) { JSONObject() }
        val amounts = try { JSONArray(amountsJson) } catch (_: Exception) { JSONArray() }

        val fee = info.optString("fee", "0")
        val comment = info.optString("comment", "")
        val isEnough = info.optBoolean("isEnough", true)

        val dp = { value: Int -> TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
        ).toInt() }

        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)

        // Root overlay
        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(OVERLAY_COLOR)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnClickListener {
                dialog.dismiss()
                reject(request)
            }
        }

        // Modal card
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(BG_COLOR)
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnClickListener { /* absorb clicks */ }
        }
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        overlay.addView(card, cardParams)

        // Title
        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.tx_request_title)
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        // App name (from DApp WebView)
        val activeWebView = DAppResponseRouter.getActiveAppName()
        if (activeWebView != null) {
            card.addView(TextView(activity).apply {
                text = activeWebView
                setTextColor(ACCENT_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(16))
            })
        }

        // Scrollable details
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(details)
        card.addView(scroll)

        // Amounts
        val spending = mutableListOf<Pair<String, String>>()
        val receiving = mutableListOf<Pair<String, String>>()
        for (i in 0 until amounts.length()) {
            val entry = amounts.optJSONObject(i) ?: continue
            val amount = formatAmount(entry.optString("amount", "0"))
            val assetId = entry.optInt("assetID", 0)
            val spend = entry.optBoolean("spend", false)
            val assetName = com.privimemobile.wallet.assetTicker(assetId)
            if (spend) spending.add(amount to assetName) else receiving.add(amount to assetName)
        }

        if (spending.isNotEmpty()) {
            details.addView(sectionLabel(activity, activity.getString(R.string.balance_sending).uppercase(), dp))
            for ((amount, asset) in spending) {
                details.addView(TextView(activity).apply {
                    text = "-$amount $asset"
                    setTextColor(SEND_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(2))
                })
            }
        }

        if (receiving.isNotEmpty()) {
            details.addView(sectionLabel(activity, activity.getString(R.string.balance_receiving).uppercase(), dp))
            for ((amount, asset) in receiving) {
                details.addView(TextView(activity).apply {
                    text = "+$amount $asset"
                    setTextColor(RECEIVE_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(2))
                })
            }
        }

        if (fee != "0") {
            details.addView(sectionLabel(activity, activity.getString(R.string.general_fee).uppercase(), dp))
            details.addView(TextView(activity).apply {
                text = "${formatAmount(fee)} BEAM"
                setTextColor(TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
        }

        if (comment.isNotEmpty()) {
            details.addView(sectionLabel(activity, activity.getString(R.string.general_details).uppercase(), dp))
            details.addView(TextView(activity).apply {
                text = comment
                setTextColor(TEXT_SECONDARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 6
            })
        }

        if (!isEnough) {
            details.addView(TextView(activity).apply {
                text = activity.getString(R.string.dapp_insufficient_funds)
                setTextColor(ERROR_COLOR)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(12), 0, 0)
            })
        }

        // Buttons row
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        card.addView(btnRow)

        // Reject button
        val rejectBtn = TextView(activity).apply {
            text = activity.getString(R.string.tx_reject)
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(BTN_REJECT_BG)
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                dialog.dismiss()
                reject(request)
            }
        }
        btnRow.addView(rejectBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        // Approve button
        if (isEnough) {
            val approveBtn = TextView(activity).apply {
                text = activity.getString(R.string.tx_approve)
                setTextColor(Color.parseColor("#032E49"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    setColor(ACCENT_COLOR)
                    cornerRadius = dp(10).toFloat()
                }
                background = bg
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener {
                    dialog.dismiss()
                    com.privimemobile.wallet.TxAuthHelper.authenticateBeforeAction(
                        activity = activity,
                        actionLabel = activity.getString(R.string.tx_approve_dapp),
                        onApproved = { approve(request) },
                        onRejected = { reject(request) },
                    )
                }
            }
            btnRow.addView(approveBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            })
        }

        dialog.setContentView(overlay)
        dialog.setOnCancelListener { reject(request) }

        overlay.alpha = 0f
        card.scaleX = 0.85f; card.scaleY = 0.85f; card.alpha = 0f
        dialog.show()

        overlay.animate().alpha(1f).setDuration(200).start()
        card.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(300).setStartDelay(50)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()

        Log.d(TAG, "Showing native TX approval dialog")
    }

    private fun sectionLabel(activity: android.app.Activity, text: String, dp: (Int) -> Int): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }
    }

    /** Access EncryptedSharedPreferences for wallet password + biometrics settings. */
    private fun getEncryptedPrefs(activity: android.app.Activity): SharedPreferences? {
        return try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(activity)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                activity,
                "privimw_secure_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to access encrypted prefs: ${e.message}")
            null
        }
    }

    /** Check if any auth is required on send (password or biometrics) */
    private fun isAuthRequired(activity: android.app.Activity): Boolean {
        val prefs = getEncryptedPrefs(activity) ?: return false
        val askPassword = prefs.getBoolean("ask_password_on_send", false)
        val fingerprint = prefs.getBoolean("fingerprint_enabled", false)
        return askPassword || fingerprint
    }

    /** Check if biometrics is enabled */
    private fun isBiometricsEnabled(activity: android.app.Activity): Boolean {
        return getEncryptedPrefs(activity)?.getBoolean("fingerprint_enabled", false) ?: false
    }

    /** Get stored wallet password for verification */
    private fun getStoredPassword(activity: android.app.Activity): String? {
        return getEncryptedPrefs(activity)?.getString("wallet_password", null)
    }

    /** Show Android system biometric prompt */
    private fun showBiometricPrompt(activity: android.app.Activity, request: String, txDialog: Dialog) {
        val cancelSignal = CancellationSignal()
        val prompt = BiometricPromptApi.Builder(activity)
            .setTitle(activity.getString(R.string.tx_approve_title))
            .setSubtitle(activity.getString(R.string.tx_authenticate_confirm))
            .setNegativeButton(activity.getString(R.string.lock_use_password_button), activity.mainExecutor) { _, _ ->
                // Biometric cancelled — fall back to password
                txDialog.dismiss()
                showPasswordPrompt(activity, request)
            }
            .build()

        prompt.authenticate(cancelSignal, activity.mainExecutor,
            object : BiometricPromptApi.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPromptApi.AuthenticationResult) {
                    Log.d(TAG, "Biometric auth succeeded")
                    txDialog.dismiss()
                    approve(request)
                }
                override fun onAuthenticationFailed() {
                    // Single attempt failed — prompt stays open for retry
                    Log.d(TAG, "Biometric auth failed (retry)")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.d(TAG, "Biometric auth error: $errString")
                    // User cancelled or too many attempts — fall back to password
                    txDialog.dismiss()
                    showPasswordPrompt(activity, request)
                }
            }
        )
    }

    /** Show password prompt before approving TX */
    private fun showPasswordPrompt(activity: android.app.Activity, request: String) {
        val dp = { value: Int -> TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
        ).toInt() }

        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)

        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(OVERLAY_COLOR)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnClickListener {
                dialog.dismiss()
                reject(request)
            }
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(BG_COLOR)
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnClickListener { /* absorb */ }
        }
        overlay.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.send_confirm_enter_password)
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })

        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.tx_approve_password_subtitle)
            setTextColor(TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val passwordInput = EditText(activity).apply {
            hint = activity.getString(R.string.general_password)
            setHintTextColor(0xFF557080.toInt())
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            val bg = GradientDrawable().apply {
                setColor(0xFF0D3254.toInt())
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(passwordInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        card.addView(btnRow)

        val cancelBtn = TextView(activity).apply {
            text = activity.getString(R.string.general_cancel)
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(BTN_REJECT_BG)
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                dialog.dismiss()
                reject(request)
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val confirmBtn = TextView(activity).apply {
            text = activity.getString(R.string.general_confirm)
            setTextColor(Color.parseColor("#032E49"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(ACCENT_COLOR)
                cornerRadius = dp(10).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                val entered = passwordInput.text.toString()
                val stored = getStoredPassword(activity)
                if (stored != null && entered == stored) {
                    dialog.dismiss()
                    approve(request)
                } else {
                    Toast.makeText(activity, activity.getString(R.string.tx_incorrect_password), Toast.LENGTH_SHORT).show()
                    passwordInput.text.clear()
                }
            }
        }
        btnRow.addView(confirmBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })

        dialog.setContentView(overlay)
        dialog.setOnCancelListener { reject(request) }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        overlay.alpha = 0f
        card.scaleX = 0.85f; card.scaleY = 0.85f; card.alpha = 0f
        dialog.show()

        overlay.animate().alpha(1f).setDuration(200).start()
        card.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(300).setStartDelay(50)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()
    }

    private fun approve(request: String) {
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            wallet.contractInfoApproved(request)
        }
    }

    private fun reject(request: String) {
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            wallet.contractInfoRejected(request)
        }
    }

    private fun formatAmount(beamStr: String): String {
        return try {
            val num = beamStr.replace(',', '.').toDouble()
            if (num == 0.0) "0"
            else String.format("%.8f", num).trimEnd('0').trimEnd { it == '.' || it == ',' }
        } catch (_: Exception) {
            beamStr
        }
    }
}
