package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ventana emergente que muestra el código de pairing de 6 dígitos
 * durante 10 segundos y se cierra sola.
 *
 * Solo se lanza una vez: la primera vez que este receptor arranca
 * sin tener todavía ningún Controller emparejado (ver
 * SyncPairingManager.hasAnyPairedController() y su uso en
 * SyncReceiverService). Una vez que el usuario empareja al menos un
 * Controller, esta pantalla no vuelve a aparecer sola.
 *
 * Se arma toda la vista por código (sin layout XML) para no agregar
 * archivos de más. El tema usado (App.Theme.Leanback.Preferences) ya
 * existe en el proyecto y es translúcido, así que esta Activity se ve
 * como una ventana flotando sobre lo que sea que estuviera en
 * pantalla, sin taparlo del todo.
 */
public class PairingCodeActivity extends Activity {

    public static final String EXTRA_CODE = "code";

    private static final long AUTO_CLOSE_MS = 10_000L;

    private final Handler mHandler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        String code =
                getIntent().getStringExtra(
                        EXTRA_CODE
                );

        if (
                code == null ||
                code.trim().isEmpty()
        ) {
            finish();
            return;
        }

        setContentView(
                buildContentView(
                        code.trim()
                )
        );

        mHandler.postDelayed(
                this::finish,
                AUTO_CLOSE_MS
        );
    }

    private FrameLayout buildContentView(
            String code
    ) {

        FrameLayout root =
                new FrameLayout(this);

        root.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        root.setBackgroundColor(
                Color.parseColor("#B3000000")
        );

        root.setOnClickListener(
                v -> finish()
        );

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER
        );

        int paddingPx =
                dpToPx(32);

        card.setPadding(
                paddingPx,
                paddingPx,
                paddingPx,
                paddingPx
        );

        GradientDrawable cardBackground =
                new GradientDrawable();

        cardBackground.setColor(
                Color.parseColor("#F21B1B1B")
        );

        cardBackground.setCornerRadius(
                dpToPx(16)
        );

        cardBackground.setStroke(
                dpToPx(2),
                Color.parseColor("#4DA6FF")
        );

        card.setBackground(
                cardBackground
        );

        TextView title =
                new TextView(this);

        title.setText(
                "Código de emparejamiento"
        );

        title.setTextColor(
                Color.parseColor("#CCFFFFFF")
        );

        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER);

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Escribilo en \"YG Sync Control\" para vincular esta TV"
        );

        subtitle.setTextColor(
                Color.parseColor("#99FFFFFF")
        );

        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin =
                dpToPx(6);

        subtitleParams.bottomMargin =
                dpToPx(20);

        TextView codeView =
                new TextView(this);

        codeView.setText(
                formatCode(code)
        );

        codeView.setTextColor(
                Color.parseColor("#4DA6FF")
        );

        codeView.setTypeface(
                Typeface.DEFAULT_BOLD
        );

        codeView.setTextSize(52f);
        codeView.setGravity(Gravity.CENTER);

        card.addView(title);
        card.addView(subtitle, subtitleParams);
        card.addView(codeView);

        FrameLayout.LayoutParams cardParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.gravity =
                Gravity.CENTER;

        root.addView(
                card,
                cardParams
        );

        return root;
    }

    /**
     * "123456" -> "123 456", más fácil de leer de lejos.
     */
    private String formatCode(String code) {

        if (code.length() != 6) {
            return code;
        }

        return code.substring(0, 3)
                + " "
                + code.substring(3);
    }

    private int dpToPx(int dp) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {

        mHandler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }
}
