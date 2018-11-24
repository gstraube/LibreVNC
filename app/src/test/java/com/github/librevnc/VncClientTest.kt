package com.github.librevnc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

class VncClientTest {

    private lateinit var serverSocket: ServerSocket
    private lateinit var socket: Socket
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var vncClient: VncClient

    @Before
    fun setup() {
        val port = 19215
        serverSocket = ServerSocket(port)
        vncClient = VncClient("localhost", port)

        socket = serverSocket.accept()
        inputStream = socket.getInputStream()
        outputStream = socket.getOutputStream()
    }

    @After
    fun cleanup() {
        socket.close()
        serverSocket.close()
    }

    @Test
    fun performHandshake() {
        var wasHandshakeSuccessful = false
        val thread = Thread(Runnable {
            wasHandshakeSuccessful = vncClient.performHandshake()
        })

        thread.start()

        val out = PrintWriter(outputStream)
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

        thread.join()

        assertEquals(expectedProtocolVersion, protocolVersion)
        assertEquals(1, clientSecurityType[0].toInt())
        assertTrue(wasHandshakeSuccessful)
    }

    @Test
    fun initialize() {
        var serverInitMessage: ServerInitMessage? = null
        val thread = Thread(Runnable {
            serverInitMessage = vncClient.initialize()
        })

        thread.start()

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
        vncClient.setEncodings()

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
    }
}