package com.liskovsoft.smarttubesync.controller;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ScreenConnection.StatusListener {

    private EditText mIp1, mIp2, mIp3, mIp4, mIp5, mVideoIdInput;
    private TextView mStatusText;
    private ScreenGroup mScreenGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIp1 = findViewById(R.id.ipScreen1);
        mIp2 = findViewById(R.id.ipScreen2);
        mIp3 = findViewById(R.id.ipScreen3);
        mIp4 = findViewById(R.id.ipScreen4);
        mIp5 = findViewById(R.id.ipScreen5);
        mVideoIdInput = findViewById(R.id.videoIdInput);
        mStatusText = findViewById(R.id.statusText);

        mScreenGroup = new ScreenGroup(this);

        Button btnOpen = findViewById(R.id.btnOpen);
        Button btnPlay = findViewById(R.id.btnPlay);
        Button btnPause = findViewById(R.id.btnPause);
        Button btnPrevious = findViewById(R.id.btnPrevious);
        Button btnNext = findViewById(R.id.btnNext);
        Button btnSync = findViewById(R.id.btnSync);

        btnOpen.setOnClickListener(v -> {
            connectToConfiguredScreens();
            String videoId = mVideoIdInput.getText().toString().trim();
            if (!videoId.isEmpty()) {
                mScreenGroup.openVideo(videoId);
                appendStatus("Comando 'abrir video' enviado: " + videoId);
            } else {
                appendStatus("Escribe un ID de video primero");
            }
        });

        btnPlay.setOnClickListener(v -> {
            mScreenGroup.play();
            appendStatus("Comando 'play' enviado");
        });

        btnPause.setOnClickListener(v -> {
            mScreenGroup.pause();
            appendStatus("Comando 'pausa' enviado");
        });

        btnPrevious.setOnClickListener(v -> {
            mScreenGroup.previous();
            appendStatus("Comando 'anterior' enviado");
        });

        btnNext.setOnClickListener(v -> {
            mScreenGroup.next();
            appendStatus("Comando 'siguiente' enviado");
        });

        btnSync.setOnClickListener(v -> {
            // MVP: sincroniza usando 0 como referencia. La version completa
            // deberia primero preguntar la posicion real a una pantalla "maestra".
            mScreenGroup.forceSync(0);
            appendStatus("Comando 'sincronizar' enviado");
        });
    }

    private void connectToConfiguredScreens() {
        List<String> ips = Arrays.asList(
                mIp1.getText().toString(),
                mIp2.getText().toString(),
                mIp3.getText().toString(),
                mIp4.getText().toString(),
                mIp5.getText().toString()
        );
        mScreenGroup.connectAll(ips);
        appendStatus("Conectando a las pantallas configuradas...");
    }

    @Override
    public void onStatus(String screenIp, String status) {
        runOnUiThread(() -> appendStatus(screenIp + ": " + status));
    }

    private void appendStatus(String line) {
        mStatusText.setText("Estado:\n" + line + "\n" + mStatusText.getText());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenGroup != null) {
            mScreenGroup.disconnectAll();
        }
    }
}
