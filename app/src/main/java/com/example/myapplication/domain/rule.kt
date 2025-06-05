package com.example.myapplication.domain

// 牌型枚举，按五张牌型的优先级排序
enum class HandType {
    // 五张牌型
    STRAIGHT_FLUSH, FOUR_OF_A_KIND, FULL_HOUSE, FLUSH, STRAIGHT,
    // 其他数量牌型
    THREE_OF_A_KIND, PAIR, SINGLE, INVALID
}

// 牌型评估结果，包含类型、关键点数和花色
data class HandResult(
    val type: HandType,
    val rank: Rank,
    val suit: Suit
)

// 评估单张、对子、三张、五张的牌型
fun evaluateHand(cards: List<Card>): HandResult {
    println("开始评估牌型: ${cards.joinToString { "${it.rank}${it.suit}" }}")
    
    // 根据牌数判断牌型
    val handType = when (cards.size) {
        1 -> HandType.SINGLE
        2 -> HandType.PAIR
        3 -> HandType.THREE_OF_A_KIND
        5 -> evaluateFiveCardHand(cards)
        else -> HandType.INVALID
    }
    
    println("评估结果: 牌型=$handType")
    
    // 获取牌型大小
    val rank = when (handType) {
        HandType.SINGLE -> cards[0].rank
        HandType.PAIR -> cards[0].rank
        HandType.THREE_OF_A_KIND -> cards[0].rank
        HandType.STRAIGHT_FLUSH,
        HandType.FOUR_OF_A_KIND,
        HandType.FULL_HOUSE,
        HandType.FLUSH,
        HandType.STRAIGHT -> getFiveCardHandRank(cards)
        else -> Rank.TWO
    }
    
    println("评估结果: 大小=$rank")
    return HandResult(handType, rank, cards[0].suit)
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
private fun evaluateFiveCardHand(cards: List<Card>): HandType {
    println("评估五张牌型: ${cards.joinToString { "${it.rank}${it.suit}" }}")
    
    val isFlush = cards.all { it.suit == cards[0].suit }
    val rankValues = cards.map { it.rank.value }.sorted()
    val isStraight = checkStraight(rankValues)
    
    println("五张牌型评估: 同花=$isFlush, 顺子=$isStraight")
    
    // 检查同花顺
    if (isFlush && isStraight) {
        println("五张牌型评估: 同花顺")
        return HandType.STRAIGHT_FLUSH
    }
    
    // 按点数分组
    val groups = cards.groupBy { it.rank }
    println("五张牌型评估: 点数分组=${groups.mapValues { it.value.size }}")
    
    // 检查四条
    if (groups.any { it.value.size == 4 }) {
        println("五张牌型评估: 四条")
        return HandType.FOUR_OF_A_KIND
    }
    
    // 检查葫芦
    if (groups.any { it.value.size == 3 } && groups.any { it.value.size == 2 }) {
        println("五张牌型评估: 葫芦")
        return HandType.FULL_HOUSE
    }
    
    // 检查同花
    if (isFlush) {
        println("五张牌型评估: 同花")
        return HandType.FLUSH
    }
    
    // 检查顺子
    if (isStraight) {
        println("五张牌型评估: 顺子")
        return HandType.STRAIGHT
    }
    
    println("五张牌型评估: 无效牌型")
    return HandType.INVALID
}

// 判断是否为顺子（包括A-2-3-4-5）
private fun checkStraight(rankValues: List<Int>): Boolean {
    return if (rankValues == listOf(1, 2, 3, 4, 5)) {
        true // A-2-3-4-5
    } else {
        rankValues.zipWithNext().all { (a, b) -> b - a == 1 }
    }
}

// 获取五张牌型的关键点数
private fun getFiveCardHandRank(cards: List<Card>): Rank {
    println("获取五张牌型关键点数: ${cards.joinToString { "${it.rank}${it.suit}" }}")
    
    val rankValues = cards.map { it.rank.value }.sorted()
    val groups = cards.groupBy { it.rank }
    
    // 同花顺和顺子：取最大点数
    if (cards.all { it.suit == cards[0].suit } || checkStraight(rankValues)) {
        val maxRank = if (rankValues == listOf(1, 2, 3, 4, 5)) {
            Rank.FIVE // A-2-3-4-5的最大点数是5
        } else {
            Rank.values().find { it.value == rankValues.last() }!!
        }
        println("同花顺/顺子关键点数: $maxRank")
        return maxRank
    }
    
    // 四条：取四张牌的点数
    val fourOfAKind = groups.entries.find { it.value.size == 4 }
    if (fourOfAKind != null) {
        println("四条关键点数: ${fourOfAKind.key}")
        return fourOfAKind.key
    }
    
    // 葫芦：取三张牌的点数
    val threeOfAKind = groups.entries.find { it.value.size == 3 }
    if (threeOfAKind != null) {
        println("葫芦关键点数: ${threeOfAKind.key}")
        return threeOfAKind.key
    }
    
    // 同花：取最大点数
    if (cards.all { it.suit == cards[0].suit }) {
        val maxRank = cards.maxBy { it.rank }.rank
        println("同花关键点数: $maxRank")
        return maxRank
    }
    
    // 默认返回最大点数
    val maxRank = cards.maxBy { it.rank }.rank
    println("默认关键点数: $maxRank")
    return maxRank
}

// 比较两手牌的大小
fun compareHands(hand1: List<Card>, hand2: List<Card>): Int {
    println("比较牌型:")
    println("牌型1: ${hand1.joinToString { "${it.rank}${it.suit}" }}")
    println("牌型2: ${hand2.joinToString { "${it.rank}${it.suit}" }}")
    
    // 评估两个牌型
    val result1 = evaluateHand(hand1)
    val result2 = evaluateHand(hand2)
    
    println("牌型1评估结果: 类型=${result1.type}, 大小=${result1.rank}")
    println("牌型2评估结果: 类型=${result2.type}, 大小=${result2.rank}")
    
    // 如果牌型不同，直接比较牌型大小
    if (result1.type != result2.type) {
        val comparison = result1.type.ordinal.compareTo(result2.type.ordinal)
        println("牌型不同，比较结果: $comparison")
        return comparison
    }
    
    // 牌型相同，比较大小
    val rankComparison = result1.rank.value.compareTo(result2.rank.value)
    println("牌型相同，比较大小: $rankComparison")
    return rankComparison
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
    println("验证三张: ${cards.joinToString { "${it.rank}${it.suit}" }}")
    val result = cards.size == 3 && cards.all { it.rank == cards[0].rank }
    println("三张验证结果: $result")
    return result
}

// 验证五张牌型
private fun isValidFiveCardHand(cards: List<Card>): Boolean {
    println("验证五张: ${cards.joinToString { "${it.rank}${it.suit}" }}")
    if (cards.size != 5) {
        println("五张验证失败: 牌数不是5张")
        return false
    }

    val isFlush = cards.all { it.suit == cards[0].suit }
    val rankValues = cards.map { it.rank.value }.sorted()
    val isStraight = checkStraight(rankValues)

    println("五张验证: 同花=$isFlush, 顺子=$isStraight")

    // 检查同花顺
    if (isFlush && isStraight) {
        println("五张验证: 同花顺")
        return true
    }

    // 按点数分组
    val groups = cards.groupBy { it.rank }
    println("五张验证: 点数分组=${groups.mapValues { it.value.size }}")

    // 检查四条（4张相同+1张不同）
    if (groups.any { it.value.size == 4 }) {
        println("五张验证: 四条")
        return true
    }

    // 检查葫芦（3张相同+2张相同）
    if (groups.any { it.value.size == 3 } && groups.any { it.value.size == 2 }) {
        println("五张验证: 葫芦")
        return true
    }

    // 检查同花
    if (isFlush) {
        println("五张验证: 同花")
        return true
    }

    // 检查顺子
    if (isStraight) {
        println("五张验证: 顺子")
        return true
    }

    println("五张验证: 无效牌型")
    return false
}

fun sortCards(cards: List<Card>): List<Card> {
    return cards.sortedWith(compareBy(
        { it.rank.value },  // 首先按点数数值排序
        { it.suit }         // 点数相同则按花色排序（使用Suit的compareTo实现）
    ))
}