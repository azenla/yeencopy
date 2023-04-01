package gay.pizza.yeencopy

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.security.MessageDigest
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
)

fun Yeen.key(): String = bytes.md5Hash().toHexString()

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

class YeenSaturation(val limit: Int) {
  val yeens = mutableMapOf<String, Yeen>()

  var saturation: Int = 0

  fun add(yeen: Yeen): Boolean {
    val key = yeen.key()
    if (yeens.containsKey(key)) {
      saturation++
    } else {
      yeens[key] = yeen
    }
    return saturation >= limit
  }

  fun collection(): YeenCollection = YeenCollection(yeens)
}

class YeenCopy(val url: String, val httpClient: HttpClient = HttpClient.newHttpClient()) {
  fun copyUntilSaturation(saturationLimit: Int = 100): YeenCollection {
    val saturation = YeenSaturation(saturationLimit)
    while (true) {
      val yeen = copyOne()
      if (saturation.add(yeen)) break
    }
    return saturation.collection()
  }

  fun copyOne(): Yeen {
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
}

fun main(args: Array<String>) {
  val url = args[0]
  val copier = YeenCopy(url)
  val collection: YeenCollection
  val timeInMillis = measureTimeMillis { collection = copier.copyUntilSaturation() }
  println("Collected ${collection.yeens.size} yeens in ${timeInMillis / 1000.0} sec")
  collection.writeAll(Path("yeens"))
}
