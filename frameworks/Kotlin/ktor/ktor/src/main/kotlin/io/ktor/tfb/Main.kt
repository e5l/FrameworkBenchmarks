@file:OptIn(EngineAPI::class)

package io.ktor.tfb

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*


fun main(args: Array<String>) {
    check(args.size == 1) { "Arguments are wrong: ${args.joinToString()}, expected `engine`" }

    val engine = when (args[0]) {
        "CIO" -> CIO
        "Jetty" -> Jetty
        "Netty" -> Netty
        else -> error("Engine ${args[0]} is not supported")
    }

    val server = embeddedServer(engine, 9090, configure = {
        engineConfiguration()
    }) {
        main()
    }

    server.start(wait = true)
}

fun BaseApplicationEngine.Configuration.engineConfiguration() {
    when (this) {
        is NettyApplicationEngine.Configuration -> {
            shareWorkGroup = true
        }
    }
}
