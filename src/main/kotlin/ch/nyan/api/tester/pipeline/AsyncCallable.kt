package ch.nyan.api.tester.pipeline

import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import java.util.concurrent.Callable
import java.util.concurrent.Future

interface AsyncCallable<T> : Callable<T> {
    @Async
    fun asyncCall(): Future<T> = AsyncResult(call())
}