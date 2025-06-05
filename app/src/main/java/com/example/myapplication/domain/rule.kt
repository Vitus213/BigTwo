package com.example.myapplication.domain

// 牌型枚举，按五张牌型的优先级排序
enum class HandType {
    // 五张牌型
    STRAIGHT_FLUSH, FOUR_OF_A_KIND, FULL_HOUSE, FLUSH, STRAIGHT,
    // 其他数量牌型
    THREE_OF_A_KIND, PAIR, SINGLE
}

// 牌型评估结果，包含类型、关键点数和花色
data class HandResult(
    val type: HandType,
    val rank: Rank,
    val suit: Suit
)

// 评估单张、对子、三张、五张的牌型
fun evaluateHand(cards: List<Card>): HandResult {
    return when (cards.size) {
        1 -> evaluateSingle(cards)
        2 -> evaluatePair(cards)
        3 -> evaluateThreeOfAKind(cards)
        5 -> evaluateFiveCardHand(cards)
        else -> throw IllegalArgumentException("Unsupported number of cards: ${cards.size}")
    }
}

// 评估单张
private fun evaluateSingle(cards: List<Card>): HandResult {
    require(cards.size == 1)
    val card = cards[0]
    return HandResult(HandType.SINGLE, card.rank, card.suit)
}

// 评估对子
private fun evaluatePair(cards: List<Card>): HandResult {
    require(cards.size == 2)
    require(cards[0].rank == cards[1].rank)
    val maxSuit = cards.maxBy { it.suit }.suit
    return HandResult(HandType.PAIR, cards[0].rank, maxSuit)
}

// 评估三张
private fun evaluateThreeOfAKind(cards: List<Card>): HandResult {
    require(cards.size == 3)
    require(cards.all { it.rank == cards[0].rank })
    val maxSuit = cards.maxBy { it.suit }.suit
    return HandResult(HandType.THREE_OF_A_KIND, cards[0].rank, maxSuit)
}

// 评估五张牌型
private fun evaluateFiveCardHand(cards: List<Card>): HandResult {
    require(cards.size == 5)
    val isFlush = cards.all { it.suit == cards[0].suit }
    val rankValues = cards.map { it.rank.value }.sorted()
    val isStraight = checkStraight(rankValues)

    // 同花顺
    if (isFlush && isStraight) {
        val maxRank = getStraightMaxRank(rankValues)
        val maxCard = cards.filter { it.rank == maxRank }.maxBy { it.suit }
        return HandResult(HandType.STRAIGHT_FLUSH, maxRank, maxCard.suit)
    }

    // 按点数分组，降序排列
    val groups = cards.groupBy { it.rank }
        .values
        .sortedByDescending { it.size }
    println(groups)

    // 四条
    if (groups[0].size == 4) {
        val fourRank = groups[0][0].rank
        val maxSuit = groups[0].maxBy { it.suit }.suit
        return HandResult(HandType.FOUR_OF_A_KIND, fourRank, maxSuit)
    }

    // 葫芦（三张+对子）
    if (groups[0].size == 3 && groups[1].size == 2  || (groups[0].size == 2 && groups[1].size==3)) {
        val threeRank = groups[0][0].rank
        val maxSuit = groups[0].maxBy { it.suit }.suit
        return HandResult(HandType.FULL_HOUSE, threeRank, maxSuit)
    }

    // 同花
    if (isFlush) {
        val maxCard = cards.maxWith(compareBy(Card::rank, Card::suit))
        return HandResult(HandType.FLUSH, maxCard.rank, maxCard.suit)
    }

    // 顺子（杂顺）
    if (isStraight) {
        val maxRank = getStraightMaxRank(rankValues)
        val maxCard = cards.filter { it.rank == maxRank }.maxBy { it.suit }
        return HandResult(HandType.STRAIGHT, maxRank, maxCard.suit)
    }

    throw IllegalArgumentException("Invalid five-card hand")
}

// 判断是否为顺子（包括A-2-3-4-5）
private fun checkStraight(rankValues: List<Int>): Boolean {
    return if (rankValues == listOf(1, 2, 3, 4, 5)) {
        true // A-2-3-4-5
    } else {
        rankValues.zipWithNext().all { (a, b) -> b - a == 1 }
    }
}

// 获取顺子的最大点数
private fun getStraightMaxRank(rankValues: List<Int>): Rank {
    return if (rankValues == listOf(1, 2, 3, 4, 5)) {
        Rank.FIVE // A-2-3-4-5的最大点数是5
    } else {
        Rank.values().find { it.value == rankValues.last() }!!
    }
}

// 比较两手牌的大小
fun compareHands(hand1: List<Card>, hand2: List<Card>): Int {
    require(hand1.size == hand2.size ) { "Hands must have the same number of cards" }

    val result1 = evaluateHand(hand1)
    val result2 = evaluateHand(hand2)


    // 五张牌型优先级顺序
    val fiveCardOrder = listOf(
        HandType.STRAIGHT_FLUSH,
        HandType.FOUR_OF_A_KIND,
        HandType.FULL_HOUSE,
        HandType.FLUSH,
        HandType.STRAIGHT
    )

    return when (hand1.size) {
        1, 2, 3 -> {
            // 单张、对子、三张直接比较点数和花色
            compareBy<HandResult>({ it.rank.value }, { it.suit }).compare(result1, result2)
        }
        5 -> {
            // 先比较五张牌型优先级
            val typeCompare = fiveCardOrder.indexOf(result1.type)
                .compareTo(fiveCardOrder.indexOf(result2.type))
            if (typeCompare != 0) typeCompare
            else compareBy<HandResult>({ it.rank.value }, { it.suit }).compare(result1, result2)
        }
        else -> throw IllegalArgumentException("Unsupported hand size")
    }
}

// Suit比较（按题目顺序：方块 < 梅花 < 红桃 < 黑桃）
operator fun Suit.compareTo(other: Suit): Int {
    val order = listOf(Suit.DIAMONDS, Suit.CLUBS, Suit.HEARTS, Suit.SPADES)
    return order.indexOf(this).compareTo(order.indexOf(other))
}

// 验证牌型是否有效的方法
fun isValidHand(cards: List<Card>): Boolean {
    return when (cards.size) {
        1 -> isValidSingle(cards)
        2 -> isValidPair(cards)
        3 -> isValidThreeOfAKind(cards)
        5 -> isValidFiveCardHand(cards)
        else -> false // 不支持其他数量的牌
    }
}

// 验证单张牌（总是有效）
private fun isValidSingle(cards: List<Card>): Boolean {
    return cards.size == 1
}

// 验证对子：必须两张牌且点数相同
private fun isValidPair(cards: List<Card>): Boolean {
    return cards.size == 2 && cards[0].rank == cards[1].rank
}

// 验证三张：必须三张牌且点数相同
private fun isValidThreeOfAKind(cards: List<Card>): Boolean {
    return cards.size == 3 && cards.all { it.rank == cards[0].rank }
}

// 验证五张牌型
private fun isValidFiveCardHand(cards: List<Card>): Boolean {
    if (cards.size != 5) return false

    val isFlush = cards.all { it.suit == cards[0].suit }
    val rankValues = cards.map { it.rank.value }.sorted()
    val isStraight = checkStraight(rankValues)

    // 检查同花顺
    if (isFlush && isStraight) return true

    // 按点数分组
    val groups = cards.groupBy { it.rank }

    // 检查四条（4张相同+1张不同）
    if (groups.any { it.value.size == 4 }) return true

    // 检查葫芦（3张相同+2张相同）
    if (groups.any { it.value.size == 3 } && groups.any { it.value.size == 2 }) return true

    // 检查同花
    if (isFlush) return true

    // 检查顺子
    if (isStraight) return true

    return false
}

fun sortCards(cards: List<Card>): List<Card> {
    return cards.sortedWith(compareBy(
        { it.rank.value },  // 首先按点数数值排序
        { it.suit }         // 点数相同则按花色排序（使用Suit的compareTo实现）
    ))
}