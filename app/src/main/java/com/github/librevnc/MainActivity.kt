package com.github.librevnc

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import org.jetbrains.anko.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.Exception
import java.net.Socket

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadData()
    }

    private fun downloadData() {
        doAsync {
            val vncClient = VncClient("192.168.42.64", 5900)

            val wasHandshakeSuccessful = vncClient.performHandshake()

            uiThread { /*Update the UI thread here*/ toast("Handshake successful: $wasHandshakeSuccessful") }
        }
    }
}
