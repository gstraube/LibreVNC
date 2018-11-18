package com.github.librevnc

data class ServerInitMessage(
    val frameBufferWidth: Short,
    val frameBufferHeight: Short,
    val bitsPerPixel: Byte,
    val depth: Byte,
    val bigEndianFlag: Byte,
    val trueColorFlag: Byte,
    val redMax: Short,
    val greenMax: Short,
    val blueMax: Short,
    val redShift: Byte,
    val greenShift: Byte,
    val blueShift: Byte,
    val desktopName: String
)