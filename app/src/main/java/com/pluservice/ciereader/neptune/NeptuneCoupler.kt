package com.pluservice.ciereader.neptune

import android.content.Context
import android.util.Log
import com.pax.dal.IPicc
import com.pax.dal.entity.EDetectMode
import com.pax.dal.entity.EPiccType
import com.pax.dal.entity.PiccCardInfo
import com.pax.dal.exceptions.PiccDevException
import com.pax.neptunelite.api.NeptuneLiteUser
import java.io.IOException

class NeptuneCoupler(context: Context) : ICoupler {

    var tag: PiccCardInfo? = null
    private var reader: IPicc? = null

    init {
        reader = NeptuneLiteUser.getInstance().getDal(context).getPicc(EPiccType.INTERNAL)
    }

    override fun isoDepTransceive(apduCommand: ByteArray?): ByteArray? {
        return if (isIsoDep()) {
            try {
                reader!!.isoCommand(0.toByte(), apduCommand)
            } catch (e: PiccDevException) {
                throw IOException(e.message)
            }
        } else null
    }

    override fun detect(): Boolean {
        tag = reader!!.detect(EDetectMode.ONLY_A) //ISO14443_AB //ONLY_A //EMV_AB

        return tag != null
    }

    override fun close() {
        reader?.close()
    }

    override fun isIsoDep(): Boolean {
        return true
        //if (tag == null) return false
        //return tag!!.other[0] == 15.toByte()
    }

    override fun open() {
        reader?.open()
    }
}