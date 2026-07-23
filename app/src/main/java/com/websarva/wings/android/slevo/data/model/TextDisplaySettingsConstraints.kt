package com.websarva.wings.android.slevo.data.model

/**
 * Text scale and line-height ranges and defaults shared by UI, persistence, and backup validation.
 *
 * The ranges are inclusive, and validation accepts only finite values within the corresponding
 * range so every accepted backup setting can be produced by the display settings controls.
 */
object TextDisplaySettingsConstraints {
    /** Inclusive range supported by all text scale settings. */
    val TEXT_SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.7f..1.6f

    /** Inclusive range supported by the line-height setting. */
    val LINE_HEIGHT_RANGE: ClosedFloatingPointRange<Float> = 1.2f..1.8f

    /** Default value for the shared text scale setting. */
    const val DEFAULT_TEXT_SCALE: Float = 1f

    /** Default value for the header text scale setting. */
    const val DEFAULT_HEADER_TEXT_SCALE: Float = 0.85f

    /** Default value for the body text scale setting. */
    const val DEFAULT_BODY_TEXT_SCALE: Float = 1f

    /** Default value for the line-height setting. */
    const val DEFAULT_LINE_HEIGHT: Float = 1.4f

    /**
     * Checks whether a text scale is finite and within [TEXT_SCALE_RANGE], including endpoints.
     */
    fun isValidTextScale(value: Float): Boolean =
        value.isFinite() && value in TEXT_SCALE_RANGE

    /**
     * Checks whether a line height is finite and within [LINE_HEIGHT_RANGE], including endpoints.
     */
    fun isValidLineHeight(value: Float): Boolean =
        value.isFinite() && value in LINE_HEIGHT_RANGE
}
