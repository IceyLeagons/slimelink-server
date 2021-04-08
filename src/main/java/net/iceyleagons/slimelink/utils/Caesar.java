package net.iceyleagons.slimelink.utils;

import java.util.Base64;

public class Caesar {
    public static String encrypt(String plain, int offset) {
        String b64encoded = Base64.getEncoder().encodeToString(plain.getBytes());

        // Reverse the string
        String reverse = new StringBuffer(b64encoded).reverse().toString();

        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < reverse.length(); i++)
            tmp.append((char)(reverse.charAt(i) + offset));

        return tmp.toString();
    }

    public static String decrypt(String secret, int offset) {
        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < secret.length(); i++)
            tmp.append((char)(secret.charAt(i) - offset));

        String reversed = new StringBuffer(tmp.toString()).reverse().toString();
        return new String(Base64.getDecoder().decode(reversed));
    }

}
