package dev.claude.oneui7pill;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hooks com.android.systemui.battery.BatteryMeterView (names confirmed from the SM-A136B
 * firmware A136BXXSDDYK2 dex dump) and swaps its icon drawable for a One UI 7 style pill.
 * Everything is wrapped in try/catch: on any problem SystemUI just keeps the stock icon.
 */
public class PillHook implements IXposedHookLoadPackage {

    private static final String TAG = "[OneUI7Pill] ";
    private static final String VIEW_CLASS = "com.android.systemui.battery.BatteryMeterView";
    private static final String HOLDER_KEY = "oneui7pill_holder";

    // Methods after which the stock code may have re-set its own drawable / sizes / visibility.
    private static final String[] APPLY_AFTER = {
            "updateDrawable", "updatePercentText", "updateShowPercent", "setPercentShowMode",
            "scaleBatteryMeterViews", "onAttachedToWindow", "onDarkChanged", "updateColors",
            "setColorsFromContext", "onBatteryLevelChanged", "onPowerSaveChanged",
            "onDensityOrFontScaleChanged", "onConfigurationChanged", "setForceShowPercent"
    };
    private static final String[] RELEASE_AFTER = { "onDetachedFromWindow" };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        Class<?> cls = XposedHelpers.findClassIfExists(VIEW_CLASS, lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "BatteryMeterView not found - nothing hooked (stock icon stays).");
            return;
        }

        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof View) apply((View) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "apply failed: " + t);
                }
            }
        };
        XC_MethodHook releaseHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object h = XposedHelpers.getAdditionalInstanceField(param.thisObject, HOLDER_KEY);
                    if (h instanceof Holder) ((Holder) h).unregister();
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "release failed: " + t);
                }
            }
        };

        int hooked = 0;
        for (String name : APPLY_AFTER) {
            hooked += XposedBridge.hookAllMethods(cls, name, applyHook).size();
        }
        for (String name : RELEASE_AFTER) {
            hooked += XposedBridge.hookAllMethods(cls, name, releaseHook).size();
        }
        XposedBridge.log(TAG + "hooked " + hooked + " methods on BatteryMeterView");
    }

    // ------------------------------------------------------------------------------------

    private static void apply(View root) {
        ImageView icon = findIcon(root);
        if (icon == null) return;
        TextView percent = findPercent(root);

        Holder h = (Holder) XposedHelpers.getAdditionalInstanceField(root, HOLDER_KEY);
        if (h == null) {
            h = new Holder(root.getContext());
            XposedHelpers.setAdditionalInstanceField(root, HOLDER_KEY, h);
        }
        h.percentView = percent;
        h.registerIfNeeded();

        PillDrawable d = h.drawable;
        if (icon.getDrawable() != d) icon.setImageDrawable(d);
        icon.setScaleType(ImageView.ScaleType.FIT_XY);

        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp != null && (lp.width != d.widthPx() || lp.height != d.heightPx())) {
            lp.width = d.widthPx();
            lp.height = d.heightPx();
            icon.setLayoutParams(lp);
        }

        // the number now lives inside the pill -> hide the separate % text
        if (percent != null && percent.getVisibility() != View.GONE) {
            percent.setVisibility(View.GONE);
        }
        d.invalidateSelf();
    }

    private static ImageView findIcon(View root) {
        try {
            Object o = XposedHelpers.getObjectField(root, "mBatteryIconView");
            if (o instanceof ImageView) return (ImageView) o;
        } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i) instanceof ImageView) return (ImageView) g.getChildAt(i);
            }
        }
        return null;
    }

    private static TextView findPercent(View root) {
        try {
            Object o = XposedHelpers.getObjectField(root, "mBatteryPercentView");
            if (o instanceof TextView) return (TextView) o;
        } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i) instanceof TextView) return (TextView) g.getChildAt(i);
            }
        }
        return null;
    }

    /** Per-BatteryMeterView state: the drawable plus a battery receiver while attached. */
    private static final class Holder extends BroadcastReceiver implements PillDrawable.ColorSource {
        final Context context;
        final PillDrawable drawable;
        TextView percentView;
        boolean registered;

        Holder(Context ctx) {
            this.context = ctx;
            this.drawable = new PillDrawable(ctx.getResources().getDisplayMetrics().density, this);
        }

        @Override
        public int foreground() {
            // SystemUI keeps this text colour in sync with light/dark status bar areas.
            TextView tv = percentView;
            return tv != null ? tv.getCurrentTextColor() : 0xFFFFFFFF;
        }

        void registerIfNeeded() {
            if (registered) return;
            try {
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_BATTERY_CHANGED);
                f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                Intent sticky;
                if (Build.VERSION.SDK_INT >= 33) {
                    sticky = context.registerReceiver(this, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    sticky = context.registerReceiver(this, f);
                }
                registered = true;
                if (sticky != null) onReceive(context, sticky);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "register failed: " + t);
            }
        }

        void unregister() {
            if (!registered) return;
            try { context.unregisterReceiver(this); } catch (Throwable ignored) { }
            registered = false;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            try {
                int level = 100;
                boolean charging = false;
                Intent b = i;
                if (!Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                    b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                }
                if (b != null) {
                    int raw = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (raw >= 0 && scale > 0) level = Math.round(raw * 100f / scale);
                    int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                    int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    charging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL);
                }
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                boolean saver = pm != null && pm.isPowerSaveMode();
                drawable.setState(level, charging, saver);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "onReceive failed: " + t);
            }
        }
    }
}
