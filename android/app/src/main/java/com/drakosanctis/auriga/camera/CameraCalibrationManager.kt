package com.drakosanctis.auriga.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlin.math.atan
import kotlin.math.max

/**
 * Auriga Camera Calibration
 *
 * Reads the actual back camera's focal length and sensor size from
 * android.hardware.camera2.CameraCharacteristics, derives focal length in
 * pixels and horizontal FOV in degrees, and caches the result in
 * SharedPreferences so it's read once per device rather than on every
 * pipeline start.
 *
 * Falls back to the previous hardcoded constants (1400px focal length,
 * 70 degree horizontal FOV) if:
 * - The device doesn't expose LENS_INFO_AVAILABLE_FOCAL_LENGTHS or
 *   SENSOR_INFO_PHYSICAL_SIZE (some budget devices have incomplete
 *   Camera2 characteristic support).
 * - Camera access throws for any reason (permission timing, vendor bugs).
 *
 * This matters specifically for the target market: the wide range of
 * budget/mid-range Android phones means a single hardcoded calibration
 * constant will be measurably wrong on many real devices, directly
 * affecting Virtual Fiducial distance accuracy.
 */

data class CameraCalibrationProfile(
    val focalLengthPx: Float,
    val horizontalFovDegrees: Float,
    val isRealDeviceData: Boolean
)

class CameraCalibrationManager(private val context: Context) {

    companion object {
        private const val TAG = "AurigaCameraCalibration"
        private const val PREFS_NAME = "auriga_camera_calibration"
        private const val KEY_FOCAL_PX = "focal_length_px"
        private const val KEY_FOV_DEG = "horizontal_fov_degrees"
        private const val KEY_IS_REAL = "is_real_device_data"
        private const val KEY_CACHED_FOR_RESOLUTION_WIDTH = "cached_for_width"

        // Fallback constants — used only if real device data can't be read.
        const val FALLBACK_FOCAL_LENGTH_PX = 1400f
        const val FALLBACK_HORIZONTAL_FOV_DEGREES = 70f
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns a calibration profile, preferring a cached real-device reading,
     * falling back to a fresh Camera2 read, falling back to hardcoded
     * constants if both of those fail. [analyzedFrameWidthPx] should be the
     * actual pixel width of frames being analyzed (the focal-length-in-pixels
     * conversion depends on the resolution actually used, not the sensor's
     * native resolution).
     */
    fun getCalibration(analyzedFrameWidthPx: Int): CameraCalibrationProfile {
        val cached = readCache()
        if (cached != null && prefs.getInt(KEY_CACHED_FOR_RESOLUTION_WIDTH, -1) == analyzedFrameWidthPx) {
            return cached
        }

        val fresh = readFromCamera2(analyzedFrameWidthPx)
        if (fresh != null) {
            writeCache(fresh, analyzedFrameWidthPx)
            return fresh
        }

        Log.w(TAG, "Falling back to hardcoded calibration constants — could not read real device camera characteristics.")
        return CameraCalibrationProfile(
            focalLengthPx = FALLBACK_FOCAL_LENGTH_PX,
            horizontalFovDegrees = FALLBACK_HORIZONTAL_FOV_DEGREES,
            isRealDeviceData = false
        )
    }

    private fun readCache(): CameraCalibrationProfile? {
        if (!prefs.contains(KEY_FOCAL_PX)) return null
        return CameraCalibrationProfile(
            focalLengthPx = prefs.getFloat(KEY_FOCAL_PX, FALLBACK_FOCAL_LENGTH_PX),
            horizontalFovDegrees = prefs.getFloat(KEY_FOV_DEG, FALLBACK_HORIZONTAL_FOV_DEGREES),
            isRealDeviceData = prefs.getBoolean(KEY_IS_REAL, false)
        )
    }

    private fun writeCache(profile: CameraCalibrationProfile, forResolutionWidth: Int) {
        prefs.edit()
            .putFloat(KEY_FOCAL_PX, profile.focalLengthPx)
            .putFloat(KEY_FOV_DEG, profile.horizontalFovDegrees)
            .putBoolean(KEY_IS_REAL, profile.isRealDeviceData)
            .putInt(KEY_CACHED_FOR_RESOLUTION_WIDTH, forResolutionWidth)
            .apply()
    }

    /**
     * Reads CameraCharacteristics for the back camera and derives:
     *   focalLengthPx = (focalLengthMm / sensorWidthMm) * analyzedFrameWidthPx
     *   horizontalFovDegrees = 2 * atan(sensorWidthMm / (2 * focalLengthMm)) [converted to degrees]
     *
     * Returns null if any required characteristic is unavailable, rather
     * than guessing — guessing silently would defeat the purpose of reading
     * real data in the first place.
     */
    private fun readFromCamera2(analyzedFrameWidthPx: Int): CameraCalibrationProfile? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return null

            val backCameraId = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: return null

            val characteristics = manager.getCameraCharacteristics(backCameraId)

            val focalLengthsMm = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )
            val sensorSizeMm = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            )

            if (focalLengthsMm == null || focalLengthsMm.isEmpty() || sensorSizeMm == null) {
                Log.w(TAG, "Device does not expose focal length / sensor size characteristics.")
                return null
            }

            val focalLengthMm = focalLengthsMm[0]
            val sensorWidthMm = sensorSizeMm.width

            if (focalLengthMm <= 0f || sensorWidthMm <= 0f) return null

            val focalLengthPx = (focalLengthMm / sensorWidthMm) * analyzedFrameWidthPx
            val horizontalFovRadians = 2 * atan((sensorWidthMm / (2 * focalLengthMm)).toDouble())
            val horizontalFovDegrees = Math.toDegrees(horizontalFovRadians).toFloat()

            Log.i(
                TAG,
                "Read real camera calibration: focalLengthPx=$focalLengthPx, " +
                    "horizontalFovDegrees=$horizontalFovDegrees"
            )

            CameraCalibrationProfile(
                focalLengthPx = max(focalLengthPx, 1f),
                horizontalFovDegrees = horizontalFovDegrees,
                isRealDeviceData = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Camera2 characteristics", e)
            null
        }
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
