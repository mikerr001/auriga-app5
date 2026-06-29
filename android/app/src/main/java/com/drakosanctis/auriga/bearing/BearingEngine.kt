package com.drakosanctis.auriga.bearing

import kotlin.math.abs

/**
 * Auriga Bearing / Direction Engine
 *
 * Ported from auriga-hazard-engine/direction.ts (TypeScript) -> Kotlin.
 *
 * Determines LEFT | SLIGHT_LEFT | CENTER | SLIGHT_RIGHT | RIGHT
 * from multiple input sources with fallback chain.
 */

enum class Direction {
    LEFT, SLIGHT_LEFT, CENTER, SLIGHT_RIGHT, RIGHT
}

object BearingEngine {

    /**
     * Resolve direction from bearing data or bounding box position.
     *
     * Priority:
     * 1. bearingDegrees (computed from camera FOV + pixel offset)
     * 2. boundingBoxCenterX relative to image width (0-1 normalized)
     * 3. Default: CENTER
     *
     * Bearing zones (degrees from forward, symmetric):
     *   |angle| < 10°            -> CENTER
     *   10° <= |angle| < 22°     -> SLIGHT_LEFT / SLIGHT_RIGHT
     *   22° <= |angle|           -> LEFT / RIGHT
     *
     * BoundingBox zones (normalized 0-1):
     *   < 0.25       -> LEFT
     *   0.25 - 0.40  -> SLIGHT_LEFT
     *   0.40 - 0.60  -> CENTER
     *   0.60 - 0.75  -> SLIGHT_RIGHT
     *   > 0.75       -> RIGHT
     */
    fun resolveDirection(
        bearingDegrees: Float? = null,
        boundingBoxCenterX: Float? = null
    ): Direction {
        if (bearingDegrees != null) {
            val abs = abs(bearingDegrees)
            val isLeft = bearingDegrees < 0
            return when {
                abs < 10f -> Direction.CENTER
                abs < 22f -> if (isLeft) Direction.SLIGHT_LEFT else Direction.SLIGHT_RIGHT
                else -> if (isLeft) Direction.LEFT else Direction.RIGHT
            }
        }

        if (boundingBoxCenterX != null) {
            val x = boundingBoxCenterX
            return when {
                x < 0.25f -> Direction.LEFT
                x < 0.40f -> Direction.SLIGHT_LEFT
                x < 0.60f -> Direction.CENTER
                x < 0.75f -> Direction.SLIGHT_RIGHT
                else -> Direction.RIGHT
            }
        }

        return Direction.CENTER
    }

    /**
     * Computes bearing in degrees from a detected object's horizontal pixel offset
     * and the camera's known horizontal field of view.
     *
     * bearingDegrees = ((pixelX - imageWidth/2) / (imageWidth/2)) * (horizontalFovDegrees/2)
     *
     * Negative = left of center, Positive = right of center.
     */
    fun bearingFromPixelOffset(
        objectCenterPx: Float,
        imageWidthPx: Float,
        horizontalFovDegrees: Float
    ): Float {
        require(imageWidthPx > 0f) { "imageWidthPx must be > 0" }
        val halfWidth = imageWidthPx / 2f
        val normalizedOffset = (objectCenterPx - halfWidth) / halfWidth
        return normalizedOffset * (horizontalFovDegrees / 2f)
    }

    /** Human-readable description of a directional hazard, for TTS output. */
    fun describeDirection(direction: Direction): String = when (direction) {
        Direction.LEFT -> "to the left"
        Direction.SLIGHT_LEFT -> "slightly to the left"
        Direction.CENTER -> "directly ahead"
        Direction.SLIGHT_RIGHT -> "slightly to the right"
        Direction.RIGHT -> "to the right"
    }
}
