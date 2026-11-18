package dev.adrianjost.dimly

import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * Small HTTP API for Home Assistant. Every request needs the key, as `X-Key` header or `key` parameter.
 *
 *  - `POST /dim?level=40`  set the darkness (0 = off, up to [DimService.MAX_LEVEL]);
 *                          optional `fade` in ms (default [DimService.FADE_MS], 0 = instant)
 *  - `GET  /status`        current level
 *
 * Both answer `{"level": <n>}`.
 */
class DimServer(port: Int, private val service: DimService) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) runCatching { session.parseBody(HashMap()) }
        val params = session.parameters
        val given = session.headers["x-key"] ?: params["key"]?.firstOrNull().orEmpty()
        if (!sameKey(given, service.key)) return json(Response.Status.UNAUTHORIZED, """{"error":"invalid key"}""")

        return when (session.uri.trimEnd('/')) {
            "/status" -> json(Response.Status.OK, levelJson(service.level))
            "/dim" -> {
                if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use POST"}""")
                val level = params["level"]?.firstOrNull()?.trim()?.toFloatOrNull()?.toInt()
                    ?: return json(Response.Status.BAD_REQUEST, """{"error":"level must be a number"}""")
                val fade = params["fade"]?.firstOrNull()?.trim()?.toLongOrNull()
                    ?.coerceIn(0L, DimService.MAX_FADE_MS) ?: DimService.FADE_MS
                json(Response.Status.OK, levelJson(service.setLevel(level, fade)))
            }
            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private fun levelJson(level: Int) = """{"level":$level}"""

    private fun json(status: Response.Status, body: String) =
        newFixedLengthResponse(status, "application/json", body)

    /** Constant-time comparison, so the key can't be guessed from response timing. */
    private fun sameKey(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

/** IPv4 address of the Streamer on the home network, shown in the panel. */
fun localIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull()?.hostAddress
}.getOrNull()
