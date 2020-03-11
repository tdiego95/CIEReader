package com.example.dturchi.idcardreader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast

class MainActivity : AppCompatActivity(), ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeNFC()
    }

    private fun initializeNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) finish() //If nfcAdapter is null i close application
        if (!(nfcAdapter!!.isEnabled())) showNfcSettings() //If NFC is not enabled i prompt user to settings
    }

    private fun showNfcSettings() {
        Toast.makeText(this, "Please enable NFC first", Toast.LENGTH_SHORT).show()
        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
        startActivity(intent)
    }

    override fun onTagDiscovered(tag: Tag) {
        Log.d("ASD", "CHIP RILEVATO")
        try {
            val isoDep = IsoDep.get(tag) //l'oggetto IsoDep implementa la specifica  ISO-DEP (ISO 14443-4) per le operazioni di I/O verso il chip

            //alcuni controlli sul flusso
            //if (this.mSelectedId === -2 && Eac.mrz != null) { //se tutto OK, viene avviato il thread che legge i dati
                val eacListener = EacListener(isoDep)
                eacListener.run()
            //} else {
            //    Log.d("ASD", "Prima devi eseguire la scansione dell' MRZ, dopo avvicinare la carta al dispositivo.")
            //}
        } catch (ex: Exception) {
            Log.d("Error decoding tag : ", ex.message)
            ex.printStackTrace()
        }
    }

    fun EnableNFC(view: View?) {
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            Log.d("ASD", "Start NFC listening")
            nfcAdapter!!.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        } else {
            showNfcSettings()
        }
    }

    fun DisableNFC(view: View?) {
        if (nfcAdapter != null) {
            Log.d("ASD", "Stop NFC listening")
            nfcAdapter!!.disableReaderMode(this)
        }
    }
}