package com.pluservice.ciereader.eac;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;

public class DiffieHellmann {

    /*public static byte[] PrivateKey;
    public static byte[] PublicKey;

    public static void GenerateKey(byte[] Group, byte[] Prime) {
        while (Prime[0] == 0) {
            Prime = AppUtil.getSub(Prime, 1);
        }

        byte[] privData = new byte[Prime.length];
        Random rnd = new Random();
        for (int i = Prime.length - 20; i < Prime.length; i++) {
            privData[i] = (byte) (rnd.nextInt() % 256);
        }
        privData[Prime.length - 20] = 1;
        PrivateKey = privData;

        BigInteger x = BigInteger.ZERO;
        x.modPow();
        PublicKey = Group.modPow(PrivateKey, Prime);
    }

    public static byte[] ComputeKey(byte[] group, byte[] prime, byte[] privateKey, byte[] dhOtherPub) {
        byte[] key = dhOtherPub.modPow(privateKey, prime);
        while (prime[0] == 0) {
            prime = AppUtil.getSub(prime, 1);
        }
        if (key.length != prime.length) {
            key = AppUtil.fill(prime.length - key.length, (byte)0);
            AppUtil.appendByteArray(key, key);
        }

        javax.crypto.spec.DHPara
        return key;
    }*/

    //private static BigInteger g512 = new BigInteger("1234567890", 16);
    //private static BigInteger p512 = new BigInteger("1234567890", 16);

    /*D public static void GenerateKey(byte[] p512, byte[] g512) throws Exception {

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        DHParameterSpec dhParams = new DHParameterSpec(new BigInteger(p512), new BigInteger(g512));
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH", "BC");

        keyGen.initialize(dhParams, new SecureRandom());

        KeyAgreement aKeyAgree = KeyAgreement.getInstance("DH", "BC");
        KeyPair aPair = keyGen.generateKeyPair();
        KeyAgreement bKeyAgree = KeyAgreement.getInstance("DH", "BC");
        KeyPair bPair = keyGen.generateKeyPair();

        aKeyAgree.init(aPair.getPrivate());
        bKeyAgree.init(bPair.getPrivate());

        aKeyAgree.doPhase(bPair.getPublic(), true);
        bKeyAgree.doPhase(aPair.getPublic(), true);

        MessageDigest hash = MessageDigest.getInstance("SHA1", "BC");
        System.out.println(new String(hash.digest(aKeyAgree.generateSecret())));
        System.out.println(new String(hash.digest(bKeyAgree.generateSecret())));
    }*/
}
