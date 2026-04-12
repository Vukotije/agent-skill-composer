package com.vukan.agentskillcomposer.analysis

/**
 * Annotation FQNs shared across multiple analyzers.
 * Centralizing them avoids silent drift when one analyzer adds a new annotation
 * but others don't.
 */
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
