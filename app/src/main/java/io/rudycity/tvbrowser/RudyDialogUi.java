package io.rudycity.tvbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * Shared API-19-safe Rudy Design System v2 palette and native dialog styling.
 * Ensures Android TV, Legacy API 19, and Extension dialogs share the exact
 * slate hierarchy, border weights, and WCAG AA contrast as Compose surfaces.
 */
public final class RudyDialogUi {
    public static final int BG = 0xFF0C131E;
    public static final int TOOLBAR = 0xFF131B28;
    public static final int SURFACE_ELEVATED = 0xFF1B2536;
    public static final int OMNIBOX = 0xFF222E42;
    public static final int OMNIBOX_FOCUSED = 0xFF293850;
    public static final int HIGHLIGHT = 0xFF244C82;
    public static final int ACCENT = 0xFF9DC6FC;
    public static final int ON_ACCENT = 0xFF071D38;
    public static final int BORDER = 0xFF6E89AA;
    public static final int BORDER_SUBTLE = 0xFF394B63;
    public static final int TEXT = 0xFFF1F5FB;
    public static final int MUTED = 0xFFB5C3D4;
    public static final int POSITIVE = 0xFF7AD894;
    public static final int DANGER = 0xFFF48B82;

    private RudyDialogUi() {}

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressWarnings("deprecation")
    public static AlertDialog.Builder builder(Context context) {
        int theme = Build.VERSION.SDK_INT >= 21
                ? android.R.style.Theme_Material_Dialog_Alert
                : AlertDialog.THEME_HOLO_DARK;
        return new AlertDialog.Builder(context, theme);
    }

    public static AlertDialog showStyled(AlertDialog dialog) {
        dialog.show();
        styleShownDialog(dialog);
        return dialog;
    }

    public static void styleShownDialog(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(SURFACE_ELEVATED);
            bg.setCornerRadius(dp(dialog.getContext(), 18));
            bg.setStroke(dp(dialog.getContext(), 1), BORDER_SUBTLE);
            window.setBackgroundDrawable(bg);
        }
        styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), ACCENT);
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), MUTED);
        styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), ACCENT);
    }

    private static void styleButton(Button button, int textColor) {
        if (button == null) return;
        button.setTextColor(textColor);
        button.setAllCaps(false);
    }

    public static GradientDrawable toolbarButtonDrawable(
            Context context, boolean tvDevice, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(context, 10));
        drawable.setColor(focused ? HIGHLIGHT : (tvDevice ? OMNIBOX : TOOLBAR));
        if (tvDevice || focused) {
            drawable.setStroke(
                    dp(context, focused ? (tvDevice ? 2.5f : 2f) : 1f),
                    focused ? ACCENT : BORDER_SUBTLE);
        }
        return drawable;
    }

    public static GradientDrawable omniboxDrawable(
            Context context, boolean tvDevice, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(context, tvDevice ? 14 : 28));
        drawable.setColor(focused ? OMNIBOX_FOCUSED : OMNIBOX);
        drawable.setStroke(
                dp(context, focused ? (tvDevice ? 2.5f : 2f) : 1f),
                focused ? ACCENT : BORDER_SUBTLE);
        return drawable;
    }

    public static GradientDrawable cardDrawable(
            Context context, boolean active, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(context, 14));
        drawable.setColor(focused ? HIGHLIGHT : (active ? OMNIBOX_FOCUSED : OMNIBOX));
        int strokeWidth = dp(context, (focused || active) ? 2f : 1f);
        int strokeColor = focused ? ACCENT : (active ? ACCENT : BORDER_SUBTLE);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    public static TextView badge(Context context, String label, int bgColor, int fgColor) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(fgColor);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setGravity(Gravity.CENTER);
        int h = dp(context, 8);
        int v = dp(context, 2);
        view.setPadding(h, v, h, v);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(context, 6));
        bg.setColor(bgColor);
        view.setBackgroundDrawable(bg);
        return view;
    }
}
