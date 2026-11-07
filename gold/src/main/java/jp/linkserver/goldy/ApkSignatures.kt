package jp.linkserver.goldy

import com.android.apksig.ApkVerifier as AndroidSignatureVerifier
import java.io.File

/** Only populated from authenticated APK signatures or the OS's installed signing details. */
internal data class AuthenticatedSigningHistory(
    val certificates: List<String>,
    val installedData: Set<String>,
    val sdk: Int
)

internal fun acceptsSigningUpdate(installed: ApkSigners, candidate: ApkSigners): Boolean {
    if (installed == candidate) return true
    if (installed.multiple || candidate.multiple) return false
    val proof = candidate.authenticatedHistory ?: return false
    if (proof.sdk < 28) return false
    val old = installed.currentSha256.singleOrNull() ?: return false
    val current = candidate.currentSha256.singleOrNull() ?: return false
    val history = proof.certificates
    if (history.size < 2 || history.distinct().size != history.size || history.last() != current ||
        history.any { !it.matches(Regex("[0-9a-f]{64}")) }) return false
    val index = history.indexOf(old)
    if (index < 0 || index >= history.lastIndex || old !in proof.installedData) return false
    // Require a contiguous extension of the OS-established installed history, never a fork/rollback.
    val previous = installed.authenticatedHistory?.certificates ?: listOf(old)
    return previous.isNotEmpty() && previous.last() == old && previous.distinct().size == previous.size &&
        previous.size <= index + 1 && history.subList(index + 1 - previous.size, index + 1) == previous
}

/** Full APK contents/signature/PoR verification for precisely the running platform, not cert extraction. */
internal object ApkSignatureAuthenticator {
    fun inspect(file: File, sdk: Int): ApkSigners? {
        return try {
            require(sdk >= 26)
            val result = AndroidSignatureVerifier.Builder(file)
                .setMinCheckedPlatformVersion(sdk).setMaxCheckedPlatformVersion(sdk).build().verify()
            if (!result.isVerified || result.containsErrors()) null else {
                val certs = result.signerCertificates
                val hashes = certs.map { sha256(it.encoded) }
                if (hashes.isEmpty() || hashes.distinct().size != hashes.size) null else {
                    val signers = ApkSigners(hashes.toSet(), hashes.size > 1)
                    if (sdk >= 28 && !signers.multiple) {
                        val infos = when {
                            result.isVerifiedUsingV31Scheme -> result.v31SchemeSigners
                            result.isVerifiedUsingV3Scheme -> result.v3SchemeSigners
                            else -> emptyList()
                        }
                        if (infos.isNotEmpty()) {
                            val active = infos.filter { sdk in it.minSdkVersion..it.maxSdkVersion &&
                                sha256(it.certificate.encoded) == hashes.single() }.singleOrNull()
                                ?: return null
                            // Platform development-release targeting needs a separate verified contract.
                            if (active.rotationTargetsDevRelease) return null
                            active.signingCertificateLineage?.let { lineage ->
                                val ordered = lineage.certificatesInLineage
                                val history = ordered.map { sha256(it.encoded) }
                                if (history.isEmpty() || history.last() != hashes.single() || history.distinct().size != history.size)
                                    return null
                                val capabilities = ordered.filter { lineage.getSignerCapabilities(it).hasInstalledData() }
                                    .map { sha256(it.encoded) }.toSet()
                                signers.authenticatedHistory = AuthenticatedSigningHistory(history, capabilities, sdk)
                            }
                        }
                    }
                    signers
                }
            }
        } catch (_: Exception) { null }
    }
}
