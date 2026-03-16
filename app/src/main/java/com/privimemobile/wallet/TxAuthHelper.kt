package com.privimemobile.wallet

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricPrompt as BiometricPromptApi
import android.os.CancellationSignal
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Reusable TX authentication helper — biometric + password verification.
 * Used by: NativeTxApprovalDialog (DApp TXs), ShaderInvoker (protocol TXs),
 * and any other code path that sends blockchain transactions.
 */
object TxAuthHelper {
    private const val TAG = "TxAuthHelper"

    private const val BG_COLOR = 0xFF032E49.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val TEXT_SECONDARY = 0xFF8DA1B5.toInt()
    private const val ACCENT_COLOR = 0xFF00F6D2.toInt()
    private const val BTN_REJECT_BG = 0xFF0A3D5C.toInt()
    private const val OVERLAY_COLOR = 0xCC000000.toInt()

    private fun dp(activity: Activity, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
        ).toInt()
    }

    private fun getEncryptedPrefs(activity: Activity): android.content.SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(activity)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                activity, "privimw_secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Check if any auth is required on send (password or biometrics) */
    fun isAuthRequired(activity: Activity): Boolean {
        val prefs = getEncryptedPrefs(activity) ?: return false
        // SecureStorage stores these as booleans (putBoolean), not strings
        val askPassword = prefs.getBoolean("ask_password_on_send", false)
        val fingerprint = prefs.getBoolean("fingerprint_enabled", false)
        return askPassword || fingerprint
    }

    /** Check if biometrics is enabled */
    fun isBiometricsEnabled(activity: Activity): Boolean {
        return getEncryptedPrefs(activity)?.getBoolean("fingerprint_enabled", false) ?: false
    }

    /** Get stored wallet password for verification */
    fun getStoredPassword(activity: Activity): String? {
        return getEncryptedPrefs(activity)?.getString("wallet_password", null)
    }

    /**
     * Authenticate before performing an action. If no auth required, runs onApproved immediately.
     * Otherwise shows biometric or password prompt first.
     *
     * @param activity The current Activity (for UI context)
     * @param actionLabel Description shown in the auth prompt (e.g., "Update messaging address")
     * @param onApproved Called after successful authentication
     * @param onRejected Called if user cancels authentication
     */
    fun authenticateBeforeAction(
        activity: Activity,
        actionLabel: String = "Confirm Transaction",
        onApproved: () -> Unit,
        onRejected: (() -> Unit)? = null,
    ) {
        if (!isAuthRequired(activity)) {
            onApproved()
            return
        }

        if (isBiometricsEnabled(activity)) {
            showBiometricPrompt(activity, actionLabel, onApproved, onRejected)
        } else {
            showPasswordPrompt(activity, actionLabel, onApproved, onRejected)
        }
    }

    /** Show Android system biometric prompt */
    private fun showBiometricPrompt(
        activity: Activity,
        actionLabel: String,
        onApproved: () -> Unit,
        onRejected: (() -> Unit)?,
    ) {
        val cancelSignal = CancellationSignal()
        val prompt = BiometricPromptApi.Builder(activity)
            .setTitle("Approve Transaction")
            .setSubtitle(actionLabel)
            .setNegativeButton("Use Password", activity.mainExecutor) { _, _ ->
                showPasswordPrompt(activity, actionLabel, onApproved, onRejected)
            }
            .build()

        prompt.authenticate(cancelSignal, activity.mainExecutor,
            object : BiometricPromptApi.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPromptApi.AuthenticationResult) {
                    Log.d(TAG, "Biometric auth succeeded")
                    onApproved()
                }
                override fun onAuthenticationFailed() {
                    Log.d(TAG, "Biometric auth failed (retry)")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.d(TAG, "Biometric auth error: $errString")
                    showPasswordPrompt(activity, actionLabel, onApproved, onRejected)
                }
            }
        )
    }

    /** Show password prompt */
    private fun showPasswordPrompt(
        activity: Activity,
        actionLabel: String,
        onApproved: () -> Unit,
        onRejected: (() -> Unit)?,
    ) {
        val dp = { value: Int -> dp(activity, value) }
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)

        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(OVERLAY_COLOR)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnClickListener {
                dialog.dismiss()
                onRejected?.invoke()
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
            text = "Enter Password"
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })

        card.addView(TextView(activity).apply {
            text = actionLabel
            setTextColor(TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val passwordInput = EditText(activity).apply {
            hint = "Password"
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
            text = "Cancel"
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
                onRejected?.invoke()
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val confirmBtn = TextView(activity).apply {
            text = "Confirm"
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
                    onApproved()
                } else {
                    Toast.makeText(activity, "Incorrect password", Toast.LENGTH_SHORT).show()
                    passwordInput.text.clear()
                }
            }
        }
        btnRow.addView(confirmBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })

        dialog.setContentView(overlay)
        dialog.setOnCancelListener { onRejected?.invoke() }
        dialog.show()
    }
}
