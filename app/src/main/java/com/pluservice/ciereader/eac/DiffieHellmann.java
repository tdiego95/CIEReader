package com.pluservice.ciereader.eac;

import java.math.BigInteger;
import java.util.Random;

public class DiffieHellmann {

    public static byte[] PrivateKey;
    public static byte[] PublicKey;

    public static void GenerateKey(byte[] group, byte[] prime) {
        while (prime[0] == 0) {
            prime = AppUtil.getSub(prime, 1);
        }

        byte[] privData = new byte[prime.length];
        Random rnd = new Random();
        for (int i = prime.length - 20; i < prime.length; i++) {
            privData[i] = (byte) (rnd.nextInt() % 256);
        }
        privData[prime.length - 20] = 1;
        PrivateKey = privData;

        PublicKey = new BigInteger(1, group).modPow(new BigInteger(1, PrivateKey), new BigInteger(1, prime)).toByteArray();
    }

    public static byte[] ComputeKey(byte[] group, byte[] prime, BigInteger privateKey, BigInteger publicKey) {
        byte[] key = publicKey.modPow(privateKey, new BigInteger(1, prime)).toByteArray();
        while (prime[0] == 0) {
            prime = AppUtil.getSub(prime, 1);
        }
        if (key.length != prime.length) {
            byte[] key2 = AppUtil.fill(prime.length - key.length, (byte) 0);
            key = AppUtil.appendByteArray(key2, key);
        }

        return key;
    }
}
