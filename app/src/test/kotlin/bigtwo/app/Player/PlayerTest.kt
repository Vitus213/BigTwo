package bigtwo.app.player

import bigtwo.app.model.Card
import bigtwo.app.model.Card.Suit
import bigtwo.app.model.HandType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class PlayerTest {

    private lateinit var player: Player
    private lateinit var testCards: List<Card>

    @BeforeEach
    fun setUp() {
        player = Player("测试玩家")        // 创建测试用的卡牌
        testCards = listOf(
            Card(3, Suit.CLUB),
            Card(4, Suit.DIAMOND),
            Card(5, Suit.HEART)
        )
    }

    @Test
    fun testReceiveCards() {
        player.receiveCards(testCards)

        assertEquals(3, player.cardsCount())
        assertTrue(player.hasCard(testCards[0]))
        assertTrue(player.hasCard(testCards[1]))
        assertTrue(player.hasCard(testCards[2]))
    }

    @Test
    fun testGetCards() {
        player.receiveCards(testCards)
        val playerCards = player.getCards()

        assertEquals(3, playerCards.size)
        assertTrue(playerCards.containsAll(testCards))
    }

    @Test
    fun testPlayCardsSuccessful() {
        player.receiveCards(testCards)
        val playedCard = listOf(testCards[0])
        val result = player.playCards(playedCard)

        assertEquals(playedCard, result)
        assertEquals(2, player.cardsCount())
        assertFalse(player.hasCard(testCards[0]))
    }

    @Test
    fun testPlayCardsNotInHand() {
        player.receiveCards(testCards.subList(0, 2))

        val exception = assertThrows<IllegalArgumentException> {
            player.playCards(listOf(testCards[2]))
        }
        assertEquals("选择的牌不在手牌中", exception.message) // 根据你的实现内容改错误信息
    }

    @Test
    fun testCardsCount() {
        assertEquals(0, player.cardsCount())

        player.receiveCards(listOf(testCards[0]))
        assertEquals(1, player.cardsCount())

        player.receiveCards(testCards.subList(1, 3))
        assertEquals(3, player.cardsCount())
    }

    @Test
    fun testHasWon() {
        assertTrue(player.hasWon(), "初始状态应为赢（无牌）")

        player.receiveCards(testCards)
        assertFalse(player.hasWon(), "有牌时不应为赢")

        player.playCards(listOf(testCards[0]))
        player.playCards(listOf(testCards[1]))
        player.playCards(listOf(testCards[2]))
        assertTrue(player.hasWon(), "出完所有牌后应为赢")
    }

    @Test
    fun testHasCard() {
        assertFalse(player.hasCard(testCards[0]))

        player.receiveCards(listOf(testCards[0]))
        assertTrue(player.hasCard(testCards[0]))
        assertFalse(player.hasCard(testCards[1]))
    }

    @Test
    fun testPlayCardsWithValidPreviousHandType() {
        val lowerCard = Card(3, Suit.CLUB)
        val higherCard = Card(5, Suit.CLUB)

        val lowerHandType = HandType.from(listOf(lowerCard))

        player.receiveCards(listOf(higherCard))
        val result = player.playCards(listOf(higherCard), lowerHandType)

        assertEquals(listOf(higherCard), result)
        assertEquals(0, player.cardsCount())
    }

    @Test
    fun testPlayCardsWithInvalidPreviousHandType() {
        val lowerCard = Card(3, Suit.CLUB)
        val higherCard = Card(5, Suit.CLUB)

        val higherHandType = HandType.from(listOf(higherCard))

        player.receiveCards(listOf(lowerCard))

        val exception = assertThrows<IllegalArgumentException> {
            player.playCards(listOf(lowerCard), higherHandType)
        }
        assertEquals("出牌必须大于前一手牌", exception.message) // 根据你的实现内容改错误信息
    }
}
