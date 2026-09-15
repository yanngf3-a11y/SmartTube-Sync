package com.liskovsoft.smartyoutubetv2.tv.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.security.MessageDigest;
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
 *    como "emparejado" permanentemente, Y ADEMÁS se genera un token
 *    secreto único para ese Controller, que viaja en la respuesta de
 *    pairing.
 *
 * A partir de ahí, cualquier comando de control (play, pause, open,
 * etc.) tiene que venir con ese token — no alcanza con presentar el
 * deviceId, que viaja sin cifrar en cada mensaje y cualquiera en la
 * misma red podría copiar. El token es lo que realmente demuestra
 * que ese mensaje viene del Controller que se emparejó, no de un
 * impostor que solo conoce su ID.
 *
 * Todo esto se guarda con EncryptedSharedPreferences (cifrado con una
 * clave del Android Keystore del propio dispositivo), en vez de
 * SharedPreferences en texto plano.
 */
public final class SyncPairingManager {

    private static final String TAG =
            SyncPairingManager.class.getSimpleName();

    private static final String PREFS_NAME =
            "yg_sync_pairing_secure";

    private static final String KEY_CURRENT_CODE =
            "current_code";

    private static final String KEY_PAIRED_IDS =
            "paired_controller_ids";

    private static final String TOKEN_PREFIX =
            "token_";

    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static volatile SharedPreferences sPrefsCache;

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
     * controllerId como emparejado permanentemente (todavía sin
     * token — eso se pide aparte con getOrCreateToken()).
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
     * (de una vez anterior o de la actual). No confirma el token —
     * eso es verifyToken().
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

    /**
     * Devuelve el token secreto de ese controller, generando uno
     * nuevo la primera vez (justo después de un tryPair exitoso).
     * Si se vuelve a pedir para el mismo controllerId más adelante,
     * devuelve el mismo token — no se regenera solo.
     */
    public static synchronized String getOrCreateToken(
            Context context,
            String controllerId
    ) {

        String key =
                TOKEN_PREFIX
                        + controllerId.trim();

        String existing =
                prefs(context)
                        .getString(
                                key,
                                null
                        );

        if (
                existing != null &&
                !existing.trim().isEmpty()
        ) {
            return existing;
        }

        byte[] randomBytes =
                new byte[TOKEN_BYTE_LENGTH];

        RANDOM.nextBytes(
                randomBytes
        );

        String token =
                Base64.encodeToString(
                        randomBytes,
                        Base64.URL_SAFE
                                | Base64.NO_WRAP
                                | Base64.NO_PADDING
                );

        prefs(context)
                .edit()
                .putString(
                        key,
                        token
                )
                .apply();

        return token;
    }

    /**
     * true si providedToken es exactamente el token guardado para
     * ese controllerId. Comparación en tiempo constante (no corta
     * apenas encuentra una diferencia) para no filtrar el token de a
     * poco por cuánto tarda la comparación.
     */
    public static boolean verifyToken(
            Context context,
            String controllerId,
            String providedToken
    ) {

        if (
                controllerId == null ||
                controllerId.trim().isEmpty() ||
                providedToken == null ||
                providedToken.trim().isEmpty()
        ) {
            return false;
        }

        String key =
                TOKEN_PREFIX
                        + controllerId.trim();

        String storedToken =
                prefs(context)
                        .getString(
                                key,
                                null
                        );

        if (storedToken == null) {
            return false;
        }

        return MessageDigest.isEqual(
                storedToken.getBytes(),
                providedToken.trim().getBytes()
        );
    }

    private static synchronized SharedPreferences prefs(
            Context context
    ) {

        if (sPrefsCache != null) {
            return sPrefsCache;
        }

        Context appContext =
                context.getApplicationContext();

        try {

            String masterKeyAlias =
                    MasterKeys.getOrCreate(
                            MasterKeys.AES256_GCM_SPEC
                    );

            sPrefsCache =
                    EncryptedSharedPreferences.create(
                            PREFS_NAME,
                            masterKeyAlias,
                            appContext,
                            EncryptedSharedPreferences
                                    .PrefKeyEncryptionScheme
                                    .AES256_SIV,
                            EncryptedSharedPreferences
                                    .PrefValueEncryptionScheme
                                    .AES256_GCM
                    );

        } catch (Exception e) {

            /*
             * No debería pasar en un dispositivo Android 6.0+
             * normal, pero si el Keystore del dispositivo falla por
             * algún motivo raro, mejor seguir funcionando sin
             * cifrado que dejar el pairing roto del todo.
             */
            Log.e(
                    TAG,
                    "No se pudo inicializar el almacenamiento cifrado, "
                            + "usando SharedPreferences normal: "
                            + e.getMessage()
            );

            sPrefsCache =
                    appContext.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );
        }

        return sPrefsCache;
    }
}
