package ch.nyan.api.tester.pipeline

import ch.nyan.api.tester.file
import ch.nyan.api.tester.runProcess
import ch.nyan.api.tester.saveFile
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.*
import java.util.concurrent.*
import java.util.zip.ZipFile

@Component
@Scope(value = "prototype")
class TesterPipe (private val lang: String,
                  private val input: InputStream,
                  private val memory_limit: Int,
                  private val time_limit: Int) : StreamingResponseBody {
    companion object {
        val MAX_LINE: Int = 10000
    }

    @Autowired
    lateinit var beanFactory: BeanFactory

    val fileName = (1..32)
        .map { (('a'..'z') + (0..9)).random() }
        .joinToString("") +
        ".zip"

    override fun writeTo(out: OutputStream) {
        runTest(out)
        out.close()
        file(fileName).apply { if (exists() && !isDirectory) delete() }
    }

    /*
     * Example:
     * 0 // language supported
     * 0 // compiler exit code (compile succeed)
     * 10 // number of testcases
     * 0 1 // first: testcase succeed, second: cpu time
     * 0 1 // ...
     * 0 1
     * 0 1
     * 0 2
     * 0 1
     * 0 2
     * 0 2
     * 0 1
     * 0 1
     */
    fun runTest(outputStream: OutputStream) {
        saveFile(input, fileName)
        val file = file(fileName)
        println("${file.length()}, ${file.absoluteFile}")
        val zip = ZipFile(file)
        val out = PrintWriter(outputStream, true)


        val inEntries = mutableListOf<String>()
        val outEntries = mutableListOf<String>()

        var executable: String? = null

        zip.entries().toList().forEach {
            if (it.isDirectory) return@forEach

            when {
                it.name.endsWith(".source") -> {
                    val compiler =
                        try {
                            beanFactory.getBean("newCompilerPipe", lang, zip.getInputStream(it)) as Callable<Pair<Int, String>>
                        } catch (e: UnsupportedOperationException) {
                            out.println(-10)
                            out.flush()
                            return
                        }

                    out.println(0)
                    val result = compiler.call()
                    if (result.first != 0) {
                        out.println(-1)
                        out.println(result.second)
                        return@runTest
                    }

                    out.println(0)
                    executable = result.second
                }
                it.name.endsWith(".in") -> {
                    inEntries.add(it.name.substringBeforeLast('.'))
                }
                it.name.endsWith(".out") -> {
                    outEntries.add(it.name.substringBeforeLast('.'))
                }
            }
        }

        if (executable == null) {
            out.println(-1)
            return
        }

        out.println(inEntries.size)

        inEntries.forEach {
            val outEntry = zip.getEntry("$it.out")
            val expected = zip.getInputStream(outEntry)

            zip.getInputStream(zip.getEntry("$it.in")).use { zin ->
                val result = test(executable!!, zin, expected)
                if (result.first != 0) {
                    out.println(result.first)
                    /*
                    if (result.second.isNotBlank()) {
                        out.println(result.second)
                    }
                     */
                    return@runTest
                }

                out.println("0 ${result.second}")
            }
        }
    }


    private fun test(executable: String, stdin: InputStream, expected: InputStream): Pair<Int, String> {
        val process = runProcess(executable)

        val inStream = beanFactory.getBean("newStreamPipe", stdin, process.outputStream) as Runnable
        val outStream = beanFactory.getBean("newStreamConsumer", process.inputStream, expected) as AsyncCallable<Pair<Int, String>>
        val errStream = beanFactory.getBean("newStreamConsumer", process.errorStream, null) as AsyncCallable<Pair<Int, String>>

        val outResultFuture = outStream.asyncCall()
        val errResultFuture = errStream.asyncCall()
        inStream.run()

        val time_limit: Long =
            if (time_limit > 0) time_limit.toLong()
            else 10000

        val startTime = System.currentTimeMillis()
        process.waitFor(time_limit, TimeUnit.MILLISECONDS)

        try {
            val outResult = outResultFuture.get(time_limit, TimeUnit.MILLISECONDS)
            val errResult = errResultFuture.get(time_limit, TimeUnit.MILLISECONDS)

            val endTime = System.currentTimeMillis()

            if (process.exitValue() != 0) {
                return -10 to errResult.second
            }

            if (outResult.first == 0) {
                return 0 to (endTime - startTime).toString() //process.info().totalCpuDuration().map { it.toMillis() }.orElse(-1).toString()
            }

            return outResult
        } catch (e: TimeoutException) {
            return -3 to "Exceeded Time Limit"
        }
    }

    @Component
    @Scope(value = "prototype")
    class StreamPipe(private val input: InputStream, private val output: OutputStream) : Runnable {
        @Async
        override fun run() {
            input.use {
                output.use {
                    input.copyTo(it)
                }
            }
        }
    }

    @Component
    @Scope(value = "prototype")
    class StreamConsumer(private val input: InputStream, private val expected: InputStream? = null) :
        AsyncCallable<Pair<Int, String>> {
        var lineNum = 0

        override fun call(): Pair<Int, String> {
            if (expected != null) {
                println("excepted != null")
                var output = StringBuilder()
                var actual = input.bufferedReader().readText()
                expected.bufferedReader().useLines { eLines ->
                    eLines.forEach {
                        ++lineNum
                        if (lineNum > MAX_LINE) {
                            return@call -2 to "Exceed Output Limit"
                        }
                        output.append(it).append("\n")
                    }
                }

                if (output.trimEnd() == actual.trimEnd()) {
                    return 0 to ""
                } else {
                    return 1 to ""
                }
            } else {
                println("excepted == null $input")
                input.bufferedReader().use {
                    println("input use")
                    val output = it.readText()
                    println("out: $output")
                    return 0 to output
                }
            }
        }
    }
}