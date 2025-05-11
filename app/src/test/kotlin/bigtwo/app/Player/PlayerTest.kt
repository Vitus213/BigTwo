package bigtwo.app.player

import bigtwo.app.model.Card
import bigtwo.app.model.Card.Suit
import bigtwo.app.model.HandType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayerTest {

    private lateinit var player: Player //创建一个玩家
    private lateinit var testCards: List<Card>

    @Before
    fun setUp() {
        player = Player("测试玩家")
        // 创建测试用的卡牌
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

    @Test(expected = IllegalArgumentException::class)
    fun testPlayCardsNotInHand() {
        player.receiveCards(testCards.subList(0, 2))
        // 尝试打出一张不在手牌中的牌
        player.playCards(listOf(testCards[2]))
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
        assertTrue("初始状态应为赢（无牌）", player.hasWon())

        player.receiveCards(testCards)
        assertFalse("有牌时不应为赢", player.hasWon())

        player.playCards(listOf(testCards[0]))
        player.playCards(listOf(testCards[1]))
        player.playCards(listOf(testCards[2]))
        assertTrue("出完所有牌后应为赢", player.hasWon())
    }

    @Test
    fun testHasCard() {
        assertFalse(player.hasCard(testCards[0]))

        player.receiveCards(listOf(testCards[0]))
        assertTrue(player.hasCard(testCards[0]))
        assertFalse(player.hasCard(testCards[1]))
    }

    // 下面两个测试需要模拟HandType类
    @Test
    fun testPlayCardsWithValidPreviousHandType() {
        // 注：此测试需要根据实际HandType实现调整
        // 创建低牌和高牌
        val lowerCard = Card(3, Suit.CLUB)
        val higherCard = Card(5, Suit.CLUB)

        // 假设HandType.from方法能正确创建手牌类型并支持比较
        val lowerHandType = HandType.from(listOf(lowerCard))

        player.receiveCards(listOf(higherCard))
        player.playCards(listOf(higherCard), lowerHandType)

        assertEquals(0, player.cardsCount())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPlayCardsWithInvalidPreviousHandType() {
        // 注：此测试需要根据实际HandType实现调整
        // 创建高牌和低牌
        val lowerCard = Card(3, Suit.CLUB)
        val higherCard = Card(5, Suit.CLUB)

        // 假设HandType.from方法能正确创建手牌类型并支持比较
        val higherHandType = HandType.from(listOf(higherCard))

        player.receiveCards(listOf(lowerCard))
        // 这应该会失败，因为低牌不能打高牌
        player.playCards(listOf(lowerCard), higherHandType)
    }
}