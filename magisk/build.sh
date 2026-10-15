#!/bin/sh
# Build build/sos_as_power.zip (Magisk module) from the connected, rooted device's own
# keylayout files, so no vendor / AOSP key layout is redistributed by this repository.
#
#   SOS key      sprd-keypad scancode 61  (KEY_F3)    -> POWER
#   hang-up key  gpio-keys    scancode 116 (KEY_POWER) -> ENDCALL
#
# Needs: adb (use ANDROID_SERIAL to pick a device) and root (su) on the phone.
set -e
cd "$(dirname "$0")"

pull() { adb shell "su -c 'cat $1'" | tr -d '\r'; }

rm -rf build && mkdir -p build/module/system/usr/keylayout build/module/system/vendor/usr/keylayout
cp module.prop build/module/module.prop

# New sprd-keypad.kl = copy of Generic.kl (what that device uses today) with the SOS key remapped.
pull /system/usr/keylayout/Generic.kl > build/Generic.kl
grep -Eq '^key 61 +F3$' build/Generic.kl || { echo "Generic.kl has no 'key 61 F3' line" >&2; exit 1; }
sed -E 's/^key 61 +F3$/key 61    POWER           WAKE/' build/Generic.kl \
    > build/module/system/usr/keylayout/sprd-keypad.kl

# gpio-keys.kl is the vendor layout of the hang-up key: POWER -> ENDCALL.
pull /vendor/usr/keylayout/gpio-keys.kl > build/gpio-keys.kl
if grep -Eq '^key 116 +POWER +WAKE$' build/gpio-keys.kl; then
    sed -E 's/^key 116 +POWER +WAKE$/key 116     ENDCALL         WAKE/' build/gpio-keys.kl \
        > build/module/system/vendor/usr/keylayout/gpio-keys.kl
elif grep -Eq '^key 116 +ENDCALL +WAKE$' build/gpio-keys.kl; then
    # This module is already installed, so the phone shows the patched file.
    cp build/gpio-keys.kl build/module/system/vendor/usr/keylayout/gpio-keys.kl
else
    echo "gpio-keys.kl has no 'key 116 POWER WAKE' line" >&2; exit 1
fi

(cd build/module && zip -qr ../sos_as_power.zip .)
echo "built: $(pwd)/build/sos_as_power.zip"
