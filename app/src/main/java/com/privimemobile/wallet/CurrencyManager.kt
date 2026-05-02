package com.privimemobile.wallet

import com.privimemobile.R
import com.privimemobile.protocol.SecureStorage

/**
 * Currency formatting, rate access, and user preference.
 *
 * CoinGecko rates are keyed `beam_usd`, `beam_sgd`, etc.
 * Formatting uses locale-aware number formatting with correct currency symbols.
 */
object CurrencyManager {

    /** Supported currencies with display labels and symbols. */
    val SUPPORTED = listOf(
        "usd" to Pair("US Dollar", "$"),
        "eur" to Pair("Euro", "€"),
        "gbp" to Pair("British Pound", "£"),
        "sgd" to Pair("Singapore Dollar", "S$"),
        "btc" to Pair("Bitcoin", "₿"),
        "eth" to Pair("Ethereum", "Ξ"),
        "jpy" to Pair("Japanese Yen", "¥"),
        "cad" to Pair("Canadian Dollar", "C$"),
        "aud" to Pair("Australian Dollar", "A$"),
        "cny" to Pair("Chinese Yuan", "¥"),
        "krw" to Pair("South Korean Won", "₩"),
        "inr" to Pair("Indian Rupee", "₹"),
        "brl" to Pair("Brazilian Real", "R$"),
        "chf" to Pair("Swiss Franc", "Fr."),
        "hkd" to Pair("Hong Kong Dollar", "HK$"),
        "nzd" to Pair("New Zealand Dollar", "NZ$"),
        "mxn" to Pair("Mexican Peso", "Mex$"),
        "rub" to Pair("Russian Ruble", "₽"),
        "sek" to Pair("Swedish Krona", "kr"),
        "nok" to Pair("Norwegian Krone", "kr"),
        "dkk" to Pair("Danish Krone", "kr"),
        "pln" to Pair("Polish Zloty", "zł"),
        "thb" to Pair("Thai Baht", "฿"),
        "idr" to Pair("Indonesian Rupiah", "Rp"),
        "php" to Pair("Philippine Peso", "₱"),
        "vnd" to Pair("Vietnamese Dong", "₫"),
        "try" to Pair("Turkish Lira", "₺"),
        "aed" to Pair("UAE Dirham", "dh"),
        "sar" to Pair("Saudi Riyal", "﷼"),
        "zar" to Pair("South African Rand", "R"),
        "ars" to Pair("Argentine Peso", "$"),
        "clp" to Pair("Chilean Peso", "$"),
        "twd" to Pair("Taiwan Dollar", "NT$"),
    )

    private val CURRENCY_MAP = SUPPORTED.toMap()

    fun getSymbol(currency: String): String = CURRENCY_MAP[currency.lowercase()]?.second ?: "$"
    fun getLabel(currency: String): String = CURRENCY_MAP[currency.lowercase()]?.first ?: currency.uppercase()

    /** Returns string resource ID for the currency's display name. */
    fun getLabelResId(currency: String): Int {
        val id = CURRENCY_LABEL_RES_IDS[currency.lowercase()]
        return id ?: android.R.string.unknownName
    }

    private val CURRENCY_LABEL_RES_IDS = mapOf(
        "usd" to R.string.currency_usd, "eur" to R.string.currency_eur,
        "gbp" to R.string.currency_gbp, "sgd" to R.string.currency_sgd,
        "btc" to R.string.currency_btc, "eth" to R.string.currency_eth,
        "jpy" to R.string.currency_jpy, "cad" to R.string.currency_cad,
        "aud" to R.string.currency_aud, "cny" to R.string.currency_cny,
        "krw" to R.string.currency_krw, "inr" to R.string.currency_inr,
        "brl" to R.string.currency_brl, "chf" to R.string.currency_chf,
        "hkd" to R.string.currency_hkd, "nzd" to R.string.currency_nzd,
        "mxn" to R.string.currency_mxn, "rub" to R.string.currency_rub,
        "sek" to R.string.currency_sek, "nok" to R.string.currency_nok,
        "dkk" to R.string.currency_dkk, "pln" to R.string.currency_pln,
        "thb" to R.string.currency_thb, "idr" to R.string.currency_idr,
        "php" to R.string.currency_php, "vnd" to R.string.currency_vnd,
        "try" to R.string.currency_try, "aed" to R.string.currency_aed,
        "sar" to R.string.currency_sar, "zar" to R.string.currency_zar,
        "ars" to R.string.currency_ars, "clp" to R.string.currency_clp,
        "twd" to R.string.currency_twd,
    )

    fun getPreferredCurrency(): String {
        return SecureStorage.getString(KEY_PREFERRED_CURRENCY, "usd") ?: "usd"
    }

    fun setPreferredCurrency(currency: String) {
        SecureStorage.putString(KEY_PREFERRED_CURRENCY, currency.lowercase())
    }

    /** Get current rate for a currency from the event bus. */
    fun getRate(currency: String): Double {
        val key = "beam_${currency.lowercase()}"
        return WalletEventBus.exchangeRates.value[key] ?: 0.0
    }

    /**
     * Format a fiat amount with symbol.
     * Examples: "$4,543.23", "S$7,424.43", "€1,234.56"
     */
    fun formatFiat(amount: Double, currency: String): String {
        val symbol = getSymbol(currency)
        val absAmount = kotlin.math.abs(amount)
        return when {
            absAmount < 0.01 && absAmount > 0 -> "$symbol< 0.01"
            absAmount >= 1_000_000 -> "$symbol${String.format("%,.2f", amount)}"
            absAmount >= 1 -> "$symbol${String.format("%,.2f", amount)}"
            else -> "$symbol${String.format("%.4f", amount)}"
        }
    }

    /**
     * Format total portfolio value for the main balance card.
     * Only appends the 3-letter currency code for ambiguous $-only symbols
     * (USD, ARS, CLP). For unique symbols (€, ₿, S$, etc.) the code is hidden.
     */
    fun formatPortfolioValue(amount: Double, currency: String): String {
        val base = formatFiat(amount, currency)
        val symbol = getSymbol(currency)
        // Pure "$" is ambiguous — append code. Prefixed $ (S$, C$, etc.) or other symbols are unique.
        return if (symbol == "$") "$base ${currency.uppercase()}" else base
    }

    /** Convert groth to fiat. Pass explicit rate for Compose-observable usage. */
    fun grothToFiat(groth: Long, currency: String, rate: Double? = null): Double {
        val actualRate = rate ?: getRate(currency)
        if (actualRate <= 0) return 0.0
        return groth.toDouble() / 100_000_000.0 * actualRate
    }

    /**
     * Convert raw asset units to fiat using DEX-derived pricing.
     * Returns null if the asset has no known price.
     *
     * @param assetId the Beam asset ID
     * @param rawAmount raw balance in smallest units (like groth for BEAM)
     * @param currency target fiat currency code (e.g. "usd", "eur")
     * @param beamRate preferred-currency BEAM rate (pass for Compose-observable; auto-reads if null)
     * @return fiat double value, or null if unpriced
     */
    fun assetToFiat(assetId: Int, rawAmount: Long, currency: String, beamRate: Double? = null): Double? {
        val rates = WalletEventBus.exchangeRates.value
        val beamRatio = rates["${assetId}_beam"] ?: return null
        if (beamRatio <= 0) return null
        val actualBeamRate = beamRate ?: getRate(currency)
        if (actualBeamRate <= 0) return null
        val decimals = rates["${assetId}_decimals"]?.toInt() ?: 8
        return rawAmount.toDouble() / Math.pow(10.0, decimals.toDouble()) * beamRatio * actualBeamRate
    }

    const val KEY_PREFERRED_CURRENCY = "preferred_currency"
}
