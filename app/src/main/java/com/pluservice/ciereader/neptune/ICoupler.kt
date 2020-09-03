package com.pluservice.ciereader.neptune

import java.io.IOException

interface ICoupler {

    @Throws(Exception::class)
    fun open()

    @Throws(Exception::class)
    fun close()

    @Throws(Exception::class)
    fun detect(): Boolean

    fun isIsoDep(): Boolean

    @Throws(IOException::class)
    fun isoDepTransceive(apduCommand: ByteArray?): ByteArray?
}