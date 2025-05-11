package bigtwo.app.model

data class Card(val rank: Int, val suit: Suit) : Comparable<Card> {

    enum class Suit {
        //四种花色 ：方块（♦）、梅花（♣）、红心（♥）、黑桃（♠）
        DIAMOND, CLUB, HEART, SPADE
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