package com.github.librevnc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.nio.ByteBuffer

class VncClientTest {

    @Test
    fun performHandshake() {
        val serverSocket = ServerSocket(19215)

        val vncClient = VncClient("localhost", 19215)
        var wasHandshakeSuccessful = false
        val thread = Thread(Runnable {
            wasHandshakeSuccessful = vncClient.performHandshake()
        })

        thread.start()

        val socket = serverSocket.accept()
        val outputStream = socket.getOutputStream()
        val out = PrintWriter(outputStream)

        val inputStream = socket.getInputStream()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        val expectedProtocolVersion = "RFB 003.008"
        out.write("$expectedProtocolVersion\n")
        out.flush()

        val protocolVersion = bufferedReader.readLine()

        val securityVersion = byteArrayOf(1, 1)
        outputStream.write(securityVersion)
        outputStream.flush()

        val clientSecurityType = ByteArray(1)
        inputStream.read(clientSecurityType)

        val authenticationResult = ByteBuffer.allocate(4).putInt(0).array()
        outputStream.write(authenticationResult)
        outputStream.flush()

        socket.close()
        serverSocket.close()
        thread.join()

        assertEquals(expectedProtocolVersion, protocolVersion)
        assertEquals(1, clientSecurityType[0].toInt())
        assertTrue(wasHandshakeSuccessful)
    }

    @Test
    fun initialize() {
        val serverSocket = ServerSocket(19215)

        val vncClient = VncClient("localhost", 19215)
        var serverInitMessage: ServerInitMessage? = null
        val thread = Thread(Runnable {
            serverInitMessage = vncClient.initialize()
        })

        thread.start()

        val socket = serverSocket.accept()
        val outputStream = socket.getOutputStream()
        val inputStream = socket.getInputStream()

        val shareAccessFlag = ByteArray(1)
        val bytesRead = inputStream.read(shareAccessFlag)
        assertEquals(1, bytesRead)

        val buffer = ByteBuffer.allocate(38)
        val frameBufferWidth: Short = 1920
        buffer.putShort(frameBufferWidth)
        val frameBufferHeight: Short = 1080
        buffer.putShort(frameBufferHeight)

        val bitsPerPixel: Byte = 32
        buffer.put(bitsPerPixel)
        val depth: Byte = 24
        buffer.put(depth) // Depth
        val bigEndianFlag: Byte = 0
        buffer.put(bigEndianFlag)
        val trueColorFlag: Byte = 1
        buffer.put(trueColorFlag)

        val redMax: Short = 255
        buffer.putShort(redMax)
        val greenMax: Short = 255
        buffer.putShort(greenMax)
        val blueMax: Short = 255
        buffer.putShort(blueMax)

        val redShift: Byte = 16
        buffer.put(redShift)
        val greenShift: Byte = 8
        buffer.put(greenShift)
        val blueShift: Byte = 0
        buffer.put(blueShift)

        // Padding
        buffer.put(0)
        buffer.put(0)
        buffer.put(0)

        val desktopName = "desktop_name:0"
        val desktopNameAsArray = desktopName.toByteArray(Charsets.US_ASCII)
        buffer.putInt(desktopNameAsArray.size) // Name length
        buffer.put(desktopNameAsArray) // Name

        outputStream.write(buffer.array())
        outputStream.flush()

        socket.close()
        serverSocket.close()
        thread.join()

        assertEquals(1, shareAccessFlag[0].toInt())
        assertTrue(serverInitMessage != null)
        assertEquals(frameBufferWidth, serverInitMessage!!.frameBufferWidth)
        assertEquals(frameBufferHeight, serverInitMessage!!.frameBufferHeight)
        assertEquals(bitsPerPixel, serverInitMessage!!.bitsPerPixel)
        assertEquals(depth, serverInitMessage!!.depth)
        assertEquals(bigEndianFlag, serverInitMessage!!.bigEndianFlag)
        assertEquals(trueColorFlag, serverInitMessage!!.trueColorFlag)
        assertEquals(redMax, serverInitMessage!!.redMax)
        assertEquals(greenMax, serverInitMessage!!.greenMax)
        assertEquals(blueMax, serverInitMessage!!.blueMax)
        assertEquals(redShift, serverInitMessage!!.redShift)
        assertEquals(greenShift, serverInitMessage!!.greenShift)
        assertEquals(blueShift, serverInitMessage!!.blueShift)
        assertEquals(desktopName, serverInitMessage!!.desktopName)
    }

    @Test
    fun setEncodings() {
        val serverSocket = ServerSocket(19215)

        val vncClient = VncClient("localhost", 19215)
        val thread = Thread(Runnable {
            vncClient.setEncodings()
        })

        thread.start()

        val socket = serverSocket.accept()
        val inputStream = socket.getInputStream()

        val expectedNumberOfBytes = 4
        val setEncodingsMessage = ByteArray(expectedNumberOfBytes)
        var bytesRead = inputStream.read(setEncodingsMessage)

        assertEquals(expectedNumberOfBytes, bytesRead)

        val byteBuffer = ByteBuffer.wrap(setEncodingsMessage)

        val messageType = byteBuffer.get()
        assertEquals(2, messageType.toInt())

        byteBuffer.get() // Padding

        val numberOfEncodings = byteBuffer.short
        assertEquals(1, numberOfEncodings.toInt())

        val encodingType = ByteArray(4)
        bytesRead = inputStream.read(encodingType)
        assertEquals(4, bytesRead)
        assertEquals(0, ByteBuffer.wrap(encodingType).int)

        socket.close()
        serverSocket.close()
        thread.join()
    }
}