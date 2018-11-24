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

    fun setEncodings() {
        if (socket.isConnected) {
            val outputStream = socket.getOutputStream()

            val setEncodingMessage = ByteBuffer.allocate(8)
            val messageType: Byte = 2
            setEncodingMessage.put(messageType)
            val padding: Byte = 0
            setEncodingMessage.put(padding)
            val numberOfEncodings: Short = 1
            setEncodingMessage.putShort(numberOfEncodings)
            val rawEncodingType = 0
            setEncodingMessage.putInt(rawEncodingType)

            outputStream.write(setEncodingMessage.array())
            outputStream.flush()
        } else {
            warn("Socket is not connected")
        }
    }

    fun sendFramebufferUpdateRequest(width: Short, height: Short, isIncremental: Boolean = true) {
        if (socket.isConnected) {
            val outputStream = socket.getOutputStream()

            val framebufferUpdateRequest = ByteBuffer.allocate(10)
            val messageType: Byte = 3
            framebufferUpdateRequest.put(messageType)

            val incremental: Byte =  if (isIncremental) 1 else 0
            framebufferUpdateRequest.put(incremental)

            val xPosition: Short = 0
            framebufferUpdateRequest.putShort(xPosition)

            val yPosition: Short = 0
            framebufferUpdateRequest.putShort(yPosition)

            framebufferUpdateRequest.putShort(width)
            framebufferUpdateRequest.putShort(height)

            outputStream.write(framebufferUpdateRequest.array())
            outputStream.flush()
        } else {
            warn("Socket is not connected")
        }
    }
}