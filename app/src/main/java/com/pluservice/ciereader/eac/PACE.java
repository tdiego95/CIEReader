package com.pluservice.ciereader.eac;

import java.math.BigInteger;
import java.util.Arrays;

public class PACE {

    public static class DHKey {
        public byte[] Public;
        public byte[] Private;
    }

    public interface IPACEMapping {
        IPACEAlgo Map(byte[] secret, byte[] nonce);
    }

    public interface IPACEAlgo extends Cloneable {
        DHKey GenerateKeyPair();

        byte[] GetSharedSecret(byte[] otherPubKey);

        byte[] Encrypt(byte[] data);

        Object clone();
    }

    public static class PACEAlgo {
        public Asn1Tag DG14Tag;
        public IPACEMapping mapping;
        public IPACEAlgo algo1;
        public IPACEAlgo algo2;

        public final byte[] GetSharedSecret1(byte[] otherPubKey) {
            return algo1.GetSharedSecret(otherPubKey);
        }

        public final byte[] GetSharedSecret2(byte[] otherPubKey) {
            return algo2.GetSharedSecret(otherPubKey);
        }

        public final void DoMapping(byte[] secret, byte[] nonce) {
            algo2 = mapping.Map(secret, nonce);
        }

        public PACEAlgo(Asn1Tag tag) {
            // dovrei inizializzare i vari componenti in base all'OID del tag.
            // per adesso mi limito a DH_GM
            DG14Tag = tag;
            algo1 = new DHAlgo(DG14Tag);
            mapping = new GenericMapping(algo1);
        }

        public final DHKey GenerateEphimeralKey1() {
            return algo1.GenerateKeyPair();
        }

        public final DHKey GenerateEphimeralKey2() {
            return algo2.GenerateKeyPair();
        }
    }

    public static class GenericMapping implements IPACEMapping {
        public IPACEAlgo algo;

        public GenericMapping(IPACEAlgo algo) {
            this.algo = algo;
        }

        public final IPACEAlgo Map(byte[] secret, byte[] nonce) {
            IPACEAlgo newAlgo = (IPACEAlgo) algo.clone();
            if (newAlgo instanceof DHAlgo) {
                DHAlgo dhAlgo = (DHAlgo) newAlgo;
                BigInteger temp = new BigInteger(1, dhAlgo.Group).modPow(new BigInteger(1, nonce), new BigInteger(1, dhAlgo.Prime));
                BigInteger temp2 = temp.multiply(new BigInteger(1, secret));
                dhAlgo.Group = temp2.remainder(new BigInteger(1, dhAlgo.Prime)).toByteArray();
            }

            return newAlgo;
        }
    }

    public static class DHAlgo implements IPACEAlgo {
        public final Object clone() {
            DHAlgo tempVar = new DHAlgo(DG14Tag);
            tempVar.Group = Group;
            tempVar.Order = Order;
            tempVar.Prime = Prime;
            tempVar.Key = Key;
            return tempVar;
        }

        public final byte[] Encrypt(byte[] data) {
            return null;
        }

        public final byte[] GetSharedSecret(byte[] otherPubKey) {
            return DiffieHellmann.ComputeKey(Group, Prime, new BigInteger(1, Key.Private), new BigInteger(1, otherPubKey));
        }

        public byte[] StandardDHParam2Prime = AppUtil.hexStringToByteArray("87A8E61DB4B6663CFFBBD19C651959998CEEF608660DD0F25D2CEED4435E3B00E00DF8F1D61957D4FAF7DF4561B2AA3016C3D91134096FAA3BF4296D830E9A7C209E0C6497517ABD5A8A9D306BCF67ED91F9E6725B4758C022E0B1EF4275BF7B6C5BFC11D45F9088B941F54EB1E59BB8BC39A0BF12307F5C4FDB70C581B23F76B63ACAE1CAA6B7902D52526735488A0EF13C6D9A51BFA4AB3AD8347796524D8EF6A167B5A41825D967E144E5140564251CCACB83E6B486F6B3CA3F7971506026C0B857F689962856DED4010ABD0BE621C3A3960A54E710C375F26375D7014103A4B54330C198AF126116D2276E11715F693877FAD7EF09CADB094AE91E1A1597");
        public byte[] StandardDHParam2Group = AppUtil.hexStringToByteArray("3FB32C9B73134D0B2E77506660EDBD484CA7B18F21EF205407F4793A1A0BA12510DBC15077BE463FFF4FED4AAC0BB555BE3A6C1B0C6B47B1BC3773BF7E8C6F62901228F8C28CBB18A55AE31341000A650196F931C77A57F2DDF463E5E9EC144B777DE62AAAB8A8628AC376D282D6ED3864E67982428EBC831D14348F6F2F9193B5045AF2767164E1DFC967C1FB3F2E55A4BD1BFFE83B9C80D052B985D182EA0ADB2A3B7313D3FE14C8484B1E052588B9B7D2BBD2DF016199ECD06E1557CD0915B3353BBB64E0EC377FD028370DF92B52C7891428CDC67EB6184B523D1DB246C32F63078490F00EF8D647D148D47954515E2327CFEF98C582664B4C0F6CC41659");
        public byte[] StandardDHParam2Order = AppUtil.hexStringToByteArray("8CF83642A709A097B447997640129DA299B1A47D1EB3750BA308B0FE64F5FBD3");

        public byte[] Prime;
        public byte[] Group;
        public byte[] Order;
        public DHKey Key;

        public final DHKey GenerateKeyPair() {
            Key = new DHKey();
            try {
                DiffieHellmann.GenerateKey(Group, Prime);
                Key.Private = DiffieHellmann.PrivateKey;
                if (DiffieHellmann.PublicKey.length == 257) {
                    DiffieHellmann.PublicKey = Arrays.copyOfRange(DiffieHellmann.PublicKey, 1, DiffieHellmann.PublicKey.length);
                }
                Key.Public = DiffieHellmann.PublicKey;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return Key;
        }

        public Asn1Tag DG14Tag;

        public DHAlgo(Asn1Tag tag) {
            int paramId = 0;
            try {
                paramId = AppUtil.toUint(tag.CheckTag(0x30).Child(2, (byte) 0x02).getData());
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (paramId != 2) {
                throw new RuntimeException("Parametri di default : " + paramId + " non supportati");
            }

            Prime = StandardDHParam2Prime;
            Group = StandardDHParam2Group;
            Order = StandardDHParam2Order;

            DG14Tag = tag;
        }
    }
}
