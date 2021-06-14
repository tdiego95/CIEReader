package com.pluservice.ciereader.eac;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.pluservice.ciereader.neptune.ICoupler;

import org.jmrtd.lds.icao.MRZInfo;

import java.io.IOException;

/**
EacListener.java è la classe "ponte" tra l'interfaccia utente e lo strato di logica
che effettua la lettura dei dati dal microprocessore. 
Al suo interno i metodi per gestire la progressione della lettura e la gestione degli errori
**/

public class EacListener implements Runnable {

	private MRZInfo mrz;
	private String can;
	private IsoDep isoDep;
	private ICoupler coupler;
	private Context context;
	
	//costruttore
	public EacListener(IsoDep isoDep, ICoupler coupler, MRZInfo mrz, String can, Context context) {
		this.isoDep = isoDep;
		this.coupler = coupler;
		this.mrz = mrz;
		this.can = can;
		this.context = context;
	}
	
	@Override
	public void run() { //thread
		try {
			if (isoDep != null) {
				//si apre la connessione
				isoDep.connect();
				isoDep.setTimeout(6000);
			}

			Eac eac = new Eac(isoDep, coupler, mrz, can, context); //istanza della class di logica

			// --- REGION AUTH AND DATA READ HOME-MADE ---
			if (eac.isSac())
				eac.paceAuthentication();
			else
				eac.bacAuthentication();

			eac.readDgs(); //lettura dei datagroups
			UserInfo info = eac.parseDg11(); //parsing datagroup 11 - prende i dati personali dell'utente
			sendDataToActivity(info);
			// --- END REGION ---

			// --- REGION MRTD ---
			/*D
			PassportService service = eac.auth();

			UserInfo userInfo = eac.readDg11(service);
			sendDataToActivity(userInfo);

			Bitmap photo = eac.readDg2(service);
			sendUserImageToActivity(photo);*/
			// --- END REGION ---

			if (isoDep != null) {
				isoDep.close(); //si chiude la connessione IsoDep
			}

		} catch (IOException ioEx) {
			ioEx.printStackTrace();
			Log.e("ASD", "Perdita tag NFC");
		} catch (Exception ex) {
			ex.printStackTrace();
			Log.e("ASD", "EacListener Error : " + ex.getMessage());
		}
	}
	
	private void sendUserImageToActivity(Bitmap bitmap) {
		Intent intent = new Intent();
		intent.setAction("USER_IMAGE");
		intent.putExtra("user_image", bitmap);
		context.sendBroadcast(intent);
	}
	
	private void sendDataToActivity(UserInfo userInfo) {
		Intent intent = new Intent();
		intent.setAction("USER_INFO");
		intent.putExtra("user_info", userInfo);
		context.sendBroadcast(intent);
	}
}