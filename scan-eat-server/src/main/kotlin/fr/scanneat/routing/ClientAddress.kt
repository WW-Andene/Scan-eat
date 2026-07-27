package fr.scanneat.routing

import io.ktor.server.application.*
import io.ktor.server.request.*

// ============================================================================
// TRUSTED-PROXY-AWARE CLIENT IDENTITY
//
// RateLimiter and FetchRecipeRoute's SSRF guard both need the real client IP,
// not the raw TCP peer - every recommended deployment (Docker behind nginx,
// Railway/Render/Fly.io) puts a reverse proxy in front, so the raw peer is
// always the proxy's own address for every client.
//
// The naive fix - installing Ktor's XForwardedHeaders plugin and trusting
// X-Forwarded-For unconditionally - is exactly the bug this file closes:
// that plugin has no concept of "is the immediate peer actually one of my
// proxies," so if this server is ever reachable directly (misconfiguration,
// a platform that doesn't front it, a bare `docker run -p`), any client can
// set X-Forwarded-For to a fresh value on every request and fully defeat the
// rate limiter and the SSRF guard's per-client bucket.
//
// Instead: only trust X-Forwarded-For when the *direct* TCP peer is itself
// inside a configured trusted range (loopback, private RFC1918 space, or an
// operator-supplied allowlist for a proxy that lives on a public IP). When
// the peer isn't trusted, X-Forwarded-For is ignored entirely and the raw
// peer address is used - safe by default even if someone exposes this port
// with no reverse proxy at all, at the cost of collapsing that
// (misconfigured) deployment's clients into one shared rate-limit bucket
// rather than silently granting unlimited requests.
// ============================================================================

private data class Cidr(val network: Long, val prefixLen: Int) {
    fun contains(addr: Long): Boolean {
        if (prefixLen == 0) return true
        val mask = -1L shl (32 - prefixLen)
        return (addr and mask) == (network and mask)
    }
}

private fun ipv4ToLong(ip: String): Long? {
    val parts = ip.split(".")
    if (parts.size != 4) return null
    var value = 0L
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        value = (value shl 8) or octet.toLong()
    }
    return value
}

private fun parseCidr(spec: String): Cidr? {
    val trimmed = spec.trim()
    if (trimmed.isEmpty()) return null
    val (ipPart, prefixPart) = if ("/" in trimmed) {
        val (ip, prefix) = trimmed.split("/", limit = 2)
        ip to prefix
    } else {
        trimmed to "32"
    }
    val addr = ipv4ToLong(ipPart) ?: return null
    val prefixLen = prefixPart.toIntOrNull()?.takeIf { it in 0..32 } ?: return null
    return Cidr(addr, prefixLen)
}

// Loopback + all three RFC1918 private ranges: the default trust set covers
// every common "reverse proxy on the same host or same private network"
// topology (Docker Compose service-to-service, a sidecar, an internal LB)
// without any operator configuration. A proxy that terminates TLS on a
// public IP of its own (not on a private network relative to this process)
// must be added explicitly via TRUSTED_PROXY_CIDRS.
private val DEFAULT_TRUSTED_CIDRS = listOf("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")

private val trustedCidrs: List<Cidr> by lazy {
    val configured = System.getenv("TRUSTED_PROXY_CIDRS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
    (configured ?: DEFAULT_TRUSTED_CIDRS).mapNotNull(::parseCidr)
}

private fun isTrustedPeer(peer: String): Boolean {
    // IPv6 loopback (::1) is the other common "proxy on the same host" form;
    // CIDR matching above only covers IPv4, so it's checked separately.
    if (peer == "::1" || peer == "0:0:0:0:0:0:0:1") return true
    val addr = ipv4ToLong(peer) ?: return false
    return trustedCidrs.any { it.contains(addr) }
}

/**
 * The last hop of X-Forwarded-For, i.e. the value the *nearest* proxy
 * appended about the connection it received - not the first (leftmost)
 * entry, which is exactly the value an untrusted client can set itself.
 */
private fun lastForwardedFor(header: String): String? =
    header.split(",").map { it.trim() }.lastOrNull { it.isNotEmpty() }

/**
 * The client identity to use for rate limiting and the SSRF guard: the real
 * TCP peer, unless that peer is itself a trusted proxy - in which case the
 * peer it reports via X-Forwarded-For is used instead. Never trusts a
 * client-supplied header directly.
 */
fun ApplicationCall.trustedClientIp(): String {
    val rawPeer = request.local.remoteHost
    if (!isTrustedPeer(rawPeer)) return rawPeer
    val forwarded = request.header("X-Forwarded-For")?.let(::lastForwardedFor)
    return forwarded ?: rawPeer
}
