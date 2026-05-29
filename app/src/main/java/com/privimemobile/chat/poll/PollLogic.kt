package com.privimemobile.chat.poll

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure poll state transitions for [pollData] JSON:
 * `{ question, options: [{text, voters: [handle]}], closed?, closedAt? }`
 */
object PollLogic {

    enum class VoteRejectReason {
        CLOSED,
        ALREADY_VOTED,
        INVALID_OPTION,
    }

    sealed class VoteResult {
        data class Applied(val pollData: String) : VoteResult()
        data class Rejected(val reason: VoteRejectReason) : VoteResult()
    }

    fun isClosed(pollData: String): Boolean = parsePoll(pollData)?.optBoolean("closed", false) == true

    /** Option index the handle voted for, or null if not voted. */
    fun findVotedOptionIndex(pollData: String, handle: String): Int? {
        val poll = parsePoll(pollData) ?: return null
        val options = poll.optJSONArray("options") ?: return null
        for (i in 0 until options.length()) {
            val voters = options.getJSONObject(i).optJSONArray("voters") ?: continue
            for (j in 0 until voters.length()) {
                if (voters.optString(j) == handle) return i
            }
        }
        return null
    }

    fun hasVoted(pollData: String, handle: String): Boolean = findVotedOptionIndex(pollData, handle) != null

    /**
     * Record a single vote. Rejects closed polls, repeat taps, and vote changes.
     */
    fun applyVote(pollData: String, voterHandle: String, optionIndex: Int): VoteResult {
        val poll = parsePoll(pollData) ?: return VoteResult.Rejected(VoteRejectReason.INVALID_OPTION)
        if (poll.optBoolean("closed", false)) {
            return VoteResult.Rejected(VoteRejectReason.CLOSED)
        }
        val options = poll.optJSONArray("options")
            ?: return VoteResult.Rejected(VoteRejectReason.INVALID_OPTION)
        if (optionIndex < 0 || optionIndex >= options.length()) {
            return VoteResult.Rejected(VoteRejectReason.INVALID_OPTION)
        }
        if (hasVoted(pollData, voterHandle)) {
            return VoteResult.Rejected(VoteRejectReason.ALREADY_VOTED)
        }
        val opt = options.getJSONObject(optionIndex)
        val voters = opt.optJSONArray("voters") ?: JSONArray()
        voters.put(voterHandle)
        opt.put("voters", voters)
        options.put(optionIndex, opt)
        poll.put("options", options)
        return VoteResult.Applied(poll.toString())
    }

    fun applyClose(pollData: String, closedAtEpochSec: Long): String {
        val poll = parsePoll(pollData) ?: JSONObject()
        poll.put("closed", true)
        poll.put("closedAt", closedAtEpochSec)
        return poll.toString()
    }

    fun applyReopen(pollData: String): String {
        val poll = parsePoll(pollData) ?: JSONObject()
        poll.put("closed", false)
        poll.remove("closedAt")
        return poll.toString()
    }

    private fun parsePoll(pollData: String): JSONObject? =
        try {
            JSONObject(pollData)
        } catch (_: Exception) {
            null
        }
}
