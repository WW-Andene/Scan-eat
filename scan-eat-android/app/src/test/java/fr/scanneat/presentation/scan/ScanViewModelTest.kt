package fr.scanneat.presentation.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Base64
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.Profile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    private val appContext = mockk<Context>(relaxed = true)

    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.language } returns flowOf("fr")
        every { prefs.profile } returns flowOf(Profile())
        every { scanRepo.observeTodayScanCount() } returns flowOf(0)
        coEvery { scanRepo.getCachedByBarcode(any(), any()) } returns null

        // addPhoto()'s toPayload() calls the real android.util.Base64 - a
        // no-op stub in the plain android.jar this test compiles/runs
        // against, so it must be faked rather than left to throw.
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "fake-base64"

        viewModel = ScanViewModel(scanRepo, prefs, connectivityManager, medicationRepo, appContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Base64::class)
    }

    /**
     * Sized so toPayload()'s upload/thumbnail scale factors (1024/size,
     * 160/size) are both >= 1 - neither branch calls the real (native,
     * un-mocked here) Bitmap.createScaledBitmap, only the instance's own
     * compress()/recycle(), which the relaxed mock already handles.
     */
    private fun fakeBitmap(size: Int = 100): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.width } returns size
        every { bmp.height } returns size
        return bmp
    }

    @Test
    fun `barcode detected before any photo is held for later scoring`() {
        viewModel.onBarcodeDetected("3017620422003")
        assertEquals("3017620422003", viewModel.scannedBarcode.value)
    }

    @Test
    fun `new barcode is ignored once a photo has been queued`() {
        // The exact user-reported scenario: photos taken first (no barcode
        // held yet), then the live feed incidentally detects one.
        viewModel.addPhoto(fakeBitmap())
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
        viewModel.onBarcodeDetected("3017620422003")
        viewModel.addPhoto(fakeBitmap())
        assertEquals(
            "a barcode already held before photos existed must be kept",
            "3017620422003", viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `clearQueue lets a fresh barcode be adopted again`() {
        viewModel.addPhoto(fakeBitmap())
        viewModel.onBarcodeDetected("111")
        assertNull(viewModel.scannedBarcode.value)

        viewModel.clearQueue()
        viewModel.onBarcodeDetected("222")
        assertEquals(
            "after the photo queue is cleared, a new barcode should be adopted again",
            "222", viewModel.scannedBarcode.value,
        )
    }

    @Test
    fun `repeated detection of the same barcode does not re-set it`() {
        viewModel.onBarcodeDetected("111")
        viewModel.onBarcodeDetected("111")
        assertEquals("111", viewModel.scannedBarcode.value)
    }

    @Test
    fun `resultConsumed clears the photo queue so the next scan starts fresh`() {
        // Without this, a leftover photo from a just-completed scan would combine
        // with the barcode-queue guard above to permanently block every future
        // barcode detection, not just resubmit stale photos.
        viewModel.addPhoto(fakeBitmap())
        assertEquals(1, viewModel.images.value.size)

        viewModel.resultConsumed()
        assertEquals(
            "photo queue must be empty once a scan is fully consumed",
            0, viewModel.images.value.size,
        )

        viewModel.onBarcodeDetected("444")
        assertEquals(
            "a new barcode must be adoptable again after resultConsumed()",
            "444", viewModel.scannedBarcode.value,
        )
    }
}
