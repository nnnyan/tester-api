package ch.nyan.api.tester

import java.io.File
import java.io.InputStream

val workingDirectory = File("/home/test")

fun file(fileName: String) = File(workingDirectory, fileName)

fun saveFile(input: InputStream, fileName: String) {
    input.use {
        file(fileName).outputStream().use { input.copyTo(it) }
    }
}

fun runProcess(vararg commands: String) =
    ProcessBuilder("su", "-", "tester", "/bin/sh", "-c", *commands)
        .directory(workingDirectory)
        .start()
