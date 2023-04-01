package gay.pizza.yeencopy

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.system.measureTimeMillis

fun instanceOfDigest(algorithm: String): MessageDigest = MessageDigest.getInstance(algorithm)
fun MessageDigest.updateAndDigest(bytes: ByteArray): ByteArray = apply { update(bytes) }.digest()
fun ByteArray.hash(algorithm: String): ByteArray = instanceOfDigest(algorithm).updateAndDigest(this)
fun ByteArray.md5Hash(): ByteArray = hash("MD5")
fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }

class Yeen(
  val bytes: ByteArray,
  val type: String
) {
  val key: String = bytes.md5Hash().toHexString()
}

fun Yeen.fileExtension(): String = when (type) {
  "image/jpeg" -> "jpg"
  "image/png" -> "png"
  "image/gif" -> "gif"
  else -> "unk"
}

fun Yeen.write(path: Path): Unit = path.writeBytes(bytes)

class YeenCollection(
  val yeens: Map<String, Yeen>
)

fun YeenCollection.writeAll(path: Path) {
  path.createDirectories()
  for ((key, yeen) in yeens) {
    yeen.write(path.resolve("${key}.${yeen.fileExtension()}"))
  }
}

class YeenSaturation(private val limit: Int) {
  private val yeens = ConcurrentHashMap<String, Yeen>()
  private val saturation = AtomicInteger(0)

  fun add(yeen: Yeen): Boolean {
    if (yeens.putIfAbsent(yeen.key, yeen) != null) {
      saturation.incrementAndGet()
    }
    return saturation.get() >= limit
  }

  fun collection(): YeenCollection = YeenCollection(yeens)
}

class YeenCopy(private val url: String, private val parallelTriesCount: Int = 8) : AutoCloseable {
  private val httpClient: HttpClient = HttpClient.newHttpClient()
  private val pool = ScheduledThreadPoolExecutor(parallelTriesCount)

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
      pool.execute {
        val yeen = copyOne()
        if (saturation.add(yeen)) {
          flag.set(true)
        }
      }
    }
    while (pool.activeCount > 0) {
      Thread.sleep(1)
    }
    return flag.get()
  }

  private fun copyOne(): Yeen {
    val request = HttpRequest.newBuilder().GET()
      .uri(URI.create(url))
      .header("User-Agent", "Pizza-YeenCopy/1.0")
      .build()
    val response = httpClient.send(request, BodyHandlers.ofByteArray())
    if (response.statusCode() != 200) {
      throw RuntimeException("Status Code: ${response.statusCode()}")
    }
    return Yeen(
      bytes = response.body(),
      type = response.headers()
        .firstValue("Content-Type")
        .orElse("image/unknown")
    )
  }

  override fun close() {
    pool.shutdownNow()
  }
}

fun main(args: Array<String>) {
  val url = args[0]
  val copier = YeenCopy(url)
  val collection: YeenCollection
  val timeInMillis = measureTimeMillis { copier.use { collection = copier.copyUntilSaturation() } }
  println("Collected ${collection.yeens.size} yeens in ${timeInMillis / 1000.0} seconds")
  collection.writeAll(Path("yeens"))
}
