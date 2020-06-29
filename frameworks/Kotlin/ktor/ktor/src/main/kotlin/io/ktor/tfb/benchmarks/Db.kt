package io.ktor.tfb.benchmarks

import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.tfb.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import java.util.*
import java.util.concurrent.*

fun Application.dbBenchmark(
    databaseDispatcher: CoroutineDispatcher,
    pool: HikariDataSource
) {
    val dbRows = 10000
    val worldSerializer = World.serializer()
    val worldListSerializer = World.serializer().list

    routing {
        get("/db") {
            val random = ThreadLocalRandom.current()
            val queries = call.queries()
            val result = ArrayList<World>(queries ?: 1)

            withContext(databaseDispatcher) {
                pool.connection.use { connection ->
                    connection.prepareStatement("SELECT id, randomNumber FROM World WHERE id = ?").use { statement ->
                        for (i in 1..(queries ?: 1)) {
                            statement.setInt(1, random.nextInt(dbRows) + 1)
                            statement.executeQuery().use { rs ->
                                while (rs.next()) {
                                    result += World(rs.getInt(1), rs.getInt(2))
                                }
                            }
                        }
                    }
                }
            }

            call.respondText(
                when (queries) {
                    null -> Json.stringify(worldSerializer, result.single())
                    else -> Json.stringify(worldListSerializer, result)
                }, ContentType.Application.Json, HttpStatusCode.OK
            )
        }
    }
}
