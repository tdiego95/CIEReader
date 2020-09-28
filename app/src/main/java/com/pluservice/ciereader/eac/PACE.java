package com.pluservice.ciereader.eac;

import java.math.BigInteger;

public class PACE {

    public class DHKey {
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
    }

    public class PACEAlgo {
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

    public class GenericMapping implements IPACEMapping {
        public IPACEAlgo algo;

        public GenericMapping(IPACEAlgo algo) {
            this.algo = algo;
        }

        public final IPACEAlgo Map(byte[] secret, byte[] nonce) {
            /*D Object tempVar = algo.Clone();
            IPACEAlgo newAlgo = tempVar instanceof IPACEAlgo ? (IPACEAlgo) tempVar : null;
            if (newAlgo instanceof DHAlgo) {
                DHAlgo dhNewAlgo = (DHAlgo) newAlgo;

                BigInteger group = new BigInteger(dhNewAlgo.Group);
                BigInteger nonce2 = new BigInteger(nonce);
                BigInteger prime = new BigInteger(dhNewAlgo.Prime);
                BigInteger secret2 = new BigInteger(secret);

                BigInteger temp = group.modPow(nonce2, prime);
                BigInteger temp2 = temp.multiply(secret2);
                dhNewAlgo.Group = temp2.remainder(prime).toByteArray();

            }
            return newAlgo;*/
            return null;
        }
    }

    public class DHAlgo implements IPACEAlgo {
        public final Object Clone() {
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
            //D return DiffieHellmann.ComputeKey(Group, Prime, Key.Private, otherPubKey);
            return null;
        }

        public byte[] StandardDHParam2Prime = "87A8E61D B4B6663C FFBBD19C 65195999 8CEEF608 660DD0F2 5D2CEED4 435E3B00 E00DF8F1 D61957D4 FAF7DF45 61B2AA30 16C3D911 34096FAA 3BF4296D 830E9A7C 209E0C64 97517ABD 5A8A9D30 6BCF67ED 91F9E672 5B4758C0 22E0B1EF 4275BF7B 6C5BFC11 D45F9088 B941F54E B1E59BB8 BC39A0BF 12307F5C 4FDB70C5 81B23F76 B63ACAE1 CAA6B790 2D525267 35488A0E F13C6D9A 51BFA4AB 3AD83477 96524D8E F6A167B5 A41825D9 67E144E5 14056425 1CCACB83 E6B486F6 B3CA3F79 71506026 C0B857F6 89962856 DED4010A BD0BE621 C3A3960A 54E710C3 75F26375 D7014103 A4B54330 C198AF12 6116D227 6E11715F 693877FA D7EF09CA DB094AE9 1E1A1597".getBytes();
        public byte[] StandardDHParam2Group = "3FB32C9B 73134D0B 2E775066 60EDBD48 4CA7B18F 21EF2054 07F4793A 1A0BA125 10DBC150 77BE463F FF4FED4A AC0BB555 BE3A6C1B 0C6B47B1 BC3773BF 7E8C6F62 901228F8 C28CBB18 A55AE313 41000A65 0196F931 C77A57F2 DDF463E5 E9EC144B 777DE62A AAB8A862 8AC376D2 82D6ED38 64E67982 428EBC83 1D14348F 6F2F9193 B5045AF2 767164E1 DFC967C1 FB3F2E55 A4BD1BFF E83B9C80 D052B985 D182EA0A DB2A3B73 13D3FE14 C8484B1E 052588B9 B7D2BBD2 DF016199 ECD06E15 57CD0915 B3353BBB 64E0EC37 7FD02837 0DF92B52 C7891428 CDC67EB6 184B523D 1DB246C3 2F630784 90F00EF8 D647D148 D4795451 5E2327CF EF98C582 664B4C0F 6CC41659".getBytes();
        public byte[] StandardDHParam2Order = "8CF83642 A709A097 B4479976 40129DA2 99B1A47D 1EB3750B A308B0FE 64F5FBD3".getBytes();

        public byte[] Prime;
        public byte[] Group;
        public byte[] Order;
        public DHKey Key;

        public final DHKey GenerateKeyPair() {
            Key = new DHKey();
            byte[] tempRef_Private = Key.Private;
            byte[] tempRef_Public = Key.Public;
            try {
                //D DiffieHellmann.GenerateKey(Group, Prime);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Key.Public = tempRef_Public;
            Key.Private = tempRef_Private;
            return Key;
        }

        public Asn1Tag DG14Tag;

        public DHAlgo(Asn1Tag tag) {
            /*D int paramId = AppUtil.toUint(tag.CheckTag(0x30).Child(2, 0x02).getData());
            if (paramId != 2) {
                throw new RuntimeException("Parametri di default : " + paramId + " non supportati");
            }*/

            Prime = StandardDHParam2Prime;
            Group = StandardDHParam2Group;
            Order = StandardDHParam2Order;

            DG14Tag = tag;
        }
    }
}
