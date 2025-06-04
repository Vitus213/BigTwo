package com.example.myapplication.ai

import com.example.myapplication.domain.Card
import com.example.myapplication.domain.HandType
import com.example.myapplication.domain.Rank
import com.example.myapplication.domain.Suit
import com.example.myapplication.domain.compareHands
import com.example.myapplication.domain.evaluateHand
import kotlin.math.min

// 生成k元素组合的扩展函数
fun <T> List<T>.combinations(k: Int): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (k > this.size) return emptyList()
    if (k == this.size) return listOf(this.toList())

    val first = this[0]
    val subCombinations = this.subList(1, this.size).combinations(k - 1)
    val withFirst = subCombinations.map { listOf(first) + it }

    val withoutFirst = this.subList(1, this.size).combinations(k)

    return withFirst + withoutFirst
}

// 生成对子组合
private fun generatePairs(hand: List<Card>): List<List<Card>> {
    return hand.groupBy { it.rank }.values.flatMap { group ->
        group.combinations(2)
    }
}

// 生成三张组合
private fun generateThreeOfAKinds(hand: List<Card>): List<List<Card>> {
    return hand.groupBy { it.rank }.values.flatMap { group ->
        group.combinations(3)
    }
}

// 生成合法候选牌组
private fun generateValidCandidates(hand: List<Card>, type: HandType): List<List<Card>> {
    return when (type) {
        HandType.SINGLE -> hand.map { listOf(it) }
        HandType.PAIR -> generatePairs(hand)
        HandType.THREE_OF_A_KIND -> generateThreeOfAKinds(hand)
        HandType.STRAIGHT_FLUSH -> generateStraightFlushes(hand)
        HandType.FOUR_OF_A_KIND -> generateFourOfAKinds(hand)
        HandType.FULL_HOUSE -> generateFullHouses(hand)
        HandType.FLUSH -> generateFlushes(hand)
        HandType.STRAIGHT -> generateStraights(hand)
        else -> emptyList()
    }
}

// AI出牌主逻辑
fun aiPlay(hand: List<Card>, previousHand: List<Card>): List<Card> {
    if (previousHand.isEmpty()) {
        // 优先出方块3
        val diamond3 = hand.firstOrNull { it.rank == Rank.THREE && it.suit == Suit.DIAMONDS }
        if (diamond3 != null) return listOf(diamond3)

        // 否则出最小单张
        return hand.sortedWith(compareBy(Card::rank, Card::suit)).firstOrNull()?.let { listOf(it) } ?: emptyList()
    } else {
        // 验证上家牌型
        val previousResult = evaluateHand(previousHand)

        // 生成候选组合
        val candidates = generateValidCandidates(hand, previousResult.type)

        // 过滤出能压过的组合
        val validCandidates = candidates.filter { candidate ->
            compareHands(candidate, previousHand) > 0
        }

        // 找到最小能压过的组合
        return validCandidates.minWithOrNull(Comparator { a, b ->
            compareHands(a, b)
        }) ?: emptyList()
    }
}

// 辅助比较函数
private val cardComparator = compareBy<Card>({ it.rank.value }, { it.suit })

private fun generateStraightFlushes(hand: List<Card>): List<List<Card>> {
    return hand.groupBy { it.suit }
        .flatMap { (_, suitCards) ->
            generateStraights(suitCards).filter { cards ->
                evaluateHand(cards).type == HandType.STRAIGHT_FLUSH
            }
        }
}

// 生成四条
private fun generateFourOfAKinds(hand: List<Card>): List<List<Card>> {
    return hand.groupBy { it.rank }
        .filter { it.value.size >= 4 }
        .flatMap { (_, fourCards) ->
            val fourOfAKind = fourCards.take(4)
            val kickers = hand.filterNot { it in fourOfAKind }
            kickers.map { kicker -> fourOfAKind + kicker }
        }
}

// 生成葫芦
private fun generateFullHouses(hand: List<Card>): List<List<Card>> {
    val groups = hand.groupBy { it.rank }
    val threes = groups.values.filter { it.size >= 3 }.flatMap { it.combinations(3) }
    val pairs = groups.values.filter { it.size >= 2 }.flatMap { it.combinations(2) }

    return threes.flatMap { three ->
        pairs.filter { pair ->
            pair[0].rank != three[0].rank
        }.map { pair ->
            three + pair
        }
    }
}

// 生成同花
private fun generateFlushes(hand: List<Card>): List<List<Card>> {
    return hand.groupBy { it.suit }
        .filter { it.value.size >= 5 }
        .flatMap { (_, suitCards) ->
            suitCards.combinations(5)
        }
}

// 生成顺子（包含特殊A-2-3-4-5）
private fun generateStraights(hand: List<Card>): List<List<Card>> {
    val uniqueRanks = hand.distinctBy { it.rank }
        .sortedBy { it.rank.value }

    val straights = mutableListOf<List<Card>>()
    val rankValues = uniqueRanks.map { it.rank.value }

    // 处理特殊顺子 A-2-3-4-5
    if (rankValues.containsAll(listOf(1, 2, 3, 4, 5))) {
        val lowStraight = listOf(1, 2, 3, 4, 5).map { value ->
            uniqueRanks.first { it.rank.value == value }
        }
        straights.add(lowStraight)
    }

    // 生成常规顺子
    for (i in 0..(uniqueRanks.size - 5)) {
        val segment = uniqueRanks.subList(i, i + 5)
        val values = segment.map { it.rank.value }

        if (values.zipWithNext().all { (a, b) -> b - a == 1 }) {
            straights.add(segment)
        }
    }

    return straights
}