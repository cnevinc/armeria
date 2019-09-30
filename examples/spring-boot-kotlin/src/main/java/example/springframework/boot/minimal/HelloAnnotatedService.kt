package example.springframework.boot.minimal

import javax.validation.constraints.Size

import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

import com.linecorp.armeria.server.annotation.ExceptionHandler
import com.linecorp.armeria.server.annotation.Get
import com.linecorp.armeria.server.annotation.Param

/**
 * Note that this is not a Spring-based component but an annotated HTTP service that leverages
 * Armeria's built-in annotations.
 *
 * @see [Annotated HTTP Service](https://line.github.io/armeria/server-annotated-service.html)
 */
@Component
@Validated
@ExceptionHandler(ValidationExceptionHandler::class)
open class HelloAnnotatedService {

    @Get("/")
    fun defaultHello(): String {
        return "Hello, world! Try sending a GET request to /hello/armeria"
    }

    /**
     * An example in order to show how to use validation framework in an annotated HTTP service.
     */
    @Get("/hello/{name}")
    open fun hello(
            @Size(min = 3, max = 10, message = "name should have between 3 and 10 characters")
            @Param
            name: String): String {
        return String.format("Hello, %s! This message is from Armeria annotated service!", name)
    }
}
