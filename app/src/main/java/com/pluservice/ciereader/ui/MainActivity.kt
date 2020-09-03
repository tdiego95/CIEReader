package com.pluservice.ciereader.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.pluservice.ciereader.R
import com.pluservice.ciereader.eac.EacListener
import com.pluservice.ciereader.eac.UserInfo
import com.pluservice.ciereader.mrz.CaptureActivity
import com.pluservice.ciereader.mrz.CaptureActivity.MRZ_RESULT
import com.pluservice.ciereader.mrz.PreferencesActivity
import com.pluservice.ciereader.neptune.ICoupler
import com.pluservice.ciereader.neptune.NeptuneCoupler
import com.pluservice.ciereader.neptune.NeptuneReader


import kotlinx.android.synthetic.main.activity_main.*

import org.jmrtd.lds.MRZInfo
import org.spongycastle.jce.provider.BouncyCastleProvider

import java.security.Security

class MainActivity : AppCompatActivity(), ReaderCallback {

    private val REQUEST_PERMISSION_CAMERA = 1
    private var REQUEST_SCAN_MRZ = 99
    private var USER_INFO_FILTER = "USER_INFO"
    private var USER_IMAGE_FILTER = "USER_IMAGE"
    private var UPDATE_INFO_FILTER = "UPDATE_INFO"

    private var nfcAdapter: NfcAdapter? = null
    private var mrzInfo: MRZInfo? = null

    private var isNfcSupported = false

    var coupler: NeptuneCoupler? = null

    val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                USER_INFO_FILTER -> { //QUI MI ARRIANO I DATI PERSONALI LETTI DALLA CARTA
                    val userInfo = intent.getSerializableExtra("user_info") as UserInfo
                    txtUserInfo.text = "Dati utente :\n" +
                            userInfo.fullName + "\n" +
                            userInfo.codiceFiscale + "\n" +
                            userInfo.birthDate + "\n" +
                            userInfo.birthPlace + "\n" +
                            userInfo.residence
                }
                USER_IMAGE_FILTER -> { //QUI MI ARRIVA LA FOTO LETTA DALLA CARTA
                    val userImage = intent.getParcelableExtra<Bitmap>("user_image")
                    imgUser.setImageBitmap(userImage)
                    imgUser.visibility = View.VISIBLE
                    txtLoadingInfo.text = "-- Dati recuperati, operazione completata, è possibile rimuovere la carta --"
                    pbLoadingData.visibility = View.GONE
                    txtNfcReady.visibility = View.GONE
                }
                UPDATE_INFO_FILTER -> { //QUI MI ARRIVANO GLI AGGIORNAMENTI PER EVENTUALI UPDATE GRAFICI
                    val updateInfo = intent.getSerializableExtra("update_info") as String
                    pbLoadingData.visibility = View.VISIBLE
                    txtLoadingInfo.visibility = View.VISIBLE
                    txtLoadingInfo.text = txtLoadingInfo.text.toString() + "\n" + updateInfo

                    if (updateInfo.contains("ERROR")) {
                        pbLoadingData.visibility = View.GONE
                        txtNfcReady.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Security.insertProviderAt(BouncyCastleProvider(), 1)

        btnScanMrz.setOnClickListener { openScanMrz() }
        btnSettings.setOnClickListener { startActivity(Intent(this, PreferencesActivity::class.java)) }

        initializeNFC()

        registerReceiver()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(USER_INFO_FILTER)
        intentFilter.addAction(USER_IMAGE_FILTER)
        intentFilter.addAction(UPDATE_INFO_FILTER)
        registerReceiver(broadCastReceiver, intentFilter)
    }

    private fun initializeNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) { //If nfcAdapter is null i close application
            isNfcSupported = true
            if (!(nfcAdapter!!.isEnabled())) { //If NFC is not enabled i prompt user to settings
                showNfcSettings()
            }
        } else {
            isNfcSupported = false
            Toast.makeText(this, "NFC NON SUPPORTATO", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNfcSettings() {
        if (isNfcSupported) {
            Toast.makeText(this, "Please enable NFC first", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            startActivity(intent)
        }
    }

    private fun startNfcListening() {
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            Log.d("ASD", "Start NFC listening")
            nfcAdapter!!.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        } else {
            showNfcSettings()
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        Log.d("ASD", "CHIP RILEVATO")
        try {
            val isoDep: IsoDep = IsoDep.get(tag) //l'oggetto IsoDep implementa la specifica  ISO-DEP (ISO 14443-4) per le operazioni di I/O verso il chip
            val eacListener = EacListener(isoDep, null, mrzInfo, this)
            eacListener.run()

        } catch (ex: Exception) {
            Log.d("Error decoding tag : ", ex.message)
            ex.printStackTrace()
        }
    }

    private fun openScanMrz() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
        } else {
            txtLoadingInfo.text = ""
            txtLoadingInfo.visibility = View.GONE
            txtNfcReady.visibility = View.GONE
            txtMrzInfo.text = "Dati MRZ :"
            txtUserInfo.text = "Dati utente :"
            imgUser.visibility = View.GONE
            startActivityForResult(Intent(this, CaptureActivity::class.java), REQUEST_SCAN_MRZ)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode.equals(REQUEST_SCAN_MRZ) && resultCode.equals(Activity.RESULT_OK)) {
            val result = intent?.getSerializableExtra(MRZ_RESULT)
            mrzInfo = MRZInfo(result.toString())
            if (mrzInfo != null) {
                txtMrzInfo.text = "Dati MRZ :\n" +
                        "doc number : " + mrzInfo!!.documentNumber + "\n" +
                        "birth date : " + mrzInfo!!.dateOfBirth + "\n" +
                        "expiry date : " + mrzInfo!!.dateOfExpiry
                txtNfcReady.visibility = View.VISIBLE

                if (isNfcSupported) {
                    startNfcListening()
                } else {
                    val coupler: ICoupler = NeptuneCoupler(applicationContext)
                    val detected: Boolean = NeptuneReader().execute(coupler).get()
                    if (detected) {
                        val eacListener = EacListener(null, coupler, mrzInfo, this)
                        eacListener.run()
                    }
                }
            } else {
                Toast.makeText(this, "MRZ null", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermission() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSION_CAMERA -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_LONG).show()
                    openScanMrz()
                } else {
                    Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
}