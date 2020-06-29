package io.ktor.tfb.benchmarks

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.tfb.*
import kotlinx.serialization.json.*

fun Application.jsonBenchmark() {
    val serializer = Message.serializer()

    routing {
        get("/json") {
            call.respondText(Json.stringify(serializer, Message()), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}