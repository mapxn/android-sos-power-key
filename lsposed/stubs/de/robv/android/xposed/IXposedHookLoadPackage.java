package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

// Compile-time stub of the Xposed API; the real classes are provided by LSPosed at runtime.
public interface IXposedHookLoadPackage extends IXposedMod {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
