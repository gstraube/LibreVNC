package com.github.librevnc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

class VncClientTest {

    @Test
    fun performHandshake() {
        val serverSocket = ServerSocket(19215)

        val vncClient = VncClient("localhost", 19215)
        val thread = Thread(Runnable {
            vncClient.performHandshake()
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

        socket.close()
        thread.join()

        assertEquals(expectedProtocolVersion, protocolVersion)
        assertEquals(1, clientSecurityType[0].toInt())
    }
}