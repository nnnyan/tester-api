package ch.nyan.api.tester.controller

import ch.nyan.api.tester.pipeline.TesterPipe
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.websocket.server.PathParam

@RestController
class RunController {
    @Autowired
    lateinit var beanFactory: BeanFactory

    @RequestMapping("/run/{lang}")
    fun runCode(@PathVariable("lang") lang: String,
                @RequestParam("memory") memory_limit: Int,
                @RequestParam("time") time_limit: Int,
                @RequestParam("testcase_set") file: MultipartFile): StreamingResponseBody {
        println("asdfasdf")
        return beanFactory.getBean("newTesterPipe", lang, file.inputStream, memory_limit, time_limit)
            as StreamingResponseBody
    }

    @RequestMapping("/**")
    fun other(req: HttpServletRequest, res: HttpServletResponse) {
        res.sendRedirect("https://nyan.ch")
    }
}