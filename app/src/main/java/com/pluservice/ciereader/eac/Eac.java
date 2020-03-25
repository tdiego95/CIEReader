package com.pluservice.ciereader.eac;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.gemalto.jp2.JP2Decoder;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.BACKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.DG2File;
import org.jmrtd.lds.FaceImageInfo;
import org.jmrtd.lds.FaceInfo;
import org.jmrtd.lds.MRZInfo;
import org.jmrtd.lds.LDS;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** effettua la lettura dei dati MRTD dal microprocessore **/

public class Eac {
	
	private IsoDep isoDep = null;
	private MRZInfo mrz = null;
	private Context context = null;
	static byte[] kSessEnc = null;
	static byte[] kSessMac = null;
	static byte[] seq = null;
	public List<Byte> dgList = new ArrayList<Byte>();
	public byte[] efSod = null;
	public byte[] efCVCA = null;
	public byte[] efCom = null;
	public static Map<Integer, byte[]> mappaDg = null;
	private int index = 0;
	private static final String TAG = "m.recupero";

	//costruttore
	public Eac(IsoDep isoDep, MRZInfo mrz, Context context){
		this.isoDep = isoDep;
		this.mrz = mrz;
		this.context = context;
	}
	
	private void sendUpdateToActivity(String info) {
		Intent intent = new Intent();
		intent.setAction("UPDATE_INFO");
		intent.putExtra("update_info", info);
		context.sendBroadcast(intent);
	}

	//metodo iniziale per lo scambio delle chiavi di sessione
	public void init() throws Exception {
		
		if(isoDep == null || mrz == null || context == null) return;
		
		byte[] apduCmd = AppUtil.hexStringToByteArray("00A4040C07A0000002471001"); //select di controllo
		ApduResponse res = new ApduResponse(isoDep.transceive(apduCmd));
		Log.i(TAG,"risposta sw: " + res.getSwHex());
		Log.i(TAG,"risposta full: " + AppUtil.bytesToHex(res.getResponse()));
		if(res.getSwHex().equals("9000")) {
			Log.i(TAG,"INIT BAC AUTHENTICATION:");
			sendUpdateToActivity("Inizio autenticazione BAC");
            // init BAC auth
			byte challenge[] = AppUtil.hexStringToByteArray("0084000008");
			ApduResponse apduRes = new ApduResponse(isoDep.transceive(challenge));
			if(!apduRes.getSwHex().equals("9000")) {
				Log.i(TAG,"Errore nella richiesta di challenge [apdu]:0084000008");
                //Progressione.testoErrore +=  "Errore nella richiesta di challenge";
				throw new Exception("Errore nella richiesta di challenge [apdu]:0084000008");
			}

			byte[] rndMrtd = apduRes.getResponse();
			byte[] birth = mrz.getDateOfBirth().getBytes();
			byte[] expire = mrz.getDateOfExpiry().getBytes();

			//concateno i dati: numero documento e le due date
			byte[] pn = mrz.getDocumentNumber().getBytes();
			byte seedPartPn[] = AppUtil.appendByte(pn, AppUtil.checkdigit(pn));
			byte seedPartBirth[] = AppUtil.appendByte(birth, AppUtil.checkdigit(birth));
			byte seedPartExpire[] = AppUtil.appendByte(expire, AppUtil.checkdigit(expire));
			
			byte[] bacSeedData = AppUtil.appendByteArray(seedPartPn, seedPartBirth);
			bacSeedData = AppUtil.appendByteArray(bacSeedData, seedPartExpire);//I00000000666011111512030
			byte[] bacEnc = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(AppUtil.getLeft(AppUtil.getSha1(bacSeedData), 16), new byte[]{(byte)0x00,0x00,0x00,0x01})),16);
			byte[] bacMac = AppUtil.getLeft(AppUtil.getSha1(AppUtil.appendByteArray(AppUtil.getLeft(AppUtil.getSha1(bacSeedData), 16), new byte[]{(byte)0x00,0x00,0x00,0x02})),16);
			
			//genero i byte[] random
			byte[] rndIs1 = new byte[8];
			AppUtil.getRandomByte(rndIs1);
			
			byte[] kIs = new byte[16];
			AppUtil.getRandomByte(kIs);
			
			byte[] eIs1 = Algoritmi.desEnc(bacEnc, AppUtil.appendByteArray(AppUtil.appendByteArray(rndIs1, rndMrtd), kIs));//32byte
			byte[] eisMac = Algoritmi.macEnc(bacMac, AppUtil.getIsoPad(eIs1)); //8byte
			
			//pronto per la mutua auth
			byte apduMutaAuth[] = AppUtil.appendByteArray(eIs1,eisMac);//46byte
			byte[] apduMutuaAutenticazione = AppUtil.appendByte(AppUtil.appendByteArray(AppUtil.appendByteArray(new byte[]{0x00,(byte) 0x82,0x00,0x00,0x28},eIs1),eisMac),(byte)0x28);
			ApduResponse respMutaAuth = new ApduResponse(isoDep.transceive(apduMutuaAutenticazione));//11byte
			if(!respMutaAuth.getSwHex().equals("9000")){
				Log.i(TAG,"Errore sulla mutua auth BAC " + respMutaAuth.getSwHex());
                //Progressione.testoErrore +=  "Errore durante la procedura di autenticazione BAC! Ripetere la scansione. ";
                //Progressione.erroreBloccante = true;
				sendUpdateToActivity("-- ERRORE AUTENTICAZIONE. I DATI SONO ERRATI. RISCANSIONARE IL CODICE MRZ --");
                throw new Exception("Errore durante la procedura di autenticazione BAC! Ripetere la scansione. " +  respMutaAuth.getSwHex());
			}

			byte[] kIsMac =  Algoritmi.macEnc(bacMac, AppUtil.getIsoPad(AppUtil.getLeft(respMutaAuth.getResponse(),32)));
			byte[] kIsMac2 = AppUtil.getRight(respMutaAuth.getResponse(),8);
			if(!Arrays.equals(kIsMac,kIsMac2)) {
                //Progressione.testoErrore +=  "Errore sulla auth dell'MRTD!!!.";
                //Progressione.erroreBloccante = true;
                throw new Exception("Errore sulla auth dell'MRTD!!!");
            }
			byte[] decResp = Algoritmi.desDec(bacEnc, AppUtil.getLeft(respMutaAuth.getResponse(),32));
			byte[] kMrtd = AppUtil.getRight(decResp,16);
			byte[] kSeed = AppUtil.stringXor(kIs, kMrtd);
			
			//parsing chiavi di sessione
			kSessMac = AppUtil.getLeft(AppUtil.getSha1( AppUtil.appendByteArray(kSeed,new byte[]{0x00,0x00,0x00,0x02})),16);
			kSessEnc = AppUtil.getLeft(AppUtil.getSha1( AppUtil.appendByteArray(kSeed,new byte[]{0x00,0x00,0x00,0x01})),16);

			byte[] tmp = AppUtil.getSub(decResp, 4, 4);
			byte[] tmp2 = AppUtil.getSub(decResp, 12, 4);
			seq = AppUtil.appendByteArray(tmp,tmp2);
			Log.i(TAG,"END BAC AUTHENTICATION:");
			sendUpdateToActivity("Autenticazione BAC completata");
		} else {
			Log.i(TAG,"protocolla SAC");
		}
	}

	//recupero la struttura dei dg, la conservo dentro una mappa
	public void readDgs()throws Exception {

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
					dgNum = new Integer(1);
					break;
				case 0x75:
					dgNum = new Integer(2);
					break;
				case 0x6b:
					dgNum = new Integer(11);
					break;
				case 0x6e:
					dgNum = new Integer(14);
					break;
				case 0x77:
					dgNum = new Integer(29);
					break;
			}

			if (dgNum != 0)
				mappaDg.put(dgNum, leggiDg(dgNum));

			if(!mappaDg.containsKey(new Integer(29)))
				mappaDg.put(new Integer(29), leggiDg(29));
		}
		
		sendUpdateToActivity("Lettura data groups completata");
	}

    public void parseDg1() throws Exception {
		//D IL DG1 TORNA L'MRZ, UTILIZZARE IN CASO DI BISOGNO ESTRAZIONE SESSO E NAZIONALITA
	}

	public UserInfo parseDg11() throws Exception {
		sendUpdateToActivity("Inizio lettura dati personali");
		if(mappaDg.containsKey(new Integer(11))) {
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

    public Bitmap parseDg2() throws Exception {
	
		sendUpdateToActivity("Inizio lettura foto in corso");
		Bitmap bitmap = null;
		
		try {
			BACKeySpec bacKey = new BACKey(mrz.getDocumentNumber(), mrz.getDateOfBirth(), mrz.getDateOfExpiry());
			DG2File dg2File;
			
			CardService cardService = CardService.getInstance(isoDep);
			cardService.open();
			
			PassportService service = new PassportService(cardService);
			service.open();
			
			service.sendSelectApplet(true);
			
			service.doBAC(bacKey);
			
			LDS lds = new LDS();
			
			CardFileInputStream dg2In = service.getInputStream(PassportService.EF_DG2);
			lds.add(PassportService.EF_DG2, dg2In, dg2In.getLength());
			dg2File = lds.getDG2File();
			
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
				
				bitmap = new JP2Decoder(buffer).decode();
				
				sendUpdateToActivity("Lettura foto completata con successo");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			sendUpdateToActivity("-- ERRORE LETTURA FOTO --");
		}
	
		return bitmap;
	}
	
	//metodo per la lettura dei dg
	//numDg: il numero del datagroup da leggere
	public byte[] leggiDg(int numDg) throws Exception {
		Log.i(TAG, "Leggo il dg: " + numDg);

		byte[] data = new byte[0];
		byte somma = (byte) ((byte) numDg + (byte) 0x80);//-126
		String hex = AppUtil.bytesToHex(new byte[]{somma});//82
		byte[] appo = AppUtil.hexStringToByteArray("0cb0" + hex + "0006");//. ToString("X2") + " 00 06")
		byte[] apdu = sm(kSessEnc, kSessMac, appo);// ' read DG 
		ApduResponse respDg = new ApduResponse(isoDep.transceive(apdu));
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
			ApduResponse respDg2 = new ApduResponse(isoDep.transceive(apduDg));// ' read DG
			if (!respDg2.getSwHex().equals("9000")) {
				Log.i(TAG, "Errore nella lettura del DG" + numDg + " codice errore: " + respDg2.getSwHex());
				throw new Exception("Errore nella lettura del DG" + numDg + " codice errore: " + respDg2.getSwHex());
			}
			byte[] chunk = respSM(kSessEnc, kSessMac, respDg2.getResponse());

			data = AppUtil.appendByteArray(data, chunk);
		}
		
		/* LOG */
		String x = "";
		for(int i=0; i < data.length; i++) {
			x += " " + data[i];
		}
		Log.d("ASD", "data byte : " + x);
		Log.d("ASD", "data hex : " + AppUtil.bytesToHex(data));

		return data;
	}

	public  byte[] respSM(byte[] keyEnc, byte[] keySig, byte[] resp) throws Exception {
         return respSM(keyEnc, keySig, resp,  false);
     }

	//metodo per la gestione della risposta Secure Message
	public byte[] respSM(byte[] keyEnc, byte[] keySig, byte[] resp, boolean odd) throws Exception {

		AppUtil.increment(seq);
		// cerco il tag 87
		setIndex(0);
		byte[] encData = null;
		byte[] encObj = null;
		byte[] dataObj = null;

		do {
			if (Byte.compare(resp[index], (byte) 0x99) == 0) {
				if (Byte.compare(resp[index + 1], (byte) 0x02) != 0)
					throw new Exception("Errore nella verifica del SM - lunghezza del DataObject");
				dataObj = AppUtil.getSub(resp, index, 4);
				setIndex(index, 4);//index += 4;
				continue;
			}

			if (Byte.compare(resp[index], (byte) 0x8e) == 0) {
				byte[] calcMac = Algoritmi.macEnc(keySig, AppUtil.getIsoPad(AppUtil.appendByteArray(AppUtil.appendByteArray(seq, encObj), dataObj)));
				setIndex(index, 1);//index++;
				if (Byte.compare(resp[index], (byte) 0x08) != 0)
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
				continue;
			} else if (Byte.compare(resp[index], (byte) 0x85) == 0) {
				if (Byte.compare(resp[index + 1], (byte) 0x80) > 0) {
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
				continue;
			} else {
				throw new Exception("Tag non previsto nella risposta in SM");
			}
			//index = index + resp[index + 1] + 1;
		} while (index < resp.length);

		if (encData != null) {
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

	public static int unsignedToBytes(byte b) {
		return b & 0xFF;
	}
	
	public byte[] isoRemove(byte[] data) throws Exception {
		int i;
		for (i = data.length - 1; i >= 0; i--)
		{
			if (data[i] == (byte)0x80)
				break;
			if (data[i] != 0x00)
				throw new Exception("Padding ISO non presente");
		}
		return AppUtil.getLeft(data, i);
	}
	
	//metodo che compone l'apdu da mandare alla carta in secure message
	private byte[] sm(byte[] keyEnc, byte[] keyMac, byte[] apdu) throws Exception {
		AppUtil.increment(seq);
		byte[] calcMac = AppUtil.getIsoPad(AppUtil.appendByteArray(seq, AppUtil.getLeft(apdu,4)));
		byte[] smMac;
		byte[] dataField = null;
		byte[] doob;

		if(apdu[4] != 0 && apdu.length > 5){
			//encript la parte di dati
			byte[] enc = Algoritmi.desEnc(keyEnc, AppUtil.getIsoPad(AppUtil.getSub(apdu, 5, apdu[4])));
			if(apdu[1] %2 == 0){
				doob = AppUtil.asn1Tag(AppUtil.appendByteArray(new byte[]{0x001},enc),0x87);
			}
			else
				doob = AppUtil.asn1Tag(enc, 0x85);
			calcMac = AppUtil.appendByteArray(calcMac,doob);
			dataField = AppUtil.appendByteArray(dataField,doob);
        }
        if (apdu.length == 5 || apdu.length == apdu[4] + 6)
        { // ' se c'è un le
            doob = new byte[] {(byte) 0x97,(byte) 0x01, apdu[apdu.length - 1]};
            calcMac = AppUtil.appendByteArray(calcMac,doob);
            if(dataField == null)
            	dataField = doob.clone();
            else
            	dataField = AppUtil.appendByteArray(dataField,doob);
        }

        smMac = Algoritmi.macEnc(keyMac, AppUtil.getIsoPad(calcMac));
        //Log.i(TAG,"smMac: " + bytesToHex(smMac));
        dataField = AppUtil.appendByteArray(dataField, AppUtil.appendByteArray(new byte[] { (byte)0x8e, 0x08 },smMac));
        //Log.i(TAG,"dataField: " + bytesToHex(dataField));
        byte[] finale = AppUtil.appendByte(AppUtil.appendByteArray(AppUtil.appendByteArray(AppUtil.getLeft(apdu, 4),new byte[]{(byte)dataField.length}),dataField),(byte)0x00);
        //Log.i(TAG,"finale: " + bytesToHex(finale));
        return finale;
	}

	public List<Byte> getDgList() {
		return dgList;
	}
	public void setDgList(List<Byte> dgList) {
		this.dgList = dgList;
	}
	public byte[] getEfSod() {
		return efSod;
	}
	public void setEfSod(byte[] efSod) {
		this.efSod = efSod;
	}
	public byte[] getEfCVCA() {
		return efCVCA;
	}
	public void setEfCVCA(byte[] efCVCA) {
		this.efCVCA = efCVCA;
	}
	public byte[] getEfCom() {
		return efCom;
	}
	public void setEfCom(byte[] efCom) {
		this.efCom = efCom;
	}
	public int getIndex() {
		return index;
	}

	public void setIndex(int... argomenti) {
		int tmpIndex = 0;
		int tmpSegno = 0;
		for(int i=0;i<argomenti.length;i++){
			if(Math.signum(argomenti[i]) < 0){
				tmpSegno = argomenti[i] & 0xFF;
				tmpIndex += tmpSegno;
			}
			else
				tmpIndex += argomenti[i];
			//System.out.print("sommo: " +  tmpIndex+" , ");
		}
		this.index = tmpIndex;
	}
}
