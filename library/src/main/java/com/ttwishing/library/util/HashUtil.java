package com.ttwishing.library.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by kurt on 10/29/15.
 */
public class HashUtil {

    public static String hmacSha1Base64(String str, byte[] key) throws Exception {
        SecretKeySpec localSecretKeySpec = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(localSecretKeySpec);
        return Base64.encodeBase64String(mac.doFinal(str.getBytes("UTF-8")));
    }

    public static String hmacSha1Base64UrlSafe(String str, byte[] key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKeySpec);
        return Base64.encodeBase64URLSafeString(mac.doFinal(str.getBytes("UTF-8")));
    }

    public static String md5(String str) {
        return new String(Hex.encodeHex(DigestUtils.md5(str)));
    }
}
