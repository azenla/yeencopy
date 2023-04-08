package gay.pizza.yeencopy

import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.system.measureTimeMillis

class Yeen(val url: String)

fun Yeen.download(httpClient: HttpClient, directory: Path) {
  val uri = URL(url.replace(" ", "%20")).toURI()
  val name = Path(uri.path).fileName.name.replace("%20", "_")
  val file = directory.resolve(name)
  val request = HttpRequest.newBuilder()
    .GET().header("User-Agent", "Pizza-YeenCopy/1.0")
    .uri(uri).build()
  val response = httpClient.send(request, BodyHandlers.ofFile(file))
  if (response.statusCode() != 200) {
    throw RuntimeException("Failed to download yeen at ${url}: status=${response.statusCode()}")
  }
}

class YeenCollection(
  val yeens: Map<String, Yeen>
)

fun YeenCollection.downloadAllParallel(httpClient: HttpClient, executor: ScheduledThreadPoolExecutor, path: Path) {
  path.createDirectories()
  for (yeen in yeens.values) {
    executor.execute {
      try {
        yeen.download(httpClient, path)
      } catch (e: Exception) {
        println("Failed to download ${yeen.url}: $e")
      }
    }
  }
  while (executor.activeCount > 0) {
    Thread.sleep(1)
  }
}

class YeenSaturation(private val limit: Int) {
  private val yeens = ConcurrentHashMap<String, Yeen>()
  private val saturation = AtomicInteger(0)

  fun add(yeen: Yeen): Boolean {
    if (yeens.putIfAbsent(yeen.url, yeen) != null) {
      saturation.incrementAndGet()
    }
    return saturation.get() >= limit
  }

  fun collection(): YeenCollection = YeenCollection(yeens)
}

class YeenDiscovery(
  private val httpClient: HttpClient, private val url: String,
  private val executor: ScheduledThreadPoolExecutor, private val parallelTriesCount: Int) {
  fun copyUntilSaturation(saturationLimit: Int = 100): YeenCollection {
    val saturation = YeenSaturation(saturationLimit)
    while (true) {
      if (runTriesParallel(saturation)) {
        break
      }
    }
    return saturation.collection()
  }

  private fun runTriesParallel(saturation: YeenSaturation): Boolean {
    val flag = AtomicBoolean()
    for (i in 1..parallelTriesCount) {
      executor.execute {
        val yeen = copyOne()
        if (saturation.add(yeen)) {
          flag.set(true)
        }
      }
    }
    while (executor.activeCount > 0) {
      Thread.sleep(1)
    }
    return flag.get()
  }

  private fun copyOne(): Yeen {
    val request = HttpRequest.newBuilder().GET()
      .uri(URI.create(url))
      .header("User-Agent", "Pizza-YeenCopy/1.0")
      .build()
    val response = httpClient.send(request, BodyHandlers.ofString())
    if (response.statusCode() != 200) {
      throw RuntimeException("Status Code: ${response.statusCode()}")
    }
    val body = response.body()
    val parts = body.split("src=\"")
    val fullUrl = url + parts[1].split("\"")[0].trim()
    return Yeen(fullUrl.trim())
  }
}

fun main(args: Array<String>) {
  val url = args[0]
  val httpClient = HttpClient.newHttpClient()
  val executor = ScheduledThreadPoolExecutor(8)
  val discovery = YeenDiscovery(httpClient, url, executor, 8)
  val collection: YeenCollection
  val discoverTimeInMillis = measureTimeMillis { collection = discovery.copyUntilSaturation() }
  println("Discovered ${collection.yeens.size} yeens in ${discoverTimeInMillis / 1000.0} seconds")
  val downloadedTimeInMillis = measureTimeMillis {
    collection.downloadAllParallel(httpClient, executor, Path("yeens"))
  }
  println("Downloaded ${collection.yeens.size} yeens in ${downloadedTimeInMillis / 1000.0} seconds")
  executor.shutdown()
}
