package com.rayo.control;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Native UI micro-interactions and specialist components (100% Android SDK, MIT-pattern aligned):
 * - Tactile press feedback (scale 0.97, ease-out < 150ms)
 * - Calm number transitions (tabular-nums)
 * - Floating Sonner-style toast banner with optional Undo
 * - Instant-filter Command Palette modal
 */
public final class UiDesignHelpers {

    private static final DecelerateInterpolator EASE_OUT = new DecelerateInterpolator(1.6f);

    private UiDesignHelpers() {}

    public static void attachPressFeedback(View... views) {
        for (final View v : views) {
            if (v == null) continue;
            v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View target, MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        target.animate()
                                .scaleX(0.97f)
                                .scaleY(0.97f)
                                .setDuration(90L)
                                .setInterpolator(EASE_OUT)
                                .start();
                    } else if (action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        target.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(140L)
                                .setInterpolator(EASE_OUT)
                                .start();
                    }
                    return false;
                }
            });
        }
    }

    public static void animateIntText(final TextView tv, int from, int to, final String format) {
        if (tv == null) return;
        if (from == to || from < 0) {
            tv.setText(String.format(Locale.getDefault(), format, to));
            return;
        }
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(220L);
        anim.setInterpolator(EASE_OUT);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int val = (Integer) animation.getAnimatedValue();
                tv.setText(String.format(Locale.getDefault(), format, val));
            }
        });
        anim.start();
    }

    public static final class FloatingToastController {
        private final View container;
        private final TextView messageView;
        private final Button undoButton;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable hideRunnable = new Runnable() {
            @Override
            public void run() {
                hide();
            }
        };

        public FloatingToastController(View container, TextView messageView, Button undoButton) {
            this.container = container;
            this.messageView = messageView;
            this.undoButton = undoButton;
            attachPressFeedback(undoButton);
        }

        public void show(String message, final Runnable undoAction) {
            if (container == null || messageView == null) return;
            handler.removeCallbacks(hideRunnable);
            messageView.setText(message);

            if (undoButton != null) {
                if (undoAction != null) {
                    undoButton.setVisibility(View.VISIBLE);
                    undoButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            handler.removeCallbacks(hideRunnable);
                            hide();
                            undoAction.run();
                        }
                    });
                } else {
                    undoButton.setVisibility(View.GONE);
                    undoButton.setOnClickListener(null);
                }
            }

            if (container.getVisibility() != View.VISIBLE) {
                container.setAlpha(0f);
                container.setTranslationY(24f);
                container.setVisibility(View.VISIBLE);
                container.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200L)
                        .setInterpolator(EASE_OUT)
                        .start();
            }
            handler.postDelayed(hideRunnable, 2800L);
        }

        public void hide() {
            if (container == null || container.getVisibility() != View.VISIBLE) return;
            container.animate()
                    .alpha(0f)
                    .translationY(20f)
                    .setDuration(180L)
                    .setInterpolator(EASE_OUT)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            container.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    public static final class CommandItem {
        public final String title;
        public final String subtitle;
        public final Runnable action;

        public CommandItem(String title, String subtitle, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }
    }

    public static void showCommandPalette(final Activity activity, final List<CommandItem> items) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_command_palette, null);
        dialog.setContentView(content);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        EditText search = content.findViewById(R.id.etCommandSearch);
        final LinearLayout listContainer = content.findViewById(R.id.layoutCommandList);
        final TextView emptyView = content.findViewById(R.id.tvCommandEmpty);

        final int primaryColor = activity.getColor(R.color.text_primary);
        final int secondaryColor = activity.getColor(R.color.text_secondary);
        final float density = activity.getResources().getDisplayMetrics().density;

        final Runnable[] rebuild = new Runnable[1];
        final String[] currentQuery = {""};

        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                String q = currentQuery[0].toLowerCase(Locale.getDefault()).trim();
                int count = 0;

                for (final CommandItem item : items) {
                    if (!q.isEmpty()
                            && !item.title.toLowerCase(Locale.getDefault()).contains(q)
                            && (item.subtitle == null || !item.subtitle.toLowerCase(Locale.getDefault()).contains(q))) {
                        continue;
                    }
                    count++;
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setBackgroundResource(R.drawable.bg_btn_secondary);
                    int padH = (int) (12 * density);
                    int padV = (int) (10 * density);
                    row.setPadding(padH, padV, padH, padV);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = (int) (6 * density);
                    row.setLayoutParams(lp);

                    TextView title = new TextView(activity);
                    title.setText(item.title);
                    title.setTextColor(primaryColor);
                    title.setTextSize(13f);
                    title.setMaxLines(1);
                    row.addView(title);

                    if (item.subtitle != null && !item.subtitle.isEmpty()) {
                        TextView sub = new TextView(activity);
                        sub.setText(item.subtitle);
                        sub.setTextColor(secondaryColor);
                        sub.setTextSize(11f);
                        sub.setMaxLines(1);
                        row.addView(sub);
                    }

                    // Instant 0ms action response
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            item.action.run();
                        }
                    });
                    listContainer.addView(row);
                }

                emptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery[0] = s != null ? s.toString() : "";
                rebuild[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rebuild[0].run();
        dialog.show();
    }
}
