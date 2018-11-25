package com.github.librevnc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class InstrumentedTest {
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
    fun receiveFramebufferUpdate() {
        val appContext = InstrumentationRegistry.getTargetContext()

        var reconstructedBitmap: Bitmap? = null
        val thread = Thread(Runnable {
            reconstructedBitmap = vncClient.receiveFramebufferUpdate(bitsPerPixel = 32, expectedNumberOfRectangles = 1)
        })

        thread.start()

        val bitmap = BitmapFactory.decodeResource(appContext.resources, R.drawable.screenshot)

        val framebufferUpdate = ByteBuffer.allocate(16)

        val messageType: Byte = 0
        framebufferUpdate.put(messageType)

        val padding: Byte = 0
        framebufferUpdate.put(padding)

        val numberOfRectangles: Short = 1
        framebufferUpdate.putShort(numberOfRectangles)

        val xPosition: Short = 0
        val yPosition: Short = 0

        framebufferUpdate.putShort(xPosition)
        framebufferUpdate.putShort(yPosition)

        val width: Short = bitmap.width.toShort()
        val height: Short = bitmap.height.toShort()
        framebufferUpdate.putShort(width)
        framebufferUpdate.putShort(height)

        val encodingType = 0
        framebufferUpdate.putInt(encodingType)

        outputStream.write(framebufferUpdate.array())
        outputStream.flush()

        val byteCount = bitmap.byteCount
        val byteBuffer = ByteBuffer.allocate(byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)

        outputStream.write(byteBuffer.array())
        outputStream.flush()

        thread.join()

        assertNotNull(reconstructedBitmap)
        val reconstructedBitmapBuffer = ByteBuffer.allocate(reconstructedBitmap!!.byteCount)
        reconstructedBitmap!!.copyPixelsToBuffer(reconstructedBitmapBuffer)

        val bitmapBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(bitmapBuffer)

        assertEquals(reconstructedBitmapBuffer, bitmapBuffer)
    }
}