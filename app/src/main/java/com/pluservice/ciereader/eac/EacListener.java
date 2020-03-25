package com.pluservice.ciereader.eac;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.util.Log;

import org.jmrtd.lds.MRZInfo;

import java.io.IOException;

/**
EacListener.java è la classe "ponte" tra l'interfaccia utente e lo strato di logica
che effettua la lettura dei dati dal microprocessore. 
Al suo interno i metodi per gestire la progressione della lettura e la gestione degli errori
**/

public class EacListener implements Runnable {
	
	private Eac eac = null;
	private MRZInfo mrz = null;
	private IsoDep isoDep;
	private Context context;
	
	//costruttore
	public EacListener(IsoDep isoDep, MRZInfo mrz, Context context) {
		this.isoDep = isoDep;
		this.mrz = mrz;
		this.context = context;
	}
	
	@Override
	public void run() { //thread
		try {
			//si apre la connessione
			isoDep.connect();
			isoDep.setTimeout(6000);
			eac = new Eac(isoDep, mrz, context); //istanza della class di logica
			eac.init(); //scambio di chiavi
			eac.readDgs(); //lettura dei datagroups
			
			UserInfo info = eac.parseDg11(); //parsing datagroup 11 - prende i dati personali dell'utente
			sendDataToActivity(info);
			
			Bitmap image = eac.parseDg2();
			sendUserImageToActivity(image);
			
			isoDep.close();//si chiude la connessione IsoDep
		} catch (IOException excp) {
			excp.printStackTrace();
			Log.d("ASD", "Perdita tag NFC");
		} catch (Exception e) {
			e.printStackTrace();
			Log.d("ASD", "EacListener Error : " + e.getMessage());
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