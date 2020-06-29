package io.ktor.tfb.benchmarks

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*

private val PLAIN_TEXT_CONTENT = TextContent("Hello, World!", ContentType.Text.Plain, HttpStatusCode.OK)

fun Application.plainTextBenchmark() {
    routing {
        get("plaintext") {
            call.respond(PLAIN_TEXT_CONTENT)
        }
    }
}
