package ch.nyan.api.tester.pipeline

import ch.nyan.api.tester.file
import ch.nyan.api.tester.runProcess
import ch.nyan.api.tester.saveFile
import ch.nyan.api.tester.workingDirectory
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@Component
@Scope(value = "prototype")
class CompilerPipe (private val lang: String, private val input: InputStream) : Callable<Pair<Int, String>> {
    companion object {
        private val compilers = mapOf(
            "c" to ("cp %1\$s %1\$s.c && gcc %1\$s.c -o %2\$s" to "./%1\$s"),
            "cpp" to ("cp %1\$s %1\$s.cpp && g++ %1\$s.cpp -o %2\$s" to "./%1\$s"),
            "perl" to ("" to "perl ./%1\$s"),
            "python3" to ("" to "python3 ./%1\$s")
        )
    }

    @Autowired
    lateinit var beanFactory: BeanFactory

    init {
        if (!compilers.containsKey(lang)) {
            throw UnsupportedOperationException()
        }
    }

    override fun call(): Pair<Int, String> {
        val compiler = compilers[lang] ?: throw UnsupportedOperationException()

        val sourceName = (1..32)
            .map { ('a'..'z').random() }
            .joinToString("") +
                ".test.src"

        val outputName = (1..32)
            .map { ('a'..'z').random() }
            .joinToString("") +
            ".test"

        if (compiler.first.isBlank()) {
            saveFile(input, outputName)
        } else {
            saveFile(input, sourceName)

            println(file(sourceName).bufferedReader().readText())

            val process = ProcessBuilder("/bin/sh", "-c", compiler.first.format(sourceName, outputName))
                .directory(workingDirectory)
                .start()

            val outStream =
                beanFactory.getBean("newStreamConsumer", process.inputStream, null) as AsyncCallable<Pair<Int, String>>
            val errStream =
                beanFactory.getBean("newStreamConsumer", process.errorStream, null) as AsyncCallable<Pair<Int, String>>

            val outResultFuture = outStream.asyncCall()
            val errResultFuture = errStream.asyncCall()

            val exitValue = process.waitFor()

            val errString = errResultFuture
                .get(20000, TimeUnit.MILLISECONDS)
                .second
                .replace(sourceName, "source")
                .replace(outputName, "out")

            println("errString: $errString")

            if (exitValue != 0) {
                return exitValue to errString
            }
        }

        val chmod = ProcessBuilder("/bin/sh", "-c",
                "chown tester $outputName && chmod 555 $outputName")
            .directory(workingDirectory)
            .start()

        if (!chmod.waitFor(5000, TimeUnit.MILLISECONDS) || chmod.exitValue() != 0) {
            return -1 to ""
        }

        return 0 to compiler.second.format(outputName)
    }
}