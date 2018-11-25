package com.github.librevnc

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import org.jetbrains.anko.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        establishConnection()
    }

    private fun establishConnection() {
        doAsync {
            val vncClient = VncClient("192.168.42.64", 5900)

            val wasHandshakeSuccessful = vncClient.performHandshake()
            val serverInitMessage = vncClient.initialize()

            uiThread { toast("Handshake successful: $wasHandshakeSuccessful") }
            if (serverInitMessage != null) {
                uiThread { toast("Received server init message (desktop name: ${serverInitMessage.desktopName}") }

                vncClient.setEncodings()

                vncClient.sendFramebufferUpdateRequest(
                    width = serverInitMessage.frameBufferWidth,
                    height = serverInitMessage.frameBufferHeight, isIncremental = false
                )

                vncClient.receiveFramebufferUpdate(
                    bitsPerPixel = serverInitMessage.bitsPerPixel,
                    expectedNumberOfRectangles = 1
                )
            }
        }
    }
}
