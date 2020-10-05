package com.pluservice.ciereader.eac;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.gemalto.jp2.JP2Decoder;
import com.pluservice.ciereader.neptune.ICoupler;

import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.jmrtd.BACKey;
import org.jmrtd.BACKeySpec;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.LDSFileUtil;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.DG11File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.PACEInfo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** effettua la lettura dei dati MRTD dal microprocessore **/

public class Eac {

	private IsoDep isoDep;
	private ICoupler coupler;
	private MRZInfo mrz;
	private String can;

	private Context context;
	private static byte[] kSessEnc = null;
	private static byte[] kSessMac = null;
	private static byte[] seq = null;
	private List<Byte> dgList = new ArrayList<>();
	private static Map<Integer, byte[]> mappaDg = null;
	private int index = 0;
	private static final String TAG = "m.recupero";

	//costruttore
	public Eac(IsoDep isoDep, ICoupler coupler, MRZInfo mrz, String can, Context context) {
		this.isoDep = isoDep;
		this.coupler = coupler;
		this.mrz = mrz;
		this.can = can;
		this.context = context;
	}

	private void sendUpdateToActivity(String info) {
		Intent intent = new Intent();
		intent.setAction("UPDATE_INFO");
		intent.putExtra("update_info", info);
		context.sendBroadcast(intent);
	}

	//metodo iniziale per lo scambio delle chiavi di sessione
	public void bacAuthentication() throws Exception {

		if ((isoDep == null && coupler == null) || mrz == null || context == null) return;

		byte[] apduCmd = AppUtil.hexStringToByteArray("00A4040C07A0000002471001"); //select di controllo

		ApduResponse res;
		if (isoDep != null)
			res = new ApduResponse(isoDep.transceive(apduCmd));
		else
			res = new ApduResponse(coupler.isoDepTransceive(apduCmd));

		Log.i(TAG, "risposta sw: " + res.getSwHex());
		Log.i(TAG, "risposta full: " + AppUtil.bytesToHex(res.getResponse()));
		if (res.getSwHex().equals("9000")) {
			Log.i(TAG, "INIT BAC AUTHENTICATION:");
			sendUpdateToActivity("Inizio autenticazione BAC");
			// init BAC auth
			byte[] challenge = AppUtil.hexStringToByteArray("0084000008");

			ApduResponse apduRes;
			if (isoDep != null)
				apduRes = new ApduResponse(isoDep.transceive(challenge));
			else
				apduRes = new ApduResponse(coupler.isoDepTransceive(challenge));

			if (!apduRes.getSwHex().equals("9000")) {
				Log.i(TAG, "Errore nella richiesta di challenge [apdu]:0084000008");
				throw new Exception("Errore nella richiesta di challenge [apdu]:0084000008");
			}

			byte[] rndMrtd = apduRes.getResponse();
			byte[] birth = mrz.getDateOfBirth().getBytes();
			byte[] expire = mrz.getDateOfExpiry().getBytes();

			//concateno i dati: numero documento e le due date
			byte[] pn = mrz.getDocumentNumber().getBytes();
			byte[] seedPartPn = AppUtil.appendByte(pn, AppUtil.checkdigit(pn));
			byte[] seedPartBirth = AppUtil.appendByte(birth, AppUtil.checkdigit(birth));
			byte[] seedPartExpire = AppUtil.appendByte(expire, AppUtil.checkdigit(expire));

			byte[] bacSeedData = AppUtil.appendByteArray(seedPartPn, seedPartBirth);
			bacSeedData = AppUtil.appendByteArray(bacSeedData, seedPartExpire); //I00000000666011111512030
			byte[] bacEnc = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(AppUtil.getLeft(AppUtil.getSha1(bacSeedData), 16), new byte[]{(byte) 0x00, 0x00, 0x00, 0x01})), 16);
			byte[] bacMac = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(AppUtil.getLeft(AppUtil.getSha1(bacSeedData), 16), new byte[]{(byte) 0x00, 0x00, 0x00, 0x02})), 16);

			//genero i byte[] random
			byte[] rndIs1 = new byte[8];
			AppUtil.getRandomByte(rndIs1);

			byte[] kIs = new byte[16];
			AppUtil.getRandomByte(kIs);

			byte[] eIs1 = Algoritmi.desEnc(bacEnc, AppUtil.appendByteArray(AppUtil.appendByteArray(rndIs1, rndMrtd), kIs));//32byte
			byte[] eisMac = Algoritmi.macEnc(bacMac, AppUtil.getIsoPad(eIs1)); //8byte

			//pronto per la mutua auth
			byte apduMutaAuth[] = AppUtil.appendByteArray(eIs1, eisMac);//46byte
			byte[] apduMutuaAutenticazione = AppUtil.appendByte(AppUtil.appendByteArray(AppUtil.appendByteArray(new byte[]{0x00, (byte) 0x82, 0x00, 0x00, 0x28}, eIs1), eisMac), (byte) 0x28);

			ApduResponse respMutaAuth;
			if (isoDep != null)
				respMutaAuth = new ApduResponse(isoDep.transceive(apduMutuaAutenticazione));
			else
				respMutaAuth = new ApduResponse(coupler.isoDepTransceive(apduMutuaAutenticazione));

			if (!respMutaAuth.getSwHex().equals("9000")) {
				Log.i(TAG, "Errore sulla mutua auth BAC " + respMutaAuth.getSwHex());
				sendUpdateToActivity("-- ERRORE AUTENTICAZIONE. I DATI SONO ERRATI. RISCANSIONARE IL CODICE MRZ --");
				throw new Exception("Errore durante la procedura di autenticazione BAC! Ripetere la scansione. " + respMutaAuth.getSwHex());
			}

			byte[] kIsMac = Algoritmi.macEnc(bacMac, AppUtil.getIsoPad(AppUtil.getLeft(respMutaAuth.getResponse(), 32)));
			byte[] kIsMac2 = AppUtil.getRight(respMutaAuth.getResponse(), 8);
			if (!Arrays.equals(kIsMac, kIsMac2)) {
				throw new Exception("Errore sulla auth dell'MRTD!!!");
			}
			byte[] decResp = Algoritmi.desDec(bacEnc, AppUtil.getLeft(respMutaAuth.getResponse(), 32));
			byte[] kMrtd = AppUtil.getRight(decResp, 16);
			byte[] kSeed = AppUtil.stringXor(kIs, kMrtd);

			//parsing chiavi di sessione
			kSessMac = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(kSeed, new byte[]{0x00, 0x00, 0x00, 0x02})), 16);
			kSessEnc = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(kSeed, new byte[]{0x00, 0x00, 0x00, 0x01})), 16);

			byte[] tmp = AppUtil.getSub(decResp, 4, 4);
			byte[] tmp2 = AppUtil.getSub(decResp, 12, 4);
			seq = AppUtil.appendByteArray(tmp, tmp2);
			Log.i(TAG, "END BAC AUTHENTICATION:");
			sendUpdateToActivity("Autenticazione BAC completata");
		} else {
			Log.i(TAG, "protocolla SAC");
		}
	}

	HashMap<String, PACE.PACEAlgo> PACEAlgo = new HashMap<>();
	String DH_GM_DES_Oid = "04007f00070202040101"; //"BAB/AAcCAgQBAQ==";
	String CAN = "836841";

	public void paceAuthentication() {

		try {
			PACE.PACEAlgo algo = PACEAlgo.get(DH_GM_DES_Oid);

			byte[] temp1 = algo.DG14Tag.Child(0, (byte) 6).getData();
			byte[] temp2 = AppUtil.asn1Tag(temp1, 0x80);
			byte[] MSEData = AppUtil.appendByteArray(temp2, AppUtil.asn1Tag(new byte[]{0x02}, 0x83)); //mode : MRZ = 0x01 - CAN = 0x02

			ApduResponse res;
			if (isoDep != null)
				res = new ApduResponse(isoDep.transceive(new Apdu((byte) 0x00, (byte) 0x22, (byte) 0xc1, (byte) 0xa4, MSEData).GetBytes()));
			else
				res = new ApduResponse(coupler.isoDepTransceive(new Apdu((byte) 0x00, (byte) 0x22, (byte) 0xc1, (byte) 0xa4, MSEData).GetBytes()));
			if (!res.getSwHex().equals("9000")) {
				throw new Exception("Errore nel protocollo PACE:MSE Set AT - " + res.getSwHex());
			}

			byte[] GAData1 = AppUtil.asn1Tag(new byte[]{}, 0x7c);
			ApduResponse res2;
			if (isoDep != null)
				res2 = new ApduResponse(isoDep.transceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData1, (byte) 0x00).GetBytes()));
			else
				res2 = new ApduResponse(coupler.isoDepTransceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData1, (byte) 0x00).GetBytes()));
			if (!res2.getSwHex().equals("9000")) {
				throw new Exception("Errore nel protocollo PACE:General Authenticate 1 - " + res2.getSwHex());
			}

			byte[] encryptedNonce = Asn1Tag.Companion.parse(res2.getResponse(), false).CheckTag(0x7c).Child(0, (byte) 0x80).getData();

			// la chiave per decifrare il nonce è SHA1(K||00000003);
			byte[] keyNonce = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(CAN.getBytes(), new byte[]{(byte) 0x00, 0x00, 0x00, 0x03})), 16);
			byte[] nonce = Algoritmi.desDec(keyNonce, encryptedNonce);
			PACE.DHKey key1 = algo.GenerateEphimeralKey1();

			// il secondo GA serve a inviare la chiave pubblica effimera
			byte[] GAData2 = AppUtil.asn1Tag(AppUtil.asn1Tag(key1.Public, 0x81), 0x7c);
			ApduResponse res3;
			if (isoDep != null)
				res3 = new ApduResponse(isoDep.transceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData2, (byte) 0x00).GetBytes()));
			else
				res3 = new ApduResponse(coupler.isoDepTransceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData2, (byte) 0x00).GetBytes()));
			if (!res3.getSwHex().equals("9000")) {
				throw new Exception("Errore nel protocollo PACE:General Authenticate 2 - " + res3.getSwHex());
			}

			byte[] otherPubKey1 = Asn1Tag.Companion.parse(res3.getResponse(), false).CheckTag(0x7c).Child(0, (byte) 0x82).getData();

			byte[] secret1 = algo.GetSharedSecret1(otherPubKey1);
			algo.DoMapping(secret1, nonce);
			PACE.DHKey key2 = algo.GenerateEphimeralKey2();

			// il terzo GA serve a inviare la chiave pubblica effimera nei nuovi parametri di dominio
			byte[] GAData3 = AppUtil.asn1Tag(AppUtil.asn1Tag(key2.Public, 0x83), 0x7c);
			ApduResponse res4;
			if (isoDep != null)
				res4 = new ApduResponse(isoDep.transceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData3, (byte) 0x00).GetBytes()));
			else
				res4 = new ApduResponse(coupler.isoDepTransceive(new Apdu((byte) 0x10, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData3, (byte) 0x00).GetBytes()));
			if (!res4.getSwHex().equals("9000")) {
				throw new Exception("Errore nel protocollo PACE:General Authenticate 3 - " + res4.getSwHex());
			}

			byte[] otherPubKey2 = Asn1Tag.Companion.parse(res4.getResponse(), false).CheckTag(0x7c).Child(0, (byte) 0x84).getData();
			byte[] dynamicBindingData = otherPubKey2;
			byte[] secret2 = algo.GetSharedSecret2(otherPubKey2);

			kSessMac = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(secret2, new byte[]{(byte) 0x00, 0x00, 0x00, 0x02})), 16);
			kSessEnc = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(secret2, new byte[]{(byte) 0x00, 0x00, 0x00, 0x01})), 16);

			Asn1Tag oidTag = algo.DG14Tag.child(0);
			byte[] authData = AppUtil.asn1Tag(AppUtil.appendByteArray(AppUtil.asn1Tag(oidTag.getData(), oidTag.getTagRawNumber()), AppUtil.asn1Tag(otherPubKey2, 0x84)), 0x7F49);
			byte[] authToken = Algoritmi.macEnc(kSessMac, AppUtil.getIsoPad(authData));

			// il quarto e ultimo GA serve a inviare l'authentication token
			// fine del command chaining
			byte[] GAData4 = AppUtil.asn1Tag(AppUtil.asn1Tag(authToken, 0x85), 0x7c);
			ApduResponse res5;
			if (isoDep != null)
				res5 = new ApduResponse(isoDep.transceive(new Apdu((byte) 0x00, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData4, (byte) 0x00).GetBytes()));
			else
				res5 = new ApduResponse(coupler.isoDepTransceive(new Apdu((byte) 0x00, (byte) 0x86, (byte) 0x00, (byte) 0x00, GAData4, (byte) 0x00).GetBytes()));
			if (!res5.getSwHex().equals("9000")) {
				throw new Exception("Errore nel protocollo PACE:General Authenticate 4 - " + res5.getSwHex());
			}

			byte[] otherAuthData = AppUtil.asn1Tag(AppUtil.appendByteArray(AppUtil.asn1Tag(oidTag.getData(), oidTag.getTagRawNumber()), AppUtil.asn1Tag(key2.Public, 0x84)), 0x7F49);
			byte[] otherAuthToken = Asn1Tag.Companion.parse(res5.getResponse(), false).CheckTag(0x7c).Child(0, (byte) 0x86).getData();
			byte[] otherAuthTokenCalc = Algoritmi.macEnc(kSessMac, AppUtil.getIsoPad(otherAuthData));

			if (!Arrays.equals(otherAuthTokenCalc, otherAuthToken))
				throw new Exception("Errore nel protocollo PACE:Authentication token non corrispondente");

			seq = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

			byte[] data = sm(kSessEnc, kSessMac, new byte[]{0x0c, (byte) 0xa4, 0x04, 0x0c, 0x07, (byte) 0xA0, 0x00, 0x00, 0x02, 0x47, 0x10, 0x01, 0x00});
			ApduResponse res6;
			if (isoDep != null)
				res6 = new ApduResponse(isoDep.transceive(data));
			else
				res6 = new ApduResponse(coupler.isoDepTransceive(data));
			if (!res6.getSwHex().equals("9000")) {
				throw new Exception("Errore nella selezione dell'LDF : " + res6.getSwHex());
			}

			respSM(kSessEnc, kSessMac, res6.getResponse());

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public boolean isSac() {

		try {
			byte[] cardAccess = readCardAccess();
			// il cardAccess contiene gli algoritmi PACE supportati
			Asn1Tag caTag = Asn1Tag.Companion.parse(cardAccess, false);
			caTag.CheckTag(0x31);
			for (Asn1Tag tag : caTag.getChildren())
				PACEAlgo.put(AppUtil.bytesToHex(tag.CheckTag(0x30).Child(0, (byte) 6).getData()), new PACE.PACEAlgo(tag));

			if (!PACEAlgo.containsKey(DH_GM_DES_Oid))
				return false;

			Log.d(TAG, "CardAccess letto correttamente; il chip è SAC");
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return true;
	}

	public byte[] readCardAccess() {
		byte[] data = new byte[]{};
		try {
			byte[] apdu = AppUtil.hexStringToByteArray("00A4020C02011C"); //SELECT File command
			ApduResponse resp;
			if (isoDep != null)
				resp = new ApduResponse(isoDep.transceive(apdu));
			else
				resp = new ApduResponse(coupler.isoDepTransceive(apdu));
			if (!resp.getSwHex().equals("9000")) {
				throw new Exception("Errore nella selezione del card access");
			}

			ApduResponse chunkLen;
			if (isoDep != null)
				chunkLen = new ApduResponse(isoDep.transceive(Apdu.ReadBinary(0, (byte) 6).GetBytes()));
			else
				chunkLen = new ApduResponse(coupler.isoDepTransceive(Apdu.ReadBinary(0, (byte) 6).GetBytes()));
			if (!chunkLen.getSwHex().equals("9000")) {
				throw new Exception("Errore nella lettura del Card Access");
			}

			ByteArrayInputStream ms = new ByteArrayInputStream(chunkLen.getResponse());
			int maxLen = Asn1Tag.Companion.parseLength(ms, 0, chunkLen.getResponse().length);
			while (data.length < maxLen) {
				int readLen = Math.min(200, maxLen - data.length);

				ApduResponse chunk;
				if (isoDep != null)
					chunk = new ApduResponse(isoDep.transceive(Apdu.ReadBinary(data.length, (byte) readLen).GetBytes()));
				else
					chunk = new ApduResponse(coupler.isoDepTransceive(Apdu.ReadBinary(data.length, (byte) readLen).GetBytes()));
				if (!chunk.getSwHex().equals("9000")) {
					throw new Exception("Errore nella lettura del Card Access 2");
				}

				data = AppUtil.appendByteArray(data, chunk.getResponse());
			}
		} catch (Exception ex) {
			Log.e(TAG, "Errore nella lettura del Card Access 3");
			ex.printStackTrace();
		}

		return data;
	}

	private byte[] newApdu(byte cla, byte ins, byte p1, byte p2, byte le) {
		byte[] pbtAPDU;
		pbtAPDU = new byte[5];
		pbtAPDU[0] = cla;
		pbtAPDU[1] = ins;
		pbtAPDU[2] = p1;
		pbtAPDU[3] = p2;
		pbtAPDU[4] = le;
		return pbtAPDU;
	}

	//recupero la struttura dei dg, la conservo dentro una mappa
	public void readDgs() throws Exception {

		sendUpdateToActivity("Lettura data groups in corso");
		Log.i(TAG, "leggo i dg");
		mappaDg = new HashMap<Integer, byte[]>();
		byte[] efCom = leggiDg(30);
		Log.i(TAG, "efcom: => " + AppUtil.bytesToHex(efCom));
		Asn1Tag comtag = Asn1Tag.Companion.parse(efCom, false);
		byte[] dhList = comtag.Child(2, (byte) 0x5c).getData();
		for (byte dhNum : dhList) {
			dgList.add(dhNum);
			int dgNum = 0;
			switch (dhNum) {
				case 0x61:
					dgNum = 1;
					break;
				case 0x75:
					dgNum = 2;
					break;
				case 0x6b:
					dgNum = 11;
					break;
				case 0x6e:
					dgNum = 14;
					break;
				case 0x77:
					dgNum = 29;
					break;
			}

			if (dgNum != 0)
				mappaDg.put(dgNum, leggiDg(dgNum));

			if (!mappaDg.containsKey(new Integer(29)))
				mappaDg.put(new Integer(29), leggiDg(29));
		}

		sendUpdateToActivity("Lettura data groups completata");
	}

	public void parseDg1() throws Exception {
		//D IL DG1 TORNA L'MRZ, UTILIZZARE IN CASO DI BISOGNO ESTRAZIONE SESSO E NAZIONALITA
	}

	public UserInfo parseDg11() throws Exception {
		sendUpdateToActivity("Inizio lettura dati personali");
		if (mappaDg.containsKey(new Integer(11))) {
			byte[] data = leggiDg(new Integer(11));
			Asn1Tag tag = Asn1Tag.Companion.parse(data, false);

			UserInfo userInfo = new UserInfo();
			userInfo.setFullName(AppUtil.getStringFromByteArray(tag.child(1).getData()));
			userInfo.setCodiceFiscale(AppUtil.getStringFromByteArray(tag.child(2).getData()));
			userInfo.setBirthDate(AppUtil.getStringFromByteArray(tag.child(3).getData()));
			userInfo.setBirthPlace(AppUtil.getStringFromByteArray(tag.child(4).getData()));
			userInfo.setResidence(AppUtil.getStringFromByteArray(tag.child(5).getData()));
			sendUpdateToActivity("Lettura dati personali completata con successo");
			return userInfo;
		} else {
			sendUpdateToActivity("-- ERRORE LETTURA DATI PERSONALI --");
			return null;
		}
	}

	public PassportService auth(/*mode*/) throws Exception {

		sendUpdateToActivity("Inizio lettura foto in corso");
		Bitmap bitmap = null;
		PassportService service = null;

		try {
			CardService cardService = CardService.getInstance(isoDep);
			cardService.open();

			service = new PassportService(
					cardService,
					can != null ? PassportService.EXTENDED_MAX_TRANCEIVE_LENGTH : PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
					PassportService.DEFAULT_MAX_BLOCKSIZE,
					false,
					true);
			service.open();

			if (mrz != null) {
				/* BAC AUTH WITH MRZ */
				BACKeySpec bacKey = new BACKey(mrz.getDocumentNumber(), mrz.getDateOfBirth(), mrz.getDateOfExpiry());

				service.sendSelectApplet(true);
				service.doBAC(bacKey);

			} else if (can != null) {

				/* PACE AUTH WITH CAN */
				PACEKeySpec paceKey = PACEKeySpec.createCANKey(can);

				boolean paceSucceeded = false;

				try {
					CardAccessFile cardAccessFile = new CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS));
					Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();
					SecurityInfo securityInfo = securityInfos.iterator().next();

					List<PACEInfo> paceInfos = getPACEInfos(securityInfos);

					if (paceInfos.size() > 0) {
						PACEInfo paceInfo = paceInfos.get(0);
						service.doPACE(paceKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()), paceInfo.getParameterId());
						paceSucceeded = true;
					} else {
						paceSucceeded = true;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				service.sendSelectApplet(paceSucceeded);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return service;
	}

	public UserInfo readDg11(PassportService service) {

		sendUpdateToActivity("Inizio lettura dati personali");
		DG11File dg11File;
		UserInfo userInfo = new UserInfo();

		try {
			dg11File = (DG11File) LDSFileUtil.getLDSFile(PassportService.EF_DG11, service.getInputStream(PassportService.EF_DG11));
			userInfo.setBirthDate(dg11File.getFullDateOfBirth());
			userInfo.setCodiceFiscale(dg11File.getPersonalNumber());
			userInfo.setFullName(dg11File.getNameOfHolder());
			userInfo.setResidence(String.valueOf(dg11File.getPermanentAddress()));
			userInfo.setBirthPlace(String.valueOf(dg11File.getPlaceOfBirth()));
			sendUpdateToActivity("Lettura dati personali completata con successo");

		} catch (IOException | CardServiceException e) {
			e.printStackTrace();
			sendUpdateToActivity("Errore lettura dati personali");
		}

		return userInfo;
	}

	public Bitmap readDg2(PassportService service) {

		sendUpdateToActivity("Inizio lettura foto");
		DG2File dg2File;
		Bitmap photo = null;

		try {
			dg2File = (DG2File) LDSFileUtil.getLDSFile(PassportService.EF_DG2, service.getInputStream(PassportService.EF_DG2));

			List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
			List<FaceInfo> faceInfos = dg2File.getFaceInfos();
			for (FaceInfo faceInfo : faceInfos) {
				allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
			}

			if (!allFaceImageInfos.isEmpty()) {

				FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();
				int imageLength = faceImageInfo.getImageLength();
				DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
				byte[] buffer = new byte[imageLength];
				dataInputStream.readFully(buffer, 0, imageLength);

				photo = new JP2Decoder(buffer).decode();

				sendUpdateToActivity("Lettura foto completata con successo");
			}
		} catch (IOException | CardServiceException e) {
			e.printStackTrace();
			sendUpdateToActivity("Errore lettura foto");
		}

		return photo;
	}

	private static List<PACEInfo> getPACEInfos(Collection<SecurityInfo> securityInfos) {
		List<PACEInfo> paceInfos = new ArrayList<PACEInfo>();

		if (securityInfos == null) {
			return paceInfos;
		}

		for (SecurityInfo securityInfo : securityInfos) {
			if (securityInfo instanceof PACEInfo) {
				paceInfos.add((PACEInfo) securityInfo);
			}
		}

		return paceInfos;
	}

	//metodo per la lettura dei dg
	//numDg: il numero del datagroup da leggere
	private byte[] leggiDg(int numDg) throws Exception {
		Log.i(TAG, "Leggo il dg: " + numDg);

		byte[] data = new byte[0];
		byte somma = (byte) ((byte) numDg + (byte) 0x80);//-126
		String hex = AppUtil.bytesToHex(new byte[]{somma});//82
		byte[] appo = AppUtil.hexStringToByteArray("0cb0" + hex + "0006");//. ToString("X2") + " 00 06")
		byte[] apdu = sm(kSessEnc, kSessMac, appo);// ' read DG

		ApduResponse respDg;
		if (isoDep != null)
			respDg = new ApduResponse(isoDep.transceive(apdu));
		else
			respDg = new ApduResponse(coupler.isoDepTransceive(apdu));

		if (!respDg.getSwHex().equals("9000")) {
			Log.i(TAG, "Errore nella selezione del DG" + numDg + " SW: " + respDg.getSwHex());
			throw new Exception("Errore nella selezione del DG" + numDg + " SW: " + respDg.getSwHex());
		}

		byte[] chunkLen = respSM(kSessEnc, kSessMac, respDg.getResponse());
		ByteArrayInputStream ms = new ByteArrayInputStream(chunkLen);
		int maxLen = Asn1Tag.Companion.parseLength(ms, 0, chunkLen.length);

		while (data.length < maxLen) {
			int readLen = Math.min(0xe0, maxLen - data.length);//224
			byte[] appo2 = AppUtil.appendByte(AppUtil.appendByte(AppUtil.appendByte(AppUtil.hexStringToByteArray("0cb0"), (byte) ((byte) (data.length / 256) & (byte) 0x7f)), (byte) (data.length & 0xff)), (byte) readLen);

			byte[] apduDg = sm(kSessEnc, kSessMac, appo2);

			ApduResponse respDg2;
			if (isoDep != null)
				respDg2 = new ApduResponse(isoDep.transceive(apduDg));
			else
				respDg2 = new ApduResponse(coupler.isoDepTransceive(apduDg));

			if (!respDg2.getSwHex().equals("9000")) {
				Log.i(TAG, "Errore nella lettura del DG" + numDg + " codice errore: " + respDg2.getSwHex());
				throw new Exception("Errore nella lettura del DG" + numDg + " codice errore: " + respDg2.getSwHex());
			}
			byte[] chunk = respSM(kSessEnc, kSessMac, respDg2.getResponse());

			data = AppUtil.appendByteArray(data, chunk);
		}

		/* LOG */
		StringBuilder x = new StringBuilder();
		for (byte datum : data) {
			x.append(" ").append(datum);
		}
		Log.d("ASD", "data byte : " + x);
		Log.d("ASD", "data hex : " + AppUtil.bytesToHex(data));

		return data;
	}

	private byte[] respSM(byte[] keyEnc, byte[] keySig, byte[] resp) throws Exception {
		return respSM(keyEnc, keySig, resp, false);
	}

	//metodo per la gestione della risposta Secure Message
	private byte[] respSM(byte[] keyEnc, byte[] keySig, byte[] resp, boolean odd) throws Exception {

		AppUtil.increment(seq);
		// cerco il tag 87
		setIndex(0);
		byte[] encData = new byte[]{};
		byte[] encObj = new byte[]{};
		byte[] dataObj = new byte[]{};

		do {
			if (resp[index] == (byte) 0x99) {
				if (resp[index + 1] != (byte) 0x02)
					throw new Exception("Errore nella verifica del SM - lunghezza del DataObject");
				dataObj = AppUtil.getSub(resp, index, 4);
				setIndex(index, 4);//index += 4;
				continue;
			}

			if (resp[index] == (byte) 0x8e) {
				byte[] calcMac = Algoritmi.macEnc(keySig, AppUtil.getIsoPad(AppUtil.appendByteArray(AppUtil.appendByteArray(seq, encObj), dataObj)));
				setIndex(index, 1);//index++;
				if (resp[index] != (byte) 0x08)
					throw new Exception("Errore nella verifica del SM - lunghezza del MAC errata");
				setIndex(index, 1);//index++;
				if (!Arrays.equals(calcMac, AppUtil.getSub(resp, index, 8)))
					throw new Exception("Errore nella verifica del SM - MAC non corrispondente");
				setIndex(index, 8);//index += 8;
				continue;
			}

			if (resp[index] == (byte) 0x87) {
				if (unsignedToBytes(resp[index + 1]) > unsignedToBytes((byte) 0x80)) {
					int lgn = 0;
					int llen = unsignedToBytes(resp[index + 1]) - 0x80;
					if (llen == 1)
						lgn = unsignedToBytes(resp[index + 2]);
					if (llen == 2)
						lgn = (resp[index + 2] << 8) | resp[index + 3];
					encObj = AppUtil.getSub(resp, index, llen + lgn + 2);
					encData = AppUtil.getSub(resp, index + llen + 3, lgn - 1); // ' levo il padding indicator
					setIndex(index, llen, lgn, 2);//index += llen + lgn + 2;
				} else {
					encObj = AppUtil.getSub(resp, index, resp[index + 1] + 2);
					encData = AppUtil.getSub(resp, index + 3, resp[index + 1] - 1); // ' levo il padding indicator
					setIndex(index, resp[index + 1], 2); //index += resp[index + 1] + 2;
				}
			} else if (resp[index] == (byte) 0x85) {
				if (resp[index + 1] > (byte) 0x80) {
					int lgn = 0;
					int llen = resp[index + 1] - 0x80;
					if (llen == 1)
						lgn = resp[index + 2];
					if (llen == 2)
						lgn = (resp[index + 2] << 8) | resp[index + 3];
					encObj = AppUtil.getSub(resp, index, llen + lgn + 2);
					encData = AppUtil.getSub(resp, index + llen + 2, lgn); // ' levo il padding indicator
					setIndex(index, llen, lgn, 2);//index += llen + lgn + 2;
				} else {
					encObj = AppUtil.getSub(resp, index, resp[index + 1] + 2);
					encData = AppUtil.getSub(resp, index + 2, resp[index + 1]);
					setIndex(index, resp[index + 1], 2); //index += resp[index + 1] + 2;
				}
			} else {
				throw new Exception("Tag non previsto nella risposta in SM");
			}
			//index = index + resp[index + 1] + 1;
		} while (index < resp.length);

		if (encData.length > 0) {
			if (odd) {
        		/* byte[] smResp = isoRemove(Algoritmi.desDec(keyEnc, encData));
        		 Asn1Tag tag = Asn1Tag.parse(smResp, false);
        		 return tag.get*/
				Log.i(TAG, "caso no previsto");
			} else {
				return isoRemove(Algoritmi.desDec(keyEnc, encData));
			}
		}
		return null;
	}

	private static int unsignedToBytes(byte b) {
		return b & 0xFF;
	}

	private byte[] isoRemove(byte[] data) throws Exception {
		int i;
		for (i = data.length - 1; i >= 0; i--) {
			if (data[i] == (byte) 0x80)
				break;
			if (data[i] != 0x00)
				throw new Exception("Padding ISO non presente");
		}
		return AppUtil.getLeft(data, i);
	}

	//metodo che compone l'apdu da mandare alla carta in secure message
	private byte[] sm(byte[] keyEnc, byte[] keyMac, byte[] apdu) throws Exception {
		AppUtil.increment(seq);
		byte[] calcMac = AppUtil.getIsoPad(AppUtil.appendByteArray(seq, AppUtil.getLeft(apdu, 4)));
		byte[] smMac;
		byte[] dataField = new byte[]{};
		byte[] doob;

		if (apdu[4] != 0 && apdu.length > 5) {
			//encript la parte di dati
			byte[] enc = Algoritmi.desEnc(keyEnc, AppUtil.getIsoPad(AppUtil.getSub(apdu, 5, apdu[4])));
			if (apdu[1] % 2 == 0) {
				doob = AppUtil.asn1Tag(AppUtil.appendByteArray(new byte[]{0x001}, enc), 0x87);
			} else
				doob = AppUtil.asn1Tag(enc, 0x85);
			calcMac = AppUtil.appendByteArray(calcMac, doob);
			dataField = AppUtil.appendByteArray(dataField, doob);
		}
		if (apdu.length == 5 || apdu.length == apdu[4] + 6) { // ' se c'è un le
			doob = new byte[]{(byte) 0x97, (byte) 0x01, apdu[apdu.length - 1]};
			calcMac = AppUtil.appendByteArray(calcMac, doob);
			dataField = AppUtil.appendByteArray(dataField, doob);
		}

		smMac = Algoritmi.macEnc(keyMac, AppUtil.getIsoPad(calcMac));
		//Log.i(TAG,"smMac: " + bytesToHex(smMac));
		dataField = AppUtil.appendByteArray(dataField, AppUtil.appendByteArray(new byte[]{(byte) 0x8e, 0x08}, smMac));
		//Log.i(TAG,"dataField: " + bytesToHex(dataField));
		byte[] finale = AppUtil.appendByte(AppUtil.appendByteArray(AppUtil.appendByteArray(AppUtil.getLeft(apdu, 4), new byte[]{(byte) dataField.length}), dataField), (byte) 0x00);
		//Log.i(TAG,"finale: " + bytesToHex(finale));
		return finale;
	}

	private void setIndex(int... argomenti) {
		int tmpIndex = 0;
		int tmpSegno = 0;
		for (int value : argomenti) {
			if (Math.signum(value) < 0) {
				tmpSegno = value & 0xFF;
				tmpIndex += tmpSegno;
			} else
				tmpIndex += value;
			//System.out.print("sommo: " +  tmpIndex+" , ");
		}
		this.index = tmpIndex;
	}
}
