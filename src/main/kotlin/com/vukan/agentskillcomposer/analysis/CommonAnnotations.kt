package com.vukan.agentskillcomposer.analysis

object CommonAnnotations {

    val CONTROLLER = listOf(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
    )

    val SERVICE = listOf(
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Component",
    )

    val REPOSITORY = listOf(
        "org.springframework.stereotype.Repository",
        "io.micronaut.data.annotation.Repository",
    )
}
