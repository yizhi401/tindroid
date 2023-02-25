package co.tinode.tindroid.utils;

import android.util.Base64;

public class Base64Encoder {
    private static final String SALT = "";
    private static final int REPEAT = 1;

    public static String Encode(String str) {
        String salted = str + SALT;
        String encoded = str;
        for (int i = 0; i < REPEAT; i++) {
            encoded = Base64.encodeToString(salted.getBytes(), Base64.NO_WRAP);
            salted = encoded + SALT;
        }
        return encoded;
    }

    public static String Decode(String encrypted) {
        String decoded = encrypted;
        for (int i = 0; i < REPEAT; i++) {
            decoded = new String(Base64.decode(decoded, Base64.NO_WRAP));
            decoded = decoded.substring(0, decoded.length() - SALT.length());
        }
        return decoded;
    }


}

