package io.ktor.tfb.benchmarks

import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.tfb.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import java.util.ArrayList
import java.util.concurrent.*

fun Application.updatesBenchmark(
    databaseDispatcher: CoroutineDispatcher,
    pool: HikariDataSource
) {
    val dbRows = 10000
    val worldSerializer = World.serializer()
    val worldListSerializer = World.serializer().list

    routing {
        get("/updates") {
            val queries = call.queries()
            val random = ThreadLocalRandom.current()
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

                    result.forEach { it.randomNumber = random.nextInt(dbRows) + 1 }

                    connection.prepareStatement("UPDATE World SET randomNumber = ? WHERE id = ?").use { updateStatement ->
                            for ((id, randomNumber) in result) {
                                updateStatement.setInt(1, randomNumber)
                                updateStatement.setInt(2, id)

                                updateStatement.executeUpdate()
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