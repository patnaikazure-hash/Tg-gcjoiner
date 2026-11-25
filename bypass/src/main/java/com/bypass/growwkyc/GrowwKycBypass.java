package com.bypass.growwkyc;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GrowwKycBypass implements IXposedHookLoadPackage {

    private static final String TAG = "GrowwKycBypass";
    private static final String TARGET_PKG = "in.groww.stocks";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PKG)) return;

        XposedBridge.log(TAG + ": Groww loaded, applying KYC bypass...");

        // ── Hook 1: KycData.isKycVerified() ──────────────────────────────────
        // This is the core data class that holds KYC state.
        // We force it to always return true.
        hookKycVerifiedField(lpparam.classLoader);

        // ── Hook 2: AppLaunchViewModel - block KYC redirect on launch ─────────
        hookLaunchViewModel(lpparam.classLoader);

        // ── Hook 3: KycStatusResponse - force verified status ─────────────────
        hookKycStatusResponse(lpparam.classLoader);

        // ── Hook 4: Broad method hook - any method named isKycVerified ────────
        hookAllKycVerifiedMethods(lpparam.classLoader);

        XposedBridge.log(TAG + ": All hooks applied.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hook 1: Force KycData.isKycVerified field/getter to true
    // ─────────────────────────────────────────────────────────────────────────
    private void hookKycVerifiedField(ClassLoader cl) {
        String[] kycDataCandidates = {
            "com.nextbillion.groww.onboarding.hoist.models.KycData",
            "com.nextbillion.groww.genesys.onboardingV2.data.KycData",
            "com.nextbillion.groww.network.hoist.models.KycData",
            "com.nextbillion.groww.core.models.KycData"
        };

        for (String className : kycDataCandidates) {
            try {
                Class<?> kycDataClass = XposedHelpers.findClass(className, cl);
                // Hook getter if it exists
                try {
                    XposedHelpers.findAndHookMethod(kycDataClass, "isKycVerified",
                        XC_MethodReplacement.returnConstant(true));
                    XposedBridge.log(TAG + ": Hooked " + className + ".isKycVerified()");
                } catch (Throwable t) { /* no getter, field direct */ }

                // Also force the field on every object construction
                try {
                    XposedHelpers.findAndHookConstructor(kycDataClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    Field f = findField(param.thisObject.getClass(), "isKycVerified");
                                    if (f != null) { f.setAccessible(true); f.set(param.thisObject, true); }
                                } catch (Throwable ignored) {}
                            }
                        });
                } catch (Throwable t) { /* constructor hook optional */ }

            } catch (Throwable t) {
                // class not in this dex, skip
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hook 2: AppLaunchViewModel - intercept KYC redirect decision
    // ─────────────────────────────────────────────────────────────────────────
    private void hookLaunchViewModel(ClassLoader cl) {
        String vmClass = "com.nextbillion.groww.genesys.launch.AppLaunchViewModel";
        String[] kycMethods = {
            "checkKycStatus", "isKycComplete", "shouldShowKyc",
            "navigateToKyc", "showKycScreen", "resolveKycNavigation",
            "handleKycState", "proceedWithKyc"
        };

        try {
            Class<?> vm = XposedHelpers.findClass(vmClass, cl);
            for (String method : kycMethods) {
                try {
                    XposedHelpers.findAndHookMethod(vm, method,
                        XC_MethodReplacement.returnConstant(null));
                    XposedBridge.log(TAG + ": Hooked AppLaunchViewModel." + method);
                } catch (Throwable t) { /* method may not exist */ }
            }

            // Hook checkRootedDevice — Groww blocks rooted phones, bypass this too
            try {
                XposedHelpers.findAndHookMethod(vm, "checkRootedDevice",
                    XC_MethodReplacement.returnConstant(null));
                XposedBridge.log(TAG + ": Hooked checkRootedDevice (rooted bypass)");
            } catch (Throwable t) {}

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": AppLaunchViewModel not found: " + t.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hook 3: KycStatusResponse - force verified in API response object
    // ─────────────────────────────────────────────────────────────────────────
    private void hookKycStatusResponse(ClassLoader cl) {
        String[] candidates = {
            "com.nextbillion.groww.onboarding.hoist.models.KycStatusResponse",
            "com.nextbillion.groww.genesys.onboardingV2.data.KycStatusResponse"
        };

        for (String className : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(className, cl);
                XposedHelpers.findAndHookConstructor(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        forceKycVerifiedInObject(param.thisObject);
                    }
                });
                XposedBridge.log(TAG + ": Hooked KycStatusResponse constructor: " + className);
            } catch (Throwable t) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hook 4: Broad sweep - hook ALL isKycVerified methods in Groww classes
    // ─────────────────────────────────────────────────────────────────────────
    private void hookAllKycVerifiedMethods(ClassLoader cl) {
        // Target package prefixes
        String[] packages = {
            "com.nextbillion.groww.genesys.launch",
            "com.nextbillion.groww.genesys.onboardingV2",
            "com.nextbillion.groww.onboarding",
            "com.nextbillion.groww.genesys.invest.mutualfunds.tabs.dashboard"
        };

        for (String pkg : packages) {
            try {
                // Try known class patterns
                for (char c = 'a'; c <= 'z'; c++) {
                    try {
                        Class<?> cls = XposedHelpers.findClass(pkg + "." + c, cl);
                        hookKycMethodsOnClass(cls);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hookKycMethodsOnClass(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            String name = m.getName();
            if (name.toLowerCase().contains("kyc") || name.toLowerCase().contains("verified")) {
                Class<?> ret = m.getReturnType();
                if (ret == boolean.class || ret == Boolean.class) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, name,
                            XC_MethodReplacement.returnConstant(true));
                        XposedBridge.log(TAG + ": Broad hook: " + cls.getName() + "." + name);
                    } catch (Throwable ignored) {}
                } else if (ret == void.class) {
                    // Methods that navigate to KYC screen - suppress them
                    if (name.toLowerCase().contains("navigate") ||
                        name.toLowerCase().contains("show") ||
                        name.toLowerCase().contains("launch")) {
                        try {
                            XposedHelpers.findAndHookMethod(cls, name,
                                XC_MethodReplacement.returnConstant(null));
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void forceKycVerifiedInObject(Object obj) {
        if (obj == null) return;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                String name = f.getName().toLowerCase();
                if (name.contains("kyc") && name.contains("verif")) {
                    if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                        try {
                            f.setAccessible(true);
                            f.set(obj, true);
                        } catch (Throwable ignored) {}
                    }
                }
                // Also handle kycStatus string fields
                if (name.equals("kycstatus") && f.getType() == String.class) {
                    try {
                        f.setAccessible(true);
                        f.set(obj, "VERIFIED");
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getName().equals(name)) return f;
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
