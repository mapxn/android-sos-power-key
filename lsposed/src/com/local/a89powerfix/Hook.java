package com.local.a89powerfix;

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
    }

    // Named classes instead of anonymous ones: d8 34 crashes on anonymous subclasses here.
    private static final class PowerKeyScope extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_POWER) {
                IN_POWER_KEY.set(Boolean.TRUE);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            IN_POWER_KEY.remove();
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
