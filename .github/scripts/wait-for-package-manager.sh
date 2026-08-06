#!/usr/bin/env sh
# Wait until PackageManager is ready on the connected emulator/device.
# android-emulator-runner only waits for sys.boot_completed; without this,
# APK install can fail with "Can't find service: package".
set -eu

adb wait-for-device

echo "Waiting for package manager service..."
i=1
while [ "$i" -le 90 ]; do
  if adb shell cmd package list packages >/dev/null 2>&1; then
    echo "Package manager ready after ${i} attempt(s)."
    exit 0
  fi
  i=$((i + 1))
  sleep 2
done

echo "Package manager did not become ready in time."
adb shell getprop sys.boot_completed || true
adb shell service list || true
exit 1
