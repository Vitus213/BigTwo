package bigtwo.app

import bigtwo.app.model.Deck
import org.junit.Test

class TestDeck {
    @Test
    fun testDeckCreation() {
        val deck = Deck()
        val hands = deck.deal()

        hands.forEachIndexed { index, hand ->
            println("Player ${index + 1}'s hand (${hand.size} cards):")
            hand.sorted().forEach { println(it) }
            println("--------------")
        }
    }
}

