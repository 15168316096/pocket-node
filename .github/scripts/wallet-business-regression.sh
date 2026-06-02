#!/usr/bin/env bash
# Wallet business regression harness: install PR APK + androidTest APK, then
# run each end-to-end scenario from a clean app data directory.

set -euo pipefail

PKG=com.rjnr.pocketnode
TEST_PKG=com.rjnr.pocketnode.test
TEST_CLASS=com.rjnr.pocketnode.WalletBusinessRegressionTest
TEST_CACHE=/sdcard/Android/data/${PKG}/cache

capture_state() {
  echo "::group::Capturing wallet regression state"
  adb shell screencap -p /sdcard/wallet-business-post.png 2>/dev/null || true
  adb pull /sdcard/wallet-business-post.png wallet-business-post.png 2>/dev/null || true
  adb shell uiautomator dump /sdcard/wallet-business-dump.xml 2>/dev/null || true
  adb pull /sdcard/wallet-business-dump.xml wallet-business-dump.xml 2>/dev/null || true
  adb pull "$TEST_CACHE/fail-createWalletReachesMnemonicVerifyGate.png" fail-createWalletReachesMnemonicVerifyGate.png 2>/dev/null || true
  adb pull "$TEST_CACHE/fail-createWalletReachesMnemonicVerifyGate.xml" fail-createWalletReachesMnemonicVerifyGate.xml 2>/dev/null || true
  adb pull "$TEST_CACHE/fail-importedWalletCoversCoreBusinessEntrypoints.png" fail-importedWalletCoversCoreBusinessEntrypoints.png 2>/dev/null || true
  adb pull "$TEST_CACHE/fail-importedWalletCoversCoreBusinessEntrypoints.xml" fail-importedWalletCoversCoreBusinessEntrypoints.xml 2>/dev/null || true
  adb logcat -d > wallet-business-logcat.txt 2>/dev/null || true
  echo "::endgroup::"
}
trap capture_state EXIT

run_case() {
  local method="$1"
  local log="$2"

  echo "::group::Run ${method}"
  adb shell am force-stop "$PKG" 2>/dev/null || true
  adb shell pm clear "$PKG"
  adb shell pm clear "$TEST_PKG" 2>/dev/null || true
  adb logcat -c
  adb install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb install -r -t android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
  adb shell input keyevent 82 || true

  adb shell am instrument -w \
    -e class "${TEST_CLASS}#${method}" \
    "${TEST_PKG}/androidx.test.runner.AndroidJUnitRunner" | tee "$log"

  if ! grep -q 'OK (1 test)' "$log"; then
    echo "${method} failed"
    exit 1
  fi
  echo "::endgroup::"
}

adb wait-for-device
adb shell input keyevent 82 || true

run_case createWalletReachesMnemonicVerifyGate wallet-business-create.log
run_case importedWalletCoversCoreBusinessEntrypoints wallet-business-import.log

echo "::group::Logcat scan"
adb logcat -d > wallet-business-logcat.txt
if grep -qE 'FATAL EXCEPTION|AndroidRuntime.*FATAL' wallet-business-logcat.txt; then
  echo "FATAL detected in logcat:"
  grep -E 'FATAL EXCEPTION|AndroidRuntime.*FATAL' wallet-business-logcat.txt
  exit 1
fi
echo "::endgroup::"
