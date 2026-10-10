package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.sync.PairingCodeActivity;

public class SplashActivity extends MotherActivity implements SplashView {
    private static final String TAG = SplashActivity.class.getSimpleName();
    private Intent mNewIntent;
    private SplashPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNewIntent = getIntent();

        mPresenter = SplashPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        showPairingCodeIfLauncher(mNewIntent);

        //finish();
    }

    /**
     * YG Sync: al abrir la app desde el launcher de la TV se muestra unos
     * segundos el codigo de emparejamiento (encima de la pantalla de inicio).
     * No se muestra cuando la app se abre desde un enlace de YouTube.
     */
    private void showPairingCodeIfLauncher(Intent intent) {
        try {
            String action = intent != null ? intent.getAction() : null;

            if (action != null && !Intent.ACTION_MAIN.equals(action)) {
                return;
            }

            final android.content.Context context = getApplicationContext();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent pairing = new Intent(context, PairingCodeActivity.class);
                    pairing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(pairing);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }, 1500);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;

        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    @Override
    public void finishView() {
        try {
            finish();
        } catch (NullPointerException e) {
            // NullPointerException: Attempt to invoke virtual method 'void com.android.server.wm.DisplayContent.moveStack(com.android.server.wm.TaskStack, boolean)'
            e.printStackTrace();
        }
    }
}
