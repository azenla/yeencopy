package gay.pizza.yeencopy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.system.measureTimeMillis

data class Yeen(val url: String)

class YeenClient(val baseUrl: String, val concurrency: Int = 16) : AutoCloseable {
  private val client = HttpClient()

  suspend fun fetchRandomYeen(): Yeen {
    val response = client.get(baseUrl) { yeencopy() }.check()
    return Yeen(baseUrl + response.body<String>().split("src=\"")[1].split("\"")[0].trim())
  }

  suspend fun fetchYeensUntilSaturation(saturationLimit: Int = 200): List<Yeen> {
    val yeens = mutableSetOf<Yeen>()
    var saturation = 0
    while (saturation < saturationLimit)
      coroutineScope {
        val parallels = (1..concurrency).map { async { fetchRandomYeen() } }
        val discovered = awaitAll(*parallels.toTypedArray())
        for (yeen in discovered) if (!yeens.add(yeen)) saturation++
      }
    return yeens.toList()
  }

  suspend fun downloadYeen(yeen: Yeen, directory: Path): Path {
    val name = Path(URL(yeen.url).path).fileName.toString()
    val path = directory.resolve(name)
    val bytes = client.get(yeen.url) { yeencopy() }.check().body<ByteArray>()
    path.writeBytes(bytes)
    return path
  }

  suspend fun downloadAllYeens(yeens: List<Yeen>, directory: Path) {
    directory.createDirectories()
    yeens.forEach { yeen -> downloadYeen(yeen, directory) }
  }

  private fun HttpRequestBuilder.yeencopy() {
    header("User-Agent", "GayPizza-YeenCopy/1.0")
  }

  private fun HttpResponse.check(): HttpResponse {
    if (status.isSuccess()) return this
    throw RuntimeException("HTTP call failed to ${request.url}: status=${status.value}")
  }

  override fun close(): Unit = client.close()
}

fun main(args: Array<String>): Unit = runBlocking {
  YeenClient(if (args.isEmpty()) "https://hyena.photos" else args[0]).use { client ->
    val yeens: List<Yeen>
    val discoveryTimeInMillis = measureTimeMillis { yeens = client.fetchYeensUntilSaturation() }
    println("Discovered ${yeens.size} yeens in ${discoveryTimeInMillis / 1000.0} seconds")
    val downloadTimeInMillis = measureTimeMillis { client.downloadAllYeens(yeens, Path("yeens")) }
    println("Downloaded ${yeens.size} yeens in ${downloadTimeInMillis / 1000.0} seconds")
  }
}
