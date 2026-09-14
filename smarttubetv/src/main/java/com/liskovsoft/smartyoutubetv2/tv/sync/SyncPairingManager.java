package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Gestor de pairing (emparejamiento) del receptor YG Sync.
 *
 * Flujo:
 * 1. Al arrancar, el receptor genera un código de 6 dígitos y lo
 *    muestra en la notificación permanente.
 * 2. El Controller ("YG Sync Control") envía ese código junto con su
 *    propio deviceId en un mensaje {"type":"pair", "senderId":
 *    "<idDelController>", "payload":{"code":"123456"}}.
 * 3. Si el código coincide, el deviceId del Controller queda guardado
 *    como "emparejado" de forma permanente en este receptor.
 *
 * NOTA: esto NO es todavía autenticación fuerte (eso es el paso 2.3,
 * que usará esta lista de emparejados para rechazar comandos de
 * dispositivos no emparejados) ni transporte cifrado (paso 2.4). Es
 * el primer paso: reconocerse mutuamente por identidad estable en vez
 * de confiar en "cualquiera que sepa la IP".
 */
public final class SyncPairingManager {

    private static final String PREFS_NAME =
            "yg_sync_pairing";

    private static final String KEY_CURRENT_CODE =
            "current_code";

    private static final String KEY_PAIRED_IDS =
            "paired_controller_ids";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private SyncPairingManager() {
    }

    /**
     * Genera un nuevo código de 6 dígitos y lo deja activo. Se llama
     * una vez por arranque del servicio, en SyncReceiverService.
     */
    public static synchronized String generateNewCode(
            Context context
    ) {

        int number =
                RANDOM.nextInt(1_000_000);

        String code =
                String.format(
                        "%06d",
                        number
                );

        prefs(context)
                .edit()
                .putString(
                        KEY_CURRENT_CODE,
                        code
                )
                .apply();

        return code;
    }

    public static String getCurrentCode(
            Context context
    ) {

        return prefs(context)
                .getString(
                        KEY_CURRENT_CODE,
                        ""
                );
    }

    /**
     * Intenta emparejar un controller usando el código actual.
     * Devuelve true si el código es correcto, y en ese caso guarda el
     * controllerId como emparejado permanentemente.
     */
    public static synchronized boolean tryPair(
            Context context,
            String code,
            String controllerId
    ) {

        if (
                code == null ||
                controllerId == null ||
                controllerId.trim().isEmpty()
        ) {
            return false;
        }

        String currentCode =
                getCurrentCode(context);

        if (
                currentCode.isEmpty() ||
                !currentCode.equals(code.trim())
        ) {
            return false;
        }

        Set<String> paired =
                new HashSet<>(
                        prefs(context)
                                .getStringSet(
                                        KEY_PAIRED_IDS,
                                        Collections.emptySet()
                                )
                );

        paired.add(
                controllerId.trim()
        );

        prefs(context)
                .edit()
                .putStringSet(
                        KEY_PAIRED_IDS,
                        paired
                )
                .apply();

        return true;
    }

    /**
     * true si ese controllerId ya está emparejado con este receptor
     * (de una vez anterior o de la actual).
     */
    public static boolean isPaired(
            Context context,
            String controllerId
    ) {

        if (
                controllerId == null ||
                controllerId.trim().isEmpty()
        ) {
            return false;
        }

        Set<String> paired =
                prefs(context)
                        .getStringSet(
                                KEY_PAIRED_IDS,
                                Collections.emptySet()
                        );

        return paired.contains(
                controllerId.trim()
        );
    }

    private static SharedPreferences prefs(
            Context context
    ) {

        return context.getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }
}
