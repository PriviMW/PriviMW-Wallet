package com.privimemobile.wallet

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reformat native CSV exports for better readability.
 *
 * Native CSVs use human-readable dates and spaced column names.
 * These functions convert them to ISO 8601 timestamps and clean headers.
 */
object CsvExportUtil {

    private val NATIVE_DATE = SimpleDateFormat("dd MMM yyyy | HH:mm", Locale.ENGLISH)

    /**
     * Reformat the transactions CSV.
     *
     * Native: Type,Date | Time,Amount,Unit name,"Amount, USD","Amount, BTC","Transaction fee, BEAM",Status,Comment,Transaction ID,Kernel ID,Sending address,Sending wallet's signature,Receiving address,Receiving wallet's signature,Token,Payment proof
     * Output: Timestamp (UTC),Type,Amount,Asset,Transaction fee (BEAM),Status,Comment,Transaction ID,Kernel ID,Sending address,Sending wallet's signature,Receiving address,Receiving wallet's signature,Token,Payment proof
     */
    fun reformatTransactionsCsv(nativeCsv: String): String {
        val lines = nativeCsv.split("\n")
        if (lines.size < 2) return nativeCsv

        val header = "Timestamp (UTC),Type,Amount,Asset,Transaction fee (BEAM),Status,Comment,Transaction ID,Kernel ID,Sending address,Sending wallet's signature,Receiving address,Receiving wallet's signature,Token,Payment proof"
        val result = StringBuilder(header).append('\n')

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val reformatted = reformatTransactionsLine(line)
            if (reformatted != null) result.append(reformatted).append('\n')
        }

        return result.toString()
    }

    private fun reformatTransactionsLine(line: String): String? {
        val fields = parseCsvLine(line)
        // Native: [0]Type [1]Date|Time [2]Amount [3]Unit [4]USD [5]BTC [6]Fee [7]Status [8]Comment [9]TxID [10]KernelID [11]SendAddr [12]SendSig [13]RecvAddr [14]RecvSig [15]Token [16]Proof
        if (fields.size < 17) return null

        val reformattedTimestamp = convertToIsoUtc(fields[1].trim()) ?: return null

        return buildString {
            append(reformattedTimestamp)
            append(',')
            append(fields[0].trim().trim('"')) // Type
            append(',')
            append(fields[2].trim().trim('"')) // Amount
            append(',')
            append(fields[3].trim().trim('"')) // Asset
            append(',')
            append(fields[6].trim().trim('"')) // Transaction fee (BEAM)
            append(',')
            append(fields[7].trim().trim('"')) // Status
            append(',')
            append(fields[8].trim().trim('"')) // Comment
            append(',')
            append(fields[9].trim().trim('"')) // Transaction ID
            append(',')
            append(fields[10].trim().trim('"')) // Kernel ID
            append(',')
            append(fields[11].trim().trim('"')) // Sending address
            append(',')
            append(fields[12].trim().trim('"')) // Sending wallet's signature
            append(',')
            append(fields[13].trim().trim('"')) // Receiving address
            append(',')
            append(fields[14].trim().trim('"')) // Receiving wallet's signature
            append(',')
            append(fields[15].trim().trim('"')) // Token
            append(',')
            append(fields[16].trim().trim('"')) // Payment proof
        }
    }

    /**
     * Reformat the assets_swap CSV.
     *
     * Native: Date | Time,Amount sent,Unit name sent,Amount received,Unit name received,"Transaction fee, BEAM",Status,Comment,Transaction ID,Kernel ID,Peer address,My address
     * Output: Timestamp (UTC),Amount sent,Asset sent,Amount received,Asset received,Transaction fee (BEAM),Status,Comment,Transaction ID,Kernel ID,Peer address,My address
     */
    fun reformatAssetsSwapCsv(nativeCsv: String): String {
        val lines = nativeCsv.split("\n")
        if (lines.size < 2) return nativeCsv

        val header = "Timestamp (UTC),Amount sent,Asset sent,Amount received,Asset received,Transaction fee (BEAM),Status,Comment,Transaction ID,Kernel ID,Peer address,My address"
        val result = StringBuilder(header).append('\n')

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val reformatted = reformatAssetsSwapLine(line)
            if (reformatted != null) result.append(reformatted).append('\n')
        }

        return result.toString()
    }

    private fun reformatAssetsSwapLine(line: String): String? {
        val fields = parseCsvLine(line)
        // Native: [0]Date|Time [1]AmtSent [2]AssetSent [3]AmtRecv [4]AssetRecv [5]Fee [6]Status [7]Comment [8]TxID [9]KernelID [10]PeerAddr [11]MyAddr
        if (fields.size < 12) return null

        val reformattedTimestamp = convertToIsoUtc(fields[0].trim()) ?: return null

        return buildString {
            append(reformattedTimestamp)
            append(',')
            append(fields[1].trim()) // Amount sent
            append(',')
            append(fields[2].trim()) // Asset sent
            append(',')
            append(fields[3].trim()) // Amount received
            append(',')
            append(fields[4].trim()) // Asset received
            append(',')
            append(fields[5].trim().trim('"')) // Transaction fee
            append(',')
            append(fields[6].trim()) // Status
            append(',')
            append(fields[7].trim().trim('"')) // Comment
            append(',')
            append(fields[8].trim().trim('"')) // Transaction ID
            append(',')
            append(fields[9].trim().trim('"')) // Kernel ID
            append(',')
            append(fields[10].trim().trim('"')) // Peer address
            append(',')
            append(fields[11].trim().trim('"')) // My address
        }
    }

    private fun convertToIsoUtc(nativeDate: String): String? {
        return try {
            val parsed = NATIVE_DATE.parse(nativeDate)
            if (parsed == null) return null
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            iso.format(parsed.time)
        } catch (_: Exception) {
            nativeDate // Fallback: keep original if parse fails
        }
    }

    /**
     * Reformat the contracts CSV.
     *
     * Native: Date | Time,Send,Receive,"Transaction fee, BEAM",Status,DApp name,Application shader ID,Description,Transaction ID,Kernel ID
     * Output: Timestamp (UTC),Send,Receive,"Transaction fee, BEAM",Status,DApp name,Application shader ID,Description,Transaction ID,Kernel ID
     */
    fun reformatContractsCsv(nativeCsv: String): String {
        val lines = nativeCsv.split("\n")
        if (lines.size < 2) return nativeCsv

        val header = "Timestamp (UTC),Amount sent,Asset sent,Amount received,Asset received,Transaction fee (BEAM),Status,DApp name,Application shader ID,Description,Transaction ID,Kernel ID"
        val result = StringBuilder(header).append('\n')

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val reformatted = reformatContractsLine(line)
            if (reformatted != null) result.append(reformatted).append('\n')
        }

        return result.toString()
    }

    private fun parseAmountAsset(value: String): Pair<String, String> {
        val trimmed = value.trim().trim('"')
        if (trimmed == "-" || trimmed.isEmpty()) return "-" to "-"
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        return if (parts.size >= 2) parts[0] to parts[1] else trimmed to "-"
    }

    private fun reformatContractsLine(line: String): String? {
        val fields = parseCsvLine(line)
        // Native: [0]Date|Time [1]Send [2]Receive [3]Fee [4]Status [5]DApp [6]ShaderID [7]Desc [8]TxID [9]KernelID
        if (fields.size < 10) return null

        val reformattedTimestamp = convertToIsoUtc(fields[0].trim()) ?: return null
        val (sendAmt, sendAsset) = parseAmountAsset(fields[1])
        val (recvAmt, recvAsset) = parseAmountAsset(fields[2])

        return buildString {
            append(reformattedTimestamp)
            append(',')
            append(sendAmt) // Send amount
            append(',')
            append(sendAsset) // Send Asset
            append(',')
            append(recvAmt) // Receive amount
            append(',')
            append(recvAsset) // Receive Asset
            append(',')
            append(fields[3].trim().trim('"')) // Transaction fee (BEAM)
            append(',')
            append(fields[4].trim().trim('"')) // Status
            append(',')
            append(fields[5].trim().trim('"')) // DApp name
            append(',')
            append(fields[6].trim().trim('"')) // Application shader ID
            append(',')
            append(fields[7].trim().trim('"')) // Description
            append(',')
            append(fields[8].trim().trim('"')) // Transaction ID
            append(',')
            append(fields[9].trim().trim('"')) // Kernel ID
        }
    }

    /** Simple CSV line parser that respects quoted fields. */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}
