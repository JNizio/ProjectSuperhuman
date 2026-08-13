package com.projectsuperhuman.next

/**
 * Verified Kokoro-82M v1.0 raw ONNX contract from the published v1.0 ONNX usage contract.
 *
 * Project Superhuman does not feed these tensors directly on Android; sherpa-onnx owns that layer.
 * The publisher example does not name the output tensor, so this contract intentionally records
 * the first output by index rather than inventing a graph output name.
 */
object Kokoro82MModelContract {
    const val INPUT_IDS = "input_ids"
    const val INPUT_STYLE = "style"
    const val INPUT_SPEED = "speed"
    const val AUDIO_OUTPUT_INDEX = 0

    const val MAX_GRAPH_SEQUENCE = 512
    const val REQUIRED_EDGE_PADDING_TOKENS = 2
    const val MAX_PHONEME_TOKENS = MAX_GRAPH_SEQUENCE - REQUIRED_EDGE_PADDING_TOKENS
    const val STYLE_DIMENSIONS = 256
    const val SAMPLE_RATE_HZ = 24_000
    const val VOICE_COUNT = 53

    const val INPUT_IDS_REPRESENTATION = "int64[1,N] phoneme-vocabulary token IDs with leading/trailing pad 0"
    const val STYLE_REPRESENTATION = "float32[1,256] selected from the voice style table by phoneme-token length"
    const val SPEED_REPRESENTATION = "float32[1]"
    const val OUTPUT_REPRESENTATION = "first model output; mono float waveform consumed as audio[0] at 24000 Hz"

    val androidRuntimeAssets = listOf(
        "model.onnx",
        "voices.bin",
        "tokens.txt",
        "espeak-ng-data/",
        "lexicon-us-en.txt"
    )
}
