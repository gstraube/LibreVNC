package com.github.librevnc

import android.graphics.Bitmap
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder


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

            val incremental: Byte = if (isIncremental) 1 else 0
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

    fun receiveFramebufferUpdate(bitsPerPixel: Byte, expectedNumberOfRectangles: Short): Bitmap? {
        if (socket.isConnected) {
            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            var expectedNumberOfBytes = 4
            var bytes = ByteArray(expectedNumberOfBytes)
            var bytesRead = inputStream.read(bytes)

            if (bytesRead != expectedNumberOfBytes) {
                warn("Expected $expectedNumberOfBytes but got $bytesRead bytes")

                return null
            }

            val framebufferUpdate = ByteBuffer.wrap(bytes)

            val messageType = framebufferUpdate.get()

            val expectedMessageType = 0
            val actualMessageType = messageType.toInt()
            if (actualMessageType != 0) {
                warn("Wrong message type (expected $expectedMessageType but got $actualMessageType")

                return null
            }

            framebufferUpdate.get() // Padding

            val numberOfRectangles = framebufferUpdate.short

            if (numberOfRectangles != expectedNumberOfRectangles) {
                warn("Wrong number of rectangles (expected $expectedNumberOfRectangles but got$numberOfRectangles")
            }

            info("Received framebuffer update with $numberOfRectangles rectangles")

            expectedNumberOfBytes = 12
            bytes = ByteArray(expectedNumberOfBytes)
            bytesRead = inputStream.read(bytes)
            if (bytesRead != expectedNumberOfBytes) {
                warn("Expected $expectedNumberOfBytes but got $bytesRead bytes")

                return null
            }

            val rectangleHeader = ByteBuffer.wrap(bytes)

            val xPosition = rectangleHeader.short
            val yPosition = rectangleHeader.short
            val width = rectangleHeader.short
            val height = rectangleHeader.short
            val encodingType = rectangleHeader.int

            val props = "xPos: $xPosition, yPos: $yPosition, width: $width, height: $height, encoding: $encodingType"
            info("Received rectangle with ($props)")

            val bitsInAByte = 8
            val pixelDataSize = (width * height * bitsPerPixel) / bitsInAByte

            val allBytes = ByteArray(pixelDataSize)
            val rectBytes = ByteArray((width.toInt() * bitsPerPixel) / bitsInAByte)
            bytesRead = inputStream.read(rectBytes)
            var totalBytes = bytesRead

            var myIndex = 0
            while (totalBytes < pixelDataSize) {
                bytesRead = inputStream.read(rectBytes)
                totalBytes += bytesRead

                System.arraycopy(rectBytes, 0, allBytes, myIndex, bytesRead)
                myIndex += bytesRead
            }

            val rawData = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)

            val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            rawData.rewind()
            bitmap.copyPixelsFromBuffer(rawData)

            return bitmap
        } else {
            warn("Socket is not connected")
        }

        return null
    }
}