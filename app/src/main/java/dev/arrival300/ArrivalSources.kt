package dev.arrival300

internal interface ArrivalSources {
    fun connect()
    fun foreground(active: Boolean)
    fun close()
}
