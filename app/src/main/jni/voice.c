/*
 * Voice JNI Bridge for PriviMW
 * Based on Telegram-X voice.c
 * Encodes PCM audio to OGG/Opus format
 *
 * Licensed under Apache 2.0
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <opus.h>
#include <ogg/ogg.h>

#define LOG_TAG "VoiceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Opus encoder settings
#define SAMPLE_RATE 48000
#define CHANNELS 1
#define FRAME_SIZE 960  // 20ms at 48kHz
#define BITRATE 32000   // 32kbps for voice
#define MAX_FRAME_SIZE 6*FRAME_SIZE
#define MAX_PACKET_SIZE 4000

// OGG encoding state
typedef struct {
    ogg_stream_state os;
    ogg_packet op;
    ogg_page og;
    int serialno;
    int granulepos;
    int packetId;
} OggState;

// Encoder state
typedef struct {
    OpusEncoder *encoder;
    OggState ogg;
    FILE *outfile;
    int finished;
    unsigned char packet[MAX_PACKET_SIZE];
} EncoderState;

static EncoderState *encoder = NULL;

// Write OGG page to file
static int write_page(ogg_page *og, FILE *out) {
    if (fwrite(og->header, 1, og->header_len, out) != og->header_len) {
        return -1;
    }
    if (fwrite(og->body, 1, og->body_len, out) != og->body_len) {
        return -1;
    }
    return 0;
}

// Write Opus header
static int write_opus_header(EncoderState *st) {
    unsigned char header[19];

    // "OpusHead" magic
    memcpy(header, "OpusHead", 8);
    header[8] = 1;  // Version
    header[9] = CHANNELS;
    header[10] = 0; header[11] = 0;  // Preskip (little-endian)
    // Sample rate (little-endian)
    header[12] = SAMPLE_RATE & 0xFF;
    header[13] = (SAMPLE_RATE >> 8) & 0xFF;
    header[14] = (SAMPLE_RATE >> 16) & 0xFF;
    header[15] = (SAMPLE_RATE >> 24) & 0xFF;
    header[16] = 0;  // Gain
    header[17] = 0;
    header[18] = 0;  // Channel mapping

    ogg_packet op;
    op.packet = header;
    op.bytes = 19;
    op.b_o_s = 1;  // Beginning of stream
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 0;

    ogg_stream_packetin(&st->ogg.os, &op);

    ogg_page og;
    while (ogg_stream_flush(&st->ogg.os, &og)) {
        if (write_page(&og, st->outfile) < 0) {
            return -1;
        }
    }

    return 0;
}

// Write Opus tags (minimal)
static int write_opus_tags(EncoderState *st) {
    const char *vendor = "PriviMW";
    unsigned char *tags;
    int vendor_len = strlen(vendor);
    int tags_len = 8 + 4 + vendor_len + 4;  // "OpusTags" + vendor_len + vendor + count

    tags = malloc(tags_len);
    if (!tags) return -1;

    int pos = 0;
    memcpy(tags + pos, "OpusTags", 8);
    pos += 8;
    // Vendor string length (little-endian)
    tags[pos++] = vendor_len & 0xFF;
    tags[pos++] = (vendor_len >> 8) & 0xFF;
    tags[pos++] = (vendor_len >> 16) & 0xFF;
    tags[pos++] = (vendor_len >> 24) & 0xFF;
    // Vendor string
    memcpy(tags + pos, vendor, vendor_len);
    pos += vendor_len;
    // User comment list length (0 comments)
    tags[pos++] = 0;
    tags[pos++] = 0;
    tags[pos++] = 0;
    tags[pos++] = 0;

    ogg_packet op;
    op.packet = tags;
    op.bytes = tags_len;
    op.b_o_s = 0;
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 1;

    ogg_stream_packetin(&st->ogg.os, &op);

    ogg_page og;
    while (ogg_stream_flush(&st->ogg.os, &og)) {
        if (write_page(&og, st->outfile) < 0) {
            free(tags);
            return -1;
        }
    }

    free(tags);
    return 0;
}

// Cleanup encoder state completely
static void cleanup_encoder() {
    if (!encoder) return;

    if (encoder->outfile) {
        fclose(encoder->outfile);
        encoder->outfile = NULL;
    }
    if (encoder->encoder) {
        opus_encoder_destroy(encoder->encoder);
        encoder->encoder = NULL;
    }
    ogg_stream_clear(&encoder->ogg.os);

    free(encoder);
    encoder = NULL;
}

/*
 * Class:     com_privimemobile_chat_voice_VoiceRecorderJni
 * Method:    nativeStartRecording
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_privimemobile_chat_voice_VoiceRecorderJni_nativeStartRecording
  (JNIEnv *env, jobject thiz, jstring path) {

    const char *filepath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!filepath) return -1;

    // Cleanup any existing recording (shouldn't happen, but be safe)
    if (encoder) {
        cleanup_encoder();
    }

    encoder = calloc(1, sizeof(EncoderState));
    if (!encoder) {
        (*env)->ReleaseStringUTFChars(env, path, filepath);
        return -1;
    }

    // Create Opus encoder
    int error;
    encoder->encoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, OPUS_APPLICATION_VOIP, &error);
    if (!encoder->encoder) {
        LOGE("Failed to create Opus encoder: %d", error);
        free(encoder);
        encoder = NULL;
        (*env)->ReleaseStringUTFChars(env, path, filepath);
        return -1;
    }

    // Set bitrate
    opus_encoder_ctl(encoder->encoder, OPUS_SET_BITRATE(BITRATE));

    // Open output file
    encoder->outfile = fopen(filepath, "wb");
    if (!encoder->outfile) {
        LOGE("Failed to open output file: %s", filepath);
        opus_encoder_destroy(encoder->encoder);
        free(encoder);
        encoder = NULL;
        (*env)->ReleaseStringUTFChars(env, path, filepath);
        return -1;
    }

    // Initialize OGG stream
    encoder->ogg.serialno = rand();
    ogg_stream_init(&encoder->ogg.os, encoder->ogg.serialno);
    encoder->ogg.granulepos = 0;
    encoder->ogg.packetId = 0;

    // Write headers
    write_opus_header(encoder);
    write_opus_tags(encoder);

    encoder->finished = 0;

    (*env)->ReleaseStringUTFChars(env, path, filepath);
    return 0;
}

/*
 * Class:     com_privimemobile_chat_voice_VoiceRecorderJni
 * Method:    nativeEncodeFrame
 * Signature: ([SI)I
 */
JNIEXPORT jint JNICALL Java_com_privimemobile_chat_voice_VoiceRecorderJni_nativeEncodeFrame
  (JNIEnv *env, jobject thiz, jshortArray samples, jint frameSize) {

    if (!encoder || !encoder->encoder) return -1;

    jshort *pcm = (*env)->GetShortArrayElements(env, samples, NULL);
    if (!pcm) return -1;

    // Encode frame
    int nbytes = opus_encode(encoder->encoder, pcm, frameSize, encoder->packet, MAX_PACKET_SIZE);
    (*env)->ReleaseShortArrayElements(env, samples, pcm, JNI_ABORT);

    if (nbytes < 0) {
        LOGE("Opus encode failed: %d", nbytes);
        return -1;
    }

    // Update granule position
    encoder->ogg.granulepos += frameSize;

    // Create OGG packet
    ogg_packet op;
    op.packet = encoder->packet;
    op.bytes = nbytes;
    op.b_o_s = 0;
    op.e_o_s = 0;
    op.granulepos = encoder->ogg.granulepos;
    op.packetno = encoder->ogg.packetId++;

    // Submit to OGG stream
    ogg_stream_packetin(&encoder->ogg.os, &op);

    // Write available pages
    ogg_page og;
    while (ogg_stream_pageout(&encoder->ogg.os, &og)) {
        if (write_page(&og, encoder->outfile) < 0) {
            return -1;
        }
    }

    return nbytes;
}

/*
 * Class:     com_privimemobile_chat_voice_VoiceRecorderJni
 * Method:    nativeStopRecording
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_privimemobile_chat_voice_VoiceRecorderJni_nativeStopRecording
  (JNIEnv *env, jobject thiz) {

    if (!encoder) return NULL;

    // Mark end of stream
    ogg_packet op;
    op.packet = NULL;
    op.bytes = 0;
    op.b_o_s = 0;
    op.e_o_s = 1;
    op.granulepos = encoder->ogg.granulepos;
    op.packetno = encoder->ogg.packetId;

    ogg_stream_packetin(&encoder->ogg.os, &op);

    // Flush remaining pages
    ogg_page og;
    while (ogg_stream_flush(&encoder->ogg.os, &og)) {
        write_page(&og, encoder->outfile);
    }

    // Cleanup (closes file, destroys encoder, clears stream)
    cleanup_encoder();

    // For now, return NULL - waveform computed in Kotlin
    // In a full implementation, we'd return waveform bytes here
    return NULL;
}

/*
 * Class:     com_privimemobile_chat_voice_VoiceRecorderJni
 * Method:    nativeCancelRecording
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_privimemobile_chat_voice_VoiceRecorderJni_nativeCancelRecording
  (JNIEnv *env, jobject thiz) {

    if (!encoder) return;

    // Get file path before cleanup (to delete in Kotlin)
    // Note: file deletion is handled by Kotlin code

    // Cleanup (closes file, destroys encoder, clears stream)
    cleanup_encoder();
}