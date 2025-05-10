package com.example.bigtwo.model

data class Card(val rank: Int, val suit: Suit) : Comparable<Card> {

    enum class Suit {
        DIAMONDS, CLUBS, HEARTS, SPADES
    }

    override fun compareTo(other: Card): Int {
        return if (rank == other.rank) suit.compareTo(other.suit) else rank.compareTo(other.rank)
    }

    override fun toString(): String {
        val rankStr = when (rank) {
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            14 -> "A"
            15 -> "2"
            else -> rank.toString()
        }
        return "$rankStr of $suit"
    }
}