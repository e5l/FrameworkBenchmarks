package io.ktor.tfb.benchmarks

import com.zaxxer.hikari.*
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.routing.*
import io.ktor.tfb.*
import kotlinx.coroutines.*
import kotlinx.html.*

fun Application.fortunesBenchmark(
    databaseDispatcher: CoroutineDispatcher,
    pool: HikariDataSource
) {
    routing {
        get("/fortunes") {
            val result = mutableListOf<Fortune>()

            withContext(databaseDispatcher) {
                pool.connection.use { connection ->
                    connection.prepareStatement("SELECT id, message FROM fortune").use { statement ->
                        statement.executeQuery().use { rs ->
                            while (rs.next()) {
                                result += Fortune(rs.getInt(1), rs.getString(2))
                            }
                        }
                    }
                }
            }

            result.add(Fortune(0, "Additional fortune added at request time."))
            result.sortBy { it.message }

            call.respondHtml {
                head { title { +"Fortunes" } }
                body {
                    table {
                        tr {
                            th { +"id" }
                            th { +"message" }
                        }
                        for (fortune in result) {
                            tr {
                                td { +fortune.id.toString() }
                                td { +fortune.message }
                            }

                        }
                    }
                }
            }
        }
    }
}