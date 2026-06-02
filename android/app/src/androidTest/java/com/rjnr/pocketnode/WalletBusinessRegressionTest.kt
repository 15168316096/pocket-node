package com.rjnr.pocketnode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

private const val BUSINESS_PKG = "com.rjnr.pocketnode"
private const val BUSINESS_LAUNCH_TIMEOUT_MS = 10_000L
private const val BUSINESS_SCREEN_TIMEOUT_MS = 20_000L

private const val BUSINESS_TEST_MNEMONIC =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

@RunWith(AndroidJUnit4::class)
class WalletBusinessRegressionTest {

    private lateinit var device: UiDevice
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ctx = ApplicationProvider.getApplicationContext()
    }

    @Rule
    @JvmField
    val failureCapture: TestWatcher = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            val tag = description.methodName
            val outDir = InstrumentationRegistry.getInstrumentation()
                .targetContext
                .externalCacheDir
                ?: return
            outDir.mkdirs()
            try {
                device.takeScreenshot(File(outDir, "fail-$tag.png"))
            } catch (_: Throwable) { /* best-effort */ }
            try {
                FileOutputStream(File(outDir, "fail-$tag.xml")).use { out ->
                    device.dumpWindowHierarchy(out)
                }
            } catch (_: Throwable) { /* best-effort */ }
        }
    }

    @Test
    fun createWalletReachesMnemonicVerifyGate() {
        launchApp()

        assertTrue(
            "Create wallet entry point not reachable",
            clickButton("onboarding-create-new", "Create New Wallet", BUSINESS_SCREEN_TIMEOUT_MS)
        )

        // Stock CI emulators have no device credential. That warning is part
        // of the expected first-run surface; continue so wallet creation can
        // proceed while still proving the advisory appears when needed.
        clickButton("no-lock-continue", "Continue anyway", 4_000L)

        assertTrue(
            "Wallet naming confirm button not reachable",
            clickButton("wallet-name-create", "Create", BUSINESS_SCREEN_TIMEOUT_MS)
        )

        assertTrue(
            "Mnemonic words did not render after creating a wallet",
            device.wait(Until.hasObject(By.res("mnemonic-words")), BUSINESS_SCREEN_TIMEOUT_MS)
        )

        assertTrue(
            "Mnemonic display continue button not reachable",
            clickButton("mnemonic-display-next", "I've Written Them Down", BUSINESS_SCREEN_TIMEOUT_MS)
        )

        assertTrue(
            "Mnemonic verification gate did not render",
            device.wait(Until.hasObject(By.res("mnemonic-verify")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
    }

    @Test
    fun importedWalletCoversCoreBusinessEntrypoints() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wallet-business-regression", BUSINESS_TEST_MNEMONIC))

        launchApp()

        assertTrue(
            "Recover wallet entry point not reachable",
            clickButton("onboarding-recover", "Recover from Seed Phrase", BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "Import paste button not reachable",
            clickButton("import-paste", "Paste from Clipboard", BUSINESS_SCREEN_TIMEOUT_MS)
        )
        device.waitForIdle(1_000L)
        assertTrue(
            "Import submit button not reachable",
            clickButton("import-submit", "Import Wallet", BUSINESS_SCREEN_TIMEOUT_MS)
        )

        // Mainnet imports show sync mode selection. Testnet does not.
        clickButton("sync-sheet-apply", "Apply", 8_000L)

        completePinSetup()

        assertHomeVisible()
        assertReceiveAddressReachable()
        assertSendFormReachable()
        assertDaoDepositFormReachable()
        assertSettingsNetworkSwitchDialogReachable()
    }

    private fun completePinSetup() {
        assertTrue(
            "PIN intro continue button not reachable",
            clickButton("pin-intro-continue", "Create PIN", BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "PIN setup phase did not advance",
            tapDigit1UntilTitleChanges("Create PIN", maxTaps = 12)
        )
        assertTrue(
            "PIN confirm phase did not render",
            device.wait(Until.hasObject(By.text("Confirm PIN").pkg(BUSINESS_PKG)), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "PIN confirm phase did not complete",
            tapDigit1UntilTitleChanges("Confirm PIN", maxTaps = 12, postTapsTimeoutMs = 90_000L)
        )
    }

    private fun assertHomeVisible() {
        val balanceRow = device.wait(
            Until.findObject(By.res("home-balance-row")),
            BUSINESS_SCREEN_TIMEOUT_MS
        )
        assertNotNull("Home balance row not visible after import + PIN", balanceRow)
    }

    private fun assertReceiveAddressReachable() {
        assertTrue("Receive action not reachable", clickButton("", "Receive", BUSINESS_SCREEN_TIMEOUT_MS))
        assertTrue(
            "Receive address row not visible",
            device.wait(Until.hasObject(By.res("receive-address")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "Receive copy address button not visible",
            device.wait(Until.hasObject(By.res("receive-copy-address")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        device.pressBack()
        assertHomeVisible()
    }

    private fun assertSendFormReachable() {
        assertTrue("Send action not reachable", clickButton("", "Send", BUSINESS_SCREEN_TIMEOUT_MS))
        assertTrue(
            "Send recipient field not visible",
            device.wait(Until.hasObject(By.res("send-recipient")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "Send amount field not visible",
            device.wait(Until.hasObject(By.res("send-amount")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "Send submit button not visible",
            device.wait(Until.hasObject(By.res("send-submit")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        device.pressBack()
        assertHomeVisible()
    }

    private fun assertDaoDepositFormReachable() {
        assertTrue("DAO tab not reachable", clickByRes("tab-dao", BUSINESS_SCREEN_TIMEOUT_MS))
        assertTrue("DAO deposit button not reachable", clickButton("dao-deposit-open", "Deposit", BUSINESS_SCREEN_TIMEOUT_MS))
        assertTrue(
            "DAO deposit amount field not visible",
            device.wait(Until.hasObject(By.res("dao-deposit-amount")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "DAO deposit submit button not visible",
            device.wait(Until.hasObject(By.res("dao-deposit-submit")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
        device.pressBack()
    }

    private fun assertSettingsNetworkSwitchDialogReachable() {
        assertTrue("Settings tab not reachable", clickByRes("tab-settings", BUSINESS_SCREEN_TIMEOUT_MS))
        assertTrue(
            "Current Network row not reachable",
            clickButton("settings-current-network", "Current Network", BUSINESS_SCREEN_TIMEOUT_MS)
        )
        assertTrue(
            "Network switch confirmation dialog not visible",
            device.wait(Until.hasObject(By.res("settings-network-confirm")), BUSINESS_SCREEN_TIMEOUT_MS)
        )
    }

    private fun clickButton(res: String, text: String, timeoutMs: Long = 8_000L): Boolean {
        if (res.isNotBlank() && clickByRes(res, timeoutMs, attempts = 3)) return true
        if (text.isBlank()) return false
        repeat(4) {
            if (!device.wait(Until.hasObject(By.text(text).pkg(BUSINESS_PKG)), 2_000L)) {
                val scrollable = device.findObject(By.scrollable(true))
                if (scrollable != null) {
                    try {
                        scrollable.scrollUntil(Direction.DOWN, Until.findObject(By.text(text).pkg(BUSINESS_PKG)))
                    } catch (_: Throwable) { /* best-effort */ }
                }
            }
            try {
                val node = device.findObject(
                    By.clickable(true).pkg(BUSINESS_PKG).hasDescendant(By.text(text))
                ) ?: device.findObject(By.text(text).pkg(BUSINESS_PKG).clickable(true))
                    ?: device.findObject(By.text(text).pkg(BUSINESS_PKG))
                    ?: return@repeat
                node.click()
                return true
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle(250L)
            }
        }
        return false
    }

    private fun clickByRes(res: String, timeoutMs: Long = 8_000L, attempts: Int = 6): Boolean {
        if (!device.wait(Until.hasObject(By.res(res)), timeoutMs)) {
            // Try scrolling: maybe the node is below the fold and Compose
            // has not emitted accessibility for it yet.
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                try {
                    scrollable.scrollUntil(Direction.DOWN, Until.findObject(By.res(res)))
                } catch (_: Throwable) {
                    /* best-effort */
                }
            }
            if (!device.wait(Until.hasObject(By.res(res)), 3_000L)) return false
        }
        repeat(attempts) {
            try {
                val node = device.findObject(By.res(res)) ?: return@repeat
                node.click()
                return true
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle(300L)
            }
        }
        return false
    }

    /** PIN digit "1" is a special case; use the same fallback strategy as clickButton. */
    private fun clickDigit1(timeoutMs: Long = 8_000L): Boolean =
        clickButton("pin-keypad-1", "1", timeoutMs)

    private fun tapDigit1UntilTitleChanges(
        from: String,
        maxTaps: Int = 12,
        perTapDelayMs: Long = 250L,
        postTapsTimeoutMs: Long = 90_000L,
    ): Boolean {
        repeat(maxTaps) {
            if (!device.hasObject(By.text(from).pkg(BUSINESS_PKG))) return true
            clickDigit1(timeoutMs = 2_000L)
            device.waitForIdle(perTapDelayMs)
        }

        val deadline = System.currentTimeMillis() + postTapsTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!device.hasObject(By.text(from).pkg(BUSINESS_PKG))) return true
            Thread.sleep(500L)
        }
        return false
    }

    private fun launchApp() {
        device.pressHome()
        val launcherPkg = device.launcherPackageName
        assertNotNull("UiDevice.launcherPackageName is null - emulator image broken?", launcherPkg)
        device.wait(Until.hasObject(By.pkg(launcherPkg).depth(0)), BUSINESS_LAUNCH_TIMEOUT_MS)

        // Do not force-stop the target package here; instrumentation runs in the app process.
        val intent = ctx.packageManager.getLaunchIntentForPackage(BUSINESS_PKG)!!.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ctx.startActivity(intent)
        assertTrue(
            "App package $BUSINESS_PKG did not appear within $BUSINESS_LAUNCH_TIMEOUT_MS ms",
            device.wait(Until.hasObject(By.pkg(BUSINESS_PKG).depth(0)), BUSINESS_LAUNCH_TIMEOUT_MS)
        )
    }
}
