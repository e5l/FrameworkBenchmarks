import com.github.jasync.sql.db.*
import com.github.jasync.sql.db.postgresql.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.reactiverse.kotlin.pgclient.*
import io.reactiverse.pgclient.*
import io.reactiverse.pgclient.PgClient
import kotlinx.html.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlin.random.*

@Serializable
data class Message(val message: String)

@Serializable
data class World(val id: Int, val randomNumber: Int)

data class Fortune(val id: Int, val message: String)

val rand = Random(1)

interface Repository {
    suspend fun getWorld(): World
    suspend fun getFortunes(): List<Fortune>
    suspend fun updateWorlds(worlds: List<World>)
}

class JasyncRepository : Repository {
    private val dbConfig: ConnectionPoolConfiguration
    private val db: SuspendingConnection

    init {
        dbConfig = ConnectionPoolConfiguration(
            "tfb-database",
            database = "hello_world",
            username = "benchmarkdbuser",
            password = "benchmarkdbpass",
            maxActiveConnections = 64
        )
        db = PostgreSQLConnectionBuilder.createConnectionPool(dbConfig).asSuspending
    }

    override suspend fun getWorld(): World {
        val worldId = rand.nextInt(1, 10000)
        val result = db.sendPreparedStatement("select id, randomNumber from world where id = ?", listOf(worldId))
        val row = result.rows.first()
        return World(row.getInt(0)!!, row.getInt(1)!!)
    }

    override suspend fun getFortunes(): List<Fortune> {
        val results = db.sendPreparedStatement("select id, message from fortune")
        return results.rows.map { Fortune(it.getInt(0)!!, it.getString(1)!!) }
    }

    override suspend fun updateWorlds(worlds: List<World>) {
        worlds.forEach { world ->
            db.sendPreparedStatement(
                "update world set randomNumber = ? where id = ?",
                listOf(world.randomNumber, world.id)
            )
        }
    }
}

class ReactivePGRepository : Repository {
    private val db: PgPool

    init {
        val poolOptions = PgPoolOptions()
        poolOptions.apply {
            host = "tfb-database"
            database = "hello_world"
            user = "benchmarkdbuser"
            password = "benchmarkdbpass"
            maxSize = 64
            cachePreparedStatements = true
        }
        db = PgClient.pool(poolOptions)
    }

    override suspend fun getFortunes(): List<Fortune> {
        val results = db.preparedQueryAwait("select id, message from fortune")
        return results.map { Fortune(it.getInteger(0), it.getString(1)) }
    }

    override suspend fun getWorld(): World {
        val worldId = rand.nextInt(1, 10000)
        val result = db.preparedQueryAwait("select id, randomNumber from world where id = $1", Tuple.of(worldId))
        val row = result.first()
        return World(row.getInteger(0), row.getInteger(1)!!)
    }

    override suspend fun updateWorlds(worlds: List<World>) {
        val batch = worlds.map { Tuple.of(it.id, it.randomNumber) }
        db.preparedBatchAwait("update world set randomNumber = $1 where id = $2", batch)
    }
}

fun String.toBoxedInt(range: IntRange): Int = try {
    this.toInt().coerceIn(range)
} catch (e: NumberFormatException) {
    1
}

class MainTemplate : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {
        head {
            title { +"Fortunes" }
        }
        body {
            insert(content)
        }
    }
}

class FortuneTemplate(val fortunes: List<Fortune>, val main: MainTemplate = MainTemplate()) : Template<HTML> {
    override fun HTML.apply() {
        insert(main) {
            content {
                table {
                    tr {
                        th { +"id" }
                        th { +"message" }
                    }
                    fortunes.forEach { fortune ->
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

fun main(args: Array<String>) {
    val db = when (args.firstOrNull()) {
        "jasync-sql" -> JasyncRepository()
        "reactive-pg" -> ReactivePGRepository()
        else -> throw IllegalArgumentException("Must specify a postgres client")
    }

    val messageSerializer = Message.serializer()
    val worldSerializer = World.serializer()

    val server = embeddedServer(Netty, 8080, configure = {
        shareWorkGroup = true
    }) {
        install(DefaultHeaders)
        routing {
            get("/plaintext") {
                call.respondText("Hello, World!")
            }

            get("/json") {
                call.respondText(
                    Json.stringify(messageSerializer, Message("Hello, World!")),
                    ContentType.Application.Json
                )
            }

            get("/db") {
                call.respondText(Json.stringify(worldSerializer, db.getWorld()), ContentType.Application.Json)
            }

            get("/query/") {
                val queries = call.parameters["queries"]?.toBoxedInt(1..500) ?: 1
                val worlds = (1..queries).map { db.getWorld() }
                call.respondText(Json.stringify(worldSerializer.list, worlds), ContentType.Application.Json)
            }

            get("/fortunes") {
                val newFortune = Fortune(0, "Additional fortune added at request time.")
                val fortunes = db.getFortunes().toMutableList()
                fortunes.add(newFortune)
                fortunes.sortBy { it.message }
                call.respondHtmlTemplate(FortuneTemplate(fortunes)) { }
            }

            get("/updates") {
                val queries = call.parameters["queries"]?.toBoxedInt(1..500) ?: 1
                val worlds = (1..queries).map { db.getWorld() }
                val newWorlds = worlds.map { it.copy(randomNumber = rand.nextInt(1..10000)) }

                db.updateWorlds(newWorlds)

                call.respondText(Json.stringify(worldSerializer.list, newWorlds), ContentType.Application.Json)
            }
        }
    }

    server.start(wait = true)
}
