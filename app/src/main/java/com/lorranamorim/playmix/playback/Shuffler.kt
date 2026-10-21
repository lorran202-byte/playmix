package com.lorranamorim.playmix.playback

import kotlin.random.Random

/**
 * Aleatório de verdade: uma ordem nova a cada vez (semente nova),
 * espalhando as músicas do mesmo artista ao longo da lista.
 * Cada música aparece uma vez só, então nada repete até a lista acabar.
 */
object Shuffler {
    fun <T> shuffle(items: List<T>, artistOf: (T) -> String, random: Random = Random(System.nanoTime())): List<T> {
        if (items.size < 3) return items.shuffled(random)
        val groups = items.groupBy(artistOf).values.map { it.shuffled(random) }
        val placed = ArrayList<Pair<Double, T>>(items.size)
        for (g in groups) {
            val n = g.size
            val step = 1.0 / n
            val offset = random.nextDouble() * step
            g.forEachIndexed { i, item ->
                val jitter = (random.nextDouble() - 0.5) * step * 0.4
                placed += (offset + i * step + jitter) to item
            }
        }
        return placed.sortedBy { it.first }.map { it.second }
    }
}
