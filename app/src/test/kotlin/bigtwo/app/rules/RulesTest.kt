package bigtwo.app.rules

import bigtwo.app.model.Card
import bigtwo.app.model.Card.Suit.*
import bigtwo.app.model.HandType
import bigtwo.app.player.Player
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RulesTest {

    private lateinit var southernRules: Rules
    private lateinit var northernRules: Rules

    @Before
    fun setup() {
        // 初始化南方与北方规则对象
        southernRules = Rules(RuleVariant.SOUTHERN)
        northernRules = Rules(RuleVariant.NORTHERN)
    }

    @Test
    fun testCompareHands_SameType() {
        // 测试相同类型牌型比较（单张大小）
        val hand1 = listOf(Card(11, CLUB)) // J
        val hand2 = listOf(Card(10, SPADE)) // 10

        val result = southernRules.compareHands(hand1, hand2)
        assertTrue("J 应该大于 10", result > 0)
    }

    @Test
    fun testCompareHands_DifferentType_Southern() {
        // 南方规则中：Flush > Straight
        val straight = listOf(Card(3, CLUB), Card(4, DIAMOND), Card(5, HEART), Card(6, SPADE), Card(7, CLUB))
        val flush = listOf(Card(2, HEART), Card(5, HEART), Card(8, HEART), Card(10, HEART), Card(13, HEART))

        val result = southernRules.compareHands(flush,straight)
        assertTrue("南方规则中同花应胜过顺子", result == 1)
    }

    @Test
    fun testCompareHands_DifferentType_Northern() {
        // 北方规则中：Flush > Full House
        val fullHouse = listOf(Card(9, HEART), Card(9, CLUB), Card(9, DIAMOND), Card(5, CLUB), Card(5, SPADE))
        val flush = listOf(Card(2, CLUB), Card(5, CLUB), Card(8, CLUB), Card(10, CLUB), Card(12, CLUB))

        val result = northernRules.compareHands(flush,fullHouse)
        assertTrue("北方规则中同花应胜过葫芦", result == 1)
    }

    @Test
    fun testIsValidPlay_FirstPlay_Valid() {
        // 测试首轮出牌（无前一手）是否合法
        val cards = listOf(Card(4, CLUB))
        assertTrue(southernRules.isValidPlay(cards, null))
    }

    @Test
    fun testIsValidPlay_DifferentShape_Invalid() {
        // 前手为对子，当前为单张，不合法
        val previous = listOf(Card(5, HEART), Card(5, CLUB))
        val current = listOf(Card(4, HEART))
        assertFalse(southernRules.isValidPlay(current, previous))
    }

    @Test
    fun testIsValidPlay_CompareSize_Valid() {
        // 同为对子，后手较大
        val previous = listOf(Card(5, HEART), Card(5, CLUB))
        val current = listOf(Card(6, SPADE), Card(6, DIAMOND))
        assertTrue(southernRules.isValidPlay(current, previous))
    }

    @Test
    fun testHasStartingCard() {
        val player = Player("Alice")
        player.receiveCards(listOf(Card(3, DIAMOND), Card(10, HEART)))
        assertTrue(southernRules.hasStartingCard(player))
    }

    @Test
    fun testCalculateSouthernScore_Basic() {
        val player1 = Player("P1")
        val player2 = Player("P2")

        player1.receiveCards(List(9) { Card(it + 2, CLUB) })  // 9张牌
        player2.receiveCards(List(3) { Card(it + 2, DIAMOND) }) // 3张牌

        val scores = southernRules.calculateSouthernScore(listOf(player1, player2))
        assertEquals("应有两个玩家得分", 2, scores.size)
        print("${scores[player1]}---${scores[player2]}")
        assertEquals("总得分应为-42", -42, scores.values.sum())
    }

    @Test
    fun testCalculateSouthernScore_WithSpade2Double() {
        val player = Player("P")
        // 玩家有8张牌且有黑桃2，应触发加倍
        player.receiveCards(
            listOf(
                Card(2, SPADE), Card(3, SPADE), Card(4, CLUB),
                Card(5, DIAMOND), Card(6, HEART), Card(7, CLUB),
                Card(8, CLUB), Card(9, HEART)
            )
        )
        val points = southernRules.calculateSouthernScore(listOf(player))[player]!!
        assertTrue("应计入加倍后的牌分", points < 0)
    }

    @Test
    fun testCalculateNorthernScore_WithPenalties() {
        val p1 = Player("Full13")
        val p2 = Player("Winner")

        p1.receiveCards(
            listOf(
                Card(2, SPADE), Card(3, SPADE), Card(4, SPADE), Card(5, SPADE),
                Card(6, SPADE), Card(7, SPADE), Card(8, SPADE), Card(9, SPADE),
                Card(10, SPADE), Card(11, SPADE), Card(12, SPADE), Card(13, SPADE),
                Card(14, SPADE)
            )
        ) // 满手13张 + 黑桃2，触发炒地皮和加倍

        p2.receiveCards(listOf()) // 出完牌的赢家

        val scores = northernRules.calculateNorthernScore(listOf(p1, p2))
        assertEquals(-312, scores[p1])
        assertEquals(104, scores[p2])
    }

    @Test
    fun testCalculateNorthernScore_NormalPlay() {
        val p1 = Player("A")
        val p2 = Player("B")
        p1.receiveCards(List(4) { Card(it + 2, CLUB) })
        p2.receiveCards(List(2) { Card(it + 2, DIAMOND) })

        val scores = northernRules.calculateNorthernScore(listOf(p1, p2))
        assertEquals("总得分应为-12", -12, scores.values.sum())
    }

}
