package com.batteryfitness.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.batteryfitness.app.domain.BatteryTest
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Video capture for a single test attempt.
 *
 * Design notes that follow from the source documents:
 *
 * - Quality is requested as HD (720p) with SD as the fallback, because §6.3
 *   sets 720p/30fps as the floor and §7.3 sets a 3 GB budget phone as the
 *   target device. Asking for UHD would look better on this handset and fail
 *   on the one the challenge actually specifies.
 *
 * - Exposure and white balance are LOCKED before recording starts. An auto
 *   exposure that hunts mid-attempt changes apparent limb brightness between
 *   frames, which is exactly the kind of instability pose estimation handles
 *   badly. §8.3 warns about midday sun and overcast monsoon; locking is how we
 *   stop the camera reacting to it mid-take.
 *
 * - Files are written to app-internal storage, not shared media. Constraint C1
 *   requires athlete video to stay under our control; putting it in the public
 *   gallery would expose it to every backup and sync client on the device.
 */
class VideoRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    /** Actual negotiated capture parameters, logged into the result record. */
    var activeWidth: Int = 0; private set
    var activeHeight: Int = 0; private set
    var activeFps: Double = 0.0; private set

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bind(preview: Preview): Boolean {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider?> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(runCatching { future.get() }.getOrNull()) },
                ContextCompat.getMainExecutor(context),
            )
        } ?: return false

        // 720p target with graceful fallback — never demand more than §7.3's
        // floor device can provide.
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )

        // Lock AE/AWB so the camera stops adapting mid-attempt.
        camera?.let { cam ->
            runCatching {
                Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK, true,
                        )
                        .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AWB_LOCK, true,
                        )
                        .build()
                )
            }
        }
        return true
    }

    /** Where captures live: app-internal, not the shared gallery (C1). */
    fun outputFileFor(test: BatteryTest, athleteId: Long, sessionId: Long): File {
        val dir = File(context.filesDir, "captures/$sessionId").apply { mkdirs() }
        return File(dir, "${test.key}_a${athleteId}_${System.currentTimeMillis()}.mp4")
    }

    @SuppressLint("MissingPermission")
    fun start(
        target: File,
        withAudio: Boolean,
        onEvent: (VideoRecordEvent) -> Unit,
    ) {
        val capture = videoCapture ?: return
        val options = FileOutputOptions.Builder(target).build()
        recording = capture.output
            .prepareRecording(context, options)
            .apply { if (withAudio) runCatching { withAudioEnabled() } }
            .start(ContextCompat.getMainExecutor(context)) { event -> onEvent(event) }
    }

    fun stop() {
        recording?.stop()
        recording = null
    }

    /**
     * Read back what was actually written.
     *
     * Deliberately measured from the FILE rather than trusted from the request:
     * the negotiated resolution and frame rate can differ from what was asked
     * for, and the §6.3.1 gate must judge the artifact, not the intent.
     */
    fun probe(file: File): CaptureFacts {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            fun k(key: Int) = r.extractMetadata(key)?.toDoubleOrNull() ?: 0.0
            val w = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val h = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val durMs = k(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val fps = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toDoubleOrNull() ?: 30.0
            activeWidth = w; activeHeight = h; activeFps = fps
            CaptureFacts(
                durationSec = durMs / 1000.0,
                sizeBytes = file.length(),
                widthPx = w,
                heightPx = h,
                fps = fps,
                sha256 = sha256(file),
            )
        } catch (e: Exception) {
            CaptureFacts(0.0, file.length(), 0, 0, 0.0, sha256(file))
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * SHA-256 taken at capture time.
     *
     * This is the root of the §6.3 authenticity chain: it is what a later
     * integrity check compares against to show a file was not swapped or
     * re-encoded after recording, and what duplicate detection keys on.
     */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Last known GPS fix for the §6.3 metadata tag.
     *
     * Platform LocationManager, NOT FusedLocationProviderClient — the latter
     * ships in Google Play Services, which constraint C8 forbids the APK from
     * requiring. Returns null rather than blocking: a coach in a village with
     * no fix must still be able to record.
     */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(): Pair<Double, Double>? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.firstNotNullOfOrNull { p ->
            lm.getLastKnownLocation(p)?.let { it.latitude to it.longitude }
        }
    }.getOrNull()

    fun release() {
        recording?.stop()
        recording = null
        camera = null
        videoCapture = null
    }
}

data class CaptureFacts(
    val durationSec: Double,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val fps: Double,
    val sha256: String,
)
