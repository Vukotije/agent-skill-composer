package com.vukan.agentskillcomposer.ui

import com.vukan.agentskillcomposer.model.DetectedConvention
import com.vukan.agentskillcomposer.model.DetectedFramework

fun DetectedFramework.toDisplayString(): String = buildString {
    append(name)
    version?.let { append(" ($it)") }
}

fun DetectedConvention.toDisplayString(): String = buildString {
    append("[${confidence.displayName}] ")
    append(summary)
}
