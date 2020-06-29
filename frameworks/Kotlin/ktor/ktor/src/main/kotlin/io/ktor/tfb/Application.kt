package io.ktor.tfb

import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.tfb.benchmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*

@Serializable
data class Message(val message: String = "Hello, World!")

@Serializable
data class World(val id: Int, var randomNumber: Int)

@Serializable
data class Fortune(val id: Int, var message: String)

@UseExperimental(InternalCoroutinesApi::class)
fun Application.main() {
    val poolSize = 48
    val pool by lazy { HikariDataSource(HikariConfig().apply { configurePostgres(poolSize) }) }
    val databaseDispatcher by lazy { ExperimentalCoroutineDispatcher().blocking(poolSize) }

    install(DefaultHeaders)

    plainTextBenchmark()
    jsonBenchmark()
    dbBenchmark(databaseDispatcher, pool)
    fortunesBenchmark(databaseDispatcher, pool)
    updatesBenchmark(databaseDispatcher, pool)
}

internal fun ApplicationCall.queries() = try {
    request.queryParameters["queries"]?.toInt()?.coerceIn(1, 500)
} catch (nfe: NumberFormatException) {
    1
}

