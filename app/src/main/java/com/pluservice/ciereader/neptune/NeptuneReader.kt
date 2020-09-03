package com.pluservice.ciereader.neptune

import android.os.AsyncTask
import android.util.Log

class NeptuneReader : AsyncTask<ICoupler, Void, Boolean>() {

    var coupler: ICoupler? = null

    override fun doInBackground(vararg params: ICoupler?): Boolean {

        var detected = false
        try {
            coupler = params[0]
            coupler?.open()

            while (!detected) {
                Log.i("ASD", "Try detecting tag...")
                detected = coupler?.detect()!!
                Thread.sleep(1000)
            }

            if (detected) {
                Log.i("ASD", "Detector - Tag Detected")
            } else {
                Log.i("ASD", "Detector - Thread timeout")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ASD", "ex : " + e.message)
        }

        return detected
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)

        coupler?.close()
    }
}