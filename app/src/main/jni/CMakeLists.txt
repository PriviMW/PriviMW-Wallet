cmake_minimum_required(VERSION 3.22.1)
project(voice C)

# libogg sources
set(OGG_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/ogg/src/framing.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/ogg/src/bitwise.c
)

add_library(ogg STATIC ${OGG_SOURCES})
target_include_directories(ogg PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/ogg/include
)

# libopus sources - from official silk_sources.mk, celt_sources.mk, opus_sources.mk
# Using FIXED_POINT build (no floating point)

# Main opus sources (src/)
set(OPUS_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_decoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_encoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/extensions.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/repacketizer.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_multistream.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_multistream_encoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_multistream_decoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_projection_encoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/opus_projection_decoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src/mapping_matrix.c
)

# SILK sources (base - from SILK_SOURCES)
set(SILK_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/CNG.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/code_signs.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/init_decoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_core.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_frame.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_parameters.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_indices.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_pulses.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decoder_set_fs.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/dec_API.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/enc_API.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/encode_indices.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/encode_pulses.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/gain_quant.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/interpolate.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/LP_variable_cutoff.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_decode.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NSQ.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NSQ_del_dec.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/PLC.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/shell_coder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_gain.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_LTP.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_NLSF_CB_NB_MB.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_NLSF_CB_WB.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_other.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_pitch_lag.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/tables_pulses_per_block.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/VAD.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/control_audio_bandwidth.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/quant_LTP_gains.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/VQ_WMat_EC.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/HP_variable_cutoff.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_encode.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_VQ.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_unpack.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_del_dec_quant.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/process_NLSFs.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_LR_to_MS.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_MS_to_LR.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/check_control_input.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/control_SNR.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/init_encoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/control_codec.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/A2NLSF.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/ana_filt_bank_1.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/biquad_alt.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/bwexpander_32.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/bwexpander.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/debug.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/decode_pitch.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/inner_prod_aligned.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/lin2log.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/log2lin.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/LPC_analysis_filter.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/LPC_inv_pred_gain.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/table_LSF_cos.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF2A.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_stabilize.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/NLSF_VQ_weights_laroia.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/pitch_est_tables.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_down2_3.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_down2.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_private_AR2.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_private_down_FIR.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_private_IIR_FIR.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_private_up2_HQ.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/resampler_rom.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/sigm_Q15.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/sort.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/sum_sqr_shift.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_decode_pred.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_encode_pred.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_find_predictor.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/stereo_quant_pred.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/LPC_fit.c
)

# SILK fixed-point sources (from SILK_SOURCES_FIXED)
set(SILK_FIXED_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/LTP_analysis_filter_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/LTP_scale_ctrl_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/corrMatrix_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/encode_frame_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/find_LPC_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/find_LTP_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/find_pitch_lags_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/find_pred_coefs_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/noise_shape_analysis_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/process_gains_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/regularize_correlations_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/residual_energy16_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/residual_energy_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/warped_autocorrelation_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/apply_sine_window_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/autocorr_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/burg_modified_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/k2a_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/k2a_Q16_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/pitch_analysis_core_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/vector_ops_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/schur64_FIX.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed/schur_FIX.c
)

# CELT sources (from CELT_SOURCES)
set(CELT_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/bands.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/celt.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/celt_encoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/celt_decoder.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/cwrs.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/entcode.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/entdec.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/entenc.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/kiss_fft.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/laplace.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/mathops.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/mdct.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/modes.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/pitch.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/celt_lpc.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/quant_bands.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/rate.c
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt/vq.c
)

# Combined opus library
add_library(opus STATIC
    ${OPUS_SOURCES}
    ${SILK_SOURCES}
    ${SILK_FIXED_SOURCES}
    ${CELT_SOURCES}
)
target_include_directories(opus PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/include
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/src
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/silk/fixed
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/celt
)
target_compile_definitions(opus PRIVATE
    OPUS_BUILD=1
    FIXED_POINT=1
    DISABLE_FLOAT_API=1
    VAR_ARRAYS=1
)

# Voice JNI library
add_library(voice SHARED voice.c)
target_include_directories(voice PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/opus/include
    ${CMAKE_CURRENT_SOURCE_DIR}/lib/ogg/include
)
target_link_libraries(voice
    opus
    ogg
    log
)