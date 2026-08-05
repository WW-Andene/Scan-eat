package fr.scanneat.presentation.scan

import android.content.Context
import android.net.ConnectivityManager
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.Profile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Covers onBarcodeDetected()'s photo-queue guard - added after a real
// user-reported bug: CameraPreview's barcode detector keeps running on every
// live frame regardless of whether photos are already queued, and score()
// always prefers a held barcode over the photo queue by design (so a barcode
// detected first can be augmented with follow-up photos when OFF's entry for
// it is sparse). Before this guard, any barcode picked up incidentally while
// framing a shot - background clutter, a neighboring product swept past -
// silently hijacked the next Score tap into a lookup for a product the user
// never meant to scan, completely ignoring the photos just taken. See
// ScanViewModel.onBarcodeDetected's own doc comment for the full rationale.
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val scanRepo = mockk<ScanRepository>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val medicationRepo = mockk<MedicationRepository>(relaxed = true)
    private val priceRepo = mockk<PriceRepository>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)

    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.language } returns flowOf("fr")
        every { prefs.profile } returns flowOf(Profile())
        every { scanRepo.observeTodayScanCount() } returns flowOf(0)
        coEvery { scanRepo.getCachedByBarcode(any(), any()) } returns null

        viewModel = ScanViewModel(scanRepo, prefs, connectivityManager, medicationRepo, priceRepo, appContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * addPhoto()/identifyShelfBox() now take an already-built ImagePayload -
     * toPayload()'s Bitmap scale/compress work moved to the caller (CameraPreview's
     * background capture executor / ScanScreen's Dispatchers.Default block), so
     * these tests (which only exercise the photo-queue guard logic, not image
     * processing) no longer need to fake a Bitmap or mock android.util.Base64 at all.
     */
    private fun fakePayload() = ImagePayload(base64 = "fake-base64")

    /**
     * onBarcodeDetected() now requires the same *new* barcode to win several
     * consecutive detections before committing to it (see its own doc comment -
     * added to reject two barcodes' camera-frame detection order flickering the
     * held target back and forth when both are simultaneously in frame). A
     * single call - which was enough before that fix - no longer adopts a fresh
     * barcode, so every test simulating "this barcode is now the live-detected
     * one" needs to call it repeatedly, exactly like the real analyzer would
     * across several consecutive frames of the same physical barcode.
     */
    private fun detectStably(barcode: String) {
        repeat(3) { viewModel.onBarcodeDetected(barcode) }
    }

    @Test
    fun `barcode detected before any photo is held for later scoring`() {
        detectStably("3017620422003")
        assertEquals("3017620422003", viewModel.scannedBarcode.value)
    }

    @Test
    fun `a single detection does not yet adopt a fresh barcode`() {
        // Guards the debounce itself: two products simultaneously in frame
        // must not flicker the held target after just one frame's detection.
        viewModel.onBarcodeDetected("3017620422003")
        assertNull(viewModel.scannedBarcode.value)
    }

    @Test
    fun `new barcode is ignored once a photo has been queued`() {
        // The exact user-reported scenario: photos taken first (no barcode
        // held yet), then the live feed incidentally detects one.
        viewModel.addPhoto(fakePayload())
        viewModel.onBarcodeDetected("3017620422003")
        assertNull(
            "a barcode detected after photos exist must not be adopted",
            viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `barcode held before photos were taken survives once photos are added`() {
        // The deliberate barcode-plus-augmenting-photos flow must keep
        // working: a barcode detected first is the legitimate case for
        // scoreBarcode() to combine with follow-up photos when OFF's own
        // entry for it is sparse.
        detectStably("3017620422003")
        viewModel.addPhoto(fakePayload())
        assertEquals(
            "a barcode already held before photos existed must be kept",
            "3017620422003", viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `clearQueue lets a fresh barcode be adopted again`() {
        viewModel.addPhoto(fakePayload())
        detectStably("111")
        assertNull(viewModel.scannedBarcode.value)

        viewModel.clearQueue()
        detectStably("222")
        assertEquals(
            "after the photo queue is cleared, a new barcode should be adopted again",
            "222", viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `repeated detection of the same barcode does not re-set it`() {
        detectStably("111")
        // Already held - further detections of the same code must be a no-op,
        // not restart the stability count or otherwise disturb the held value.
        viewModel.onBarcodeDetected("111")
        viewModel.onBarcodeDetected("111")
        assertEquals("111", viewModel.scannedBarcode.value)
    }

    @Test
    fun `resultConsumed clears the photo queue so the next scan starts fresh`() {
        // Without this, a leftover photo from a just-completed scan would combine
        // with the barcode-queue guard above to permanently block every future
        // barcode detection, not just resubmit stale photos.
        viewModel.addPhoto(fakePayload())
        assertEquals(1, viewModel.images.value.size)

        viewModel.resultConsumed()
        assertEquals(
            "photo queue must be empty once a scan is fully consumed",
            0, viewModel.images.value.size,
        )

        detectStably("444")
        assertEquals(
            "a new barcode must be adoptable again after resultConsumed()",
            "444", viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `onBarcodeLost resets the in-progress stability streak`() {
        // A candidate barcode that hasn't yet won BARCODE_STABILITY_FRAMES
        // detections and then leaves frame must not silently "continue" its
        // count if the same digits reappear later by coincidence - it should
        // need a fresh full streak, same as any other first-time detection.
        viewModel.onBarcodeDetected("555")
        viewModel.onBarcodeDetected("555")
        viewModel.onBarcodeLost()
        viewModel.onBarcodeDetected("555")
        assertNull(
            "a streak interrupted by onBarcodeLost() must restart, not resume",
            viewModel.scannedBarcode.value,
        )
    }
}
