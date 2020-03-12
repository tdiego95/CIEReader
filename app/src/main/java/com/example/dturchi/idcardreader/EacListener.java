package com.example.dturchi.idcardreader;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;

/**
EacListener.java è la classe "ponte" tra l'interfaccia utente e lo strato di logica
che effettua la lettura dei dati dal microprocessore. 
Al suo interno i metodi per gestire la progressione della lettura e la gestione degli errori
**/

public class EacListener implements Runnable {
	
	private Eac eac = null;
	private IsoDep isoDep;
	
	//costruttore
	public EacListener(IsoDep isoDep) {
		this.isoDep = isoDep;
	}

	@Override
	public void run() { //thread
		try {
			//si apre la connessione
			isoDep.connect();
			isoDep.setTimeout(6000);
			eac = new Eac(isoDep);//istanza della class di logica
			eac.init();//scambio di chiavi
			eac.readDgs();//lettura dei datagroups
			//eac.parseDg1();//parsing datagroup 1 - prende la stringa MRZ
			eac.parseDg11();//parsing datagroup 11 - prende i dati personali dell'utente
			//eac.parseDg2();//parsing datagroup 2 - prende la foto
			isoDep.close();//si chiude la connessione IsoDep
		} catch(IOException excp) {
			excp.printStackTrace();
			Log.d("ASD", "Perdita tag NFC");
		} catch (Exception e) {
			e.printStackTrace();
			Log.d("ASD", "EacListener Error : " + e.getMessage());
		}
	}
}