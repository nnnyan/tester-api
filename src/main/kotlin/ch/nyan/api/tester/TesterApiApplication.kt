package ch.nyan.api.tester

import ch.nyan.api.tester.pipeline.CompilerPipe
import ch.nyan.api.tester.pipeline.TesterPipe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableAsync
import java.io.InputStream
import java.io.OutputStream
import org.springframework.web.multipart.commons.CommonsMultipartResolver



@SpringBootApplication
@Configuration
@ComponentScan("ch.nyan.api.tester")
@EnableAsync
@EnableAutoConfiguration(exclude = [ DataSourceAutoConfiguration::class ])
class TesterApiApplication {
	@Bean("newStreamConsumer")
	@Scope(value = "prototype")
	fun streamConsumer(input: InputStream, expected: InputStream?) = TesterPipe.StreamConsumer(input, expected)

	@Bean("newStreamPipe")
	@Scope(value = "prototype")
	fun streamPipe(input: InputStream, output: OutputStream) = TesterPipe.StreamPipe(input, output)

	@Bean("newTesterPipe")
	@Scope(value = "prototype")
	fun testerPipe(lang: String, input: InputStream, memory_limit: Int, time_limit: Int) = TesterPipe(lang, input, memory_limit, time_limit)

	@Bean("newCompilerPipe")
	@Scope(value = "prototype")
	fun compilerPipe(lang: String, input: InputStream) = CompilerPipe(lang, input)

	@Bean("multipartResolver")
	fun multipartResolver(): CommonsMultipartResolver = CommonsMultipartResolver().also {
			it.setMaxUploadSize(300 * 1024 * 1024)
		}
}

fun main(args: Array<String>) {
	System.setProperty("org.apache.catalina.connector.RECYCLE_FACADES", "true")
	workingDirectory.mkdirs()
	runApplication<TesterApiApplication>(*args)
}
