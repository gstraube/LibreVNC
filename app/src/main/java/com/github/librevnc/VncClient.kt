package com.github.librevnc

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.warn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.ByteBuffer

class VncClient(host: String, port: Int) : AnkoLogger {

    private var socket: Socket = Socket(host, port)

    fun performHandshake(): Boolean {
        if (socket.isConnected) {
            val inputStream = socket.getInputStream()
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val outputStream = socket.getOutputStream()
            val out = PrintWriter(outputStream, true)

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
            var bytesRead = inputStream.read(serverSecurityType)
            val supportedSecurityType = 1
            if (bytesRead == 2 && supportedSecurityType == serverSecurityType[1].toInt()) {
                outputStream.write(supportedSecurityType)
                out.flush()
            } else {
                warn("Unsupported server security type: $serverSecurityType")

                return false
            }

            val authenticationResult = ByteArray(4)
            bytesRead = inputStream.read(authenticationResult)
            inputStream.read(authenticationResult)

            if (bytesRead == 4 && ByteBuffer.wrap(authenticationResult).int == 0) {
                return true
            } else {
                warn("Authentication failed")
            }

            return false
        } else {
            warn("Socket is not connected")

            return false
        }
    }

    fun initialize(shareAccess: Boolean = true): ServerInitMessage? {
        if (socket.isConnected) {
            val shareAccessFlag = if (shareAccess) 1 else 0

            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()

            outputStream.write(shareAccessFlag)
            outputStream.flush()

            val expectedNumberOfBytes = 24
            val serverInitMessage = ByteArray(expectedNumberOfBytes)
            var bytesRead = inputStream.read(serverInitMessage)

            if (bytesRead == expectedNumberOfBytes) {
                val byteBuffer = ByteBuffer.wrap(serverInitMessage)

                val frameBufferWidth = byteBuffer.short
                val frameBufferHeight = byteBuffer.short

                val bitsPerPixel = byteBuffer.get()
                val depth = byteBuffer.get()
                val bigEndianFlag = byteBuffer.get()
                val trueColorFlag = byteBuffer.get()

                val redMax = byteBuffer.short
                val greenMax = byteBuffer.short
                val blueMax = byteBuffer.short

                val redShift = byteBuffer.get()
                val greenShift = byteBuffer.get()
                val blueShift = byteBuffer.get()

                // Padding
                byteBuffer.get()
                byteBuffer.get()
                byteBuffer.get()

                val desktopNameLength = byteBuffer.int
                val desktopName = ByteArray(desktopNameLength)
                bytesRead = inputStream.read(desktopName)
                return if (bytesRead == desktopNameLength) {
                    ServerInitMessage(
                        frameBufferWidth, frameBufferHeight, bitsPerPixel, depth, bigEndianFlag,
                        trueColorFlag, redMax, greenMax, blueMax, redShift, greenShift, blueShift, String(desktopName)
                    )
                } else {
                    warn("Could not read desktop name")

                    null
                }
            } else {
                warn("Could not read server initialization message")

                return null
            }
        } else {
            warn("Socket is not connected")

            return null
        }
    }
}