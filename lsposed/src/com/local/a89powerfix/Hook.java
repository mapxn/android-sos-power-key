package com.local.a89powerfix;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * BIHEE A89 firmware only runs the standard power-key gestures (short press = sleep,
 * long press = global actions) when PhoneWindowManager.getRunningActivityName() reports
 * the stock launcher and the keyguard is not showing; otherwise the power key goes home
 * or dismisses the keyguard. Remove both conditions so the power key behaves like stock
 * Android everywhere.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PWM = "com.android.server.policy.PhoneWindowManager";
    private static final String STOCK_LAUNCHER = "com.sprd.simple.launcher.Launcher";

    // Set while interceptKeyBeforeQueueing handles a power key event on this thread.
    private static final ThreadLocal<Boolean> IN_POWER_KEY = new ThreadLocal<>();

    // Down time (uptime ms) of the latest power key press, and how close a volume key must be to it.
    private static volatile long lastPowerDownTime = Long.MIN_VALUE / 2;
    private static final long GHOST_WINDOW_MS = 50;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(PWM, lpparam.classLoader, "getRunningActivityName",
                    XC_MethodReplacement.returnConstant(STOCK_LAUNCHER));
            XposedBridge.log("A89PowerFix: hooked PhoneWindowManager.getRunningActivityName");
        } catch (Throwable t) {
            XposedBridge.log("A89PowerFix: getRunningActivityName hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(PWM, lpparam.classLoader, "interceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, new PowerKeyScope());
            XposedHelpers.findAndHookMethod(PWM, lpparam.classLoader, "isKeyguardShowing",
                    new HideKeyguardForPowerKey());
            XposedBridge.log("A89PowerFix: hooked keyguard check for power key");
        } catch (Throwable t) {
            XposedBridge.log("A89PowerFix: keyguard hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(PWM, lpparam.classLoader, "interceptKeyBeforeDispatching",
                    IBinder.class, KeyEvent.class, int.class, new DropGhostVolumeKeys());
            XposedBridge.log("A89PowerFix: hooked ghost volume key filter");
        } catch (Throwable t) {
            XposedBridge.log("A89PowerFix: ghost volume key hook failed: " + t);
        }
    }

    // Named classes instead of anonymous ones: d8 34 crashes on anonymous subclasses here.
    private static final class PowerKeyScope extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            KeyEvent event = (KeyEvent) param.args[0];
            if (event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
                IN_POWER_KEY.set(Boolean.TRUE);
                if (event.getAction() == KeyEvent.ACTION_DOWN) lastPowerDownTime = event.getDownTime();
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            IN_POWER_KEY.remove();
        }
    }

    /**
     * When the phone is woken from deep sleep the A89 keypad reports a phantom VOLUME_UP +
     * VOLUME_DOWN together with the SOS key, which pops up the volume bar. They carry the
     * same down time as the power key (a real volume press never does), so drop them.
     * The volume keys come first in that report, so a fresh volume key is held back until
     * the power key of the same report has had time to be queued.
     */
    private static final class DropGhostVolumeKeys extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            KeyEvent event = (KeyEvent) param.args[1];
            int code = event.getKeyCode();
            if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return;
            long downTime = event.getDownTime();
            if (Math.abs(downTime - lastPowerDownTime) <= GHOST_WINDOW_MS) {
                param.setResult(Long.valueOf(-1L));
                return;
            }
            long age = SystemClock.uptimeMillis() - downTime;
            // A positive result asks the dispatcher to retry this event after that many ms.
            if (age < GHOST_WINDOW_MS) param.setResult(Long.valueOf(GHOST_WINDOW_MS - age));
        }
    }

    // Only the vendor power-key branches call isKeyguardShowing() inside
    // interceptKeyBeforeQueueing; report no keyguard there so the power key sleeps
    // instead of dismissing the lock screen.
    private static final class HideKeyguardForPowerKey extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (IN_POWER_KEY.get() != null) param.setResult(Boolean.FALSE);
        }
    }
}
