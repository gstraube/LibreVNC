package com.github.librevnc

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.warn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class VncClient(host: String, port: Int) : AnkoLogger {

    private var socket: Socket = Socket(host, port)

    fun performHandshake(): Boolean {
        if (socket.isConnected) {
            val inputStream = socket.getInputStream()
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val out = PrintWriter(socket.getOutputStream(), true)

            val serverProtocolVersion = bufferedReader.readLine()
            val supportedProtocolVersion = "RFB 003.008"
            if (supportedProtocolVersion == serverProtocolVersion) {
                out.write("$supportedProtocolVersion\n")
                out.flush()
            } else {
                warn("Unsupported server protocol version: $serverProtocolVersion")

                return false
            }

            val serverSecurityType = ByteArray(2)
            inputStream.read(serverSecurityType)
            val supportedSecurityType = 1
            if (supportedSecurityType == serverSecurityType[1].toInt()) {
                out.write(supportedSecurityType)
                out.flush()
            } else {
                warn("Unsupported server security type: $serverSecurityType")

                return false
            }

            return true
        } else {
            warn("Socket is not connected")

            return false
        }
    }
}