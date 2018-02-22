package io.ktor.locations

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*

private object LocationSamples {
    fun annotation() {
        @Location("/user/{user}")
        data class UserPage(val user: String)
    }

    fun Application.feature() {
        @Location("/")
        class Root

        @Location("/user/{user}")
        data class UserPage(val user: String)

        routing {
            get { root: Root ->
                val userName = "test"
                val userPageTest = UserPage(userName)
                val userPageLink = locations.href(userPageTest)
                call.respondText("<a href='$userPageLink'>$userName</a>")
            }
            get { userPage: UserPage ->
                call.respondText("User ${userPage.user.escapeHTML()}")
            }
        }
    }
}
