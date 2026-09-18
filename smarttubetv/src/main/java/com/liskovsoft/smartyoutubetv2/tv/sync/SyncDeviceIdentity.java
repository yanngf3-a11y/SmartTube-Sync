package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Identidad persistente del receptor YG Sync.
 *
 * Genera un deviceId (UUID) una sola vez por instalación y lo guarda en
 * SharedPreferences. A diferencia de la IP, este id no cambia aunque la
 * TV cambie de red o de dirección IP, y permite que el Controller
 * identifique de forma estable cada pantalla cuando hay varias
 * conectadas al mismo tiempo.
 *
 * NOTA: este id NO es un secreto (no sirve para autenticar). Es solo
 * un identificador estable. La autenticación/pairing se añade en un
 * paso posterior (2.2 / 2.3), y ahí sí se usará Android Keystore para
 * las credenciales generadas durante el pairing.
 */
public final class SyncDeviceIdentity {

    private static final String PREFS_NAME =
            "yg_sync_identity";

    private static final String KEY_DEVICE_ID =
            "device_id";

    private static volatile String sCachedDeviceId;

    private SyncDeviceIdentity() {
    }

    /**
     * Devuelve el deviceId persistente de este receptor, generando uno
     * nuevo la primera vez que se llama.
     */
    public static synchronized String getDeviceId(
            Context context
    ) {

        if (sCachedDeviceId != null) {
            return sCachedDeviceId;
        }

        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(
                                PREFS_NAME,
                                Context.MODE_PRIVATE
                        );

        String existingId =
                prefs.getString(
                        KEY_DEVICE_ID,
                        null
                );

        if (
                existingId != null &&
                !existingId.trim().isEmpty()
        ) {

            sCachedDeviceId = existingId;

            return sCachedDeviceId;
        }

        String newId =
                "receiver-"
                        + UUID.randomUUID().toString();

        prefs.edit()
                .putString(
                        KEY_DEVICE_ID,
                        newId
                )
                .apply();

        sCachedDeviceId = newId;

        return sCachedDeviceId;
    }
}
