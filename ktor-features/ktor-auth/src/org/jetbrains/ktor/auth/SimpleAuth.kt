package org.jetbrains.ktor.auth

import org.jetbrains.ktor.util.*
import java.security.MessageDigest

data class UserIdPrincipal(val name: String) : Principal
data class UserPasswordCredential(val name: String, val password: String) : Credential

interface HashingConfiguration {
    val hashAlgorithm: String
    val salt: String
    val users: List<UserConfiguration>
}

interface UserConfiguration {
    val name: String
    val hash: String
}

class UserHashedTableAuth(val digester: (String) -> ByteArray,
                          val table: Map<String, ByteArray>) {

    // shortcut for tests
    constructor(table: Map<String, ByteArray>) : this(getDigestFunction("SHA-256", "ktor"), table)

    constructor(config: HashingConfiguration) : this(getDigestFunction(
            config.hashAlgorithm,
            config.salt), config.users.associateBy({ it.name }, { decodeBase64(it.hash) }))

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    fun authenticate(credential: UserPasswordCredential): UserIdPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && MessageDigest.isEqual(digester(credential.password), userPasswordHash)) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}
