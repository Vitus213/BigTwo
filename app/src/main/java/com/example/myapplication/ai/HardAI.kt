package com.example.myapplication.ai

import com.example.myapplication.domain.Card
import com.example.myapplication.domain.HandType
import com.example.myapplication.domain.Rank
import com.example.myapplication.domain.Suit
import com.example.myapplication.domain.compareHands
import com.example.myapplication.domain.evaluateHand
import kotlin.random.Random

// 进阶 AI (HardAI) 的出牌策略 - 基于现有 rule.kt 规则
object HardAI {

    /**
     * 困难人机出牌的核心逻辑。
     * @param hand 当前 AI 玩家的手牌。
     * @param previousHand 上一个玩家打出的牌组。
     * @return AI 决定打出的牌组，如果选择过牌则返回空列表。
     */
    fun play(hand: List<Card>, previousHand: List<Card>): List<Card> {
        // 先对手牌进行排序，便于处理
        val sortedHand = hand.sortedWith(compareBy(Card::rank, Card::suit))

        // 如果是首个出牌
        if (previousHand.isEmpty()) {
            return chooseOpeningPlay(sortedHand)
        } else {
            // 如果是压牌
            return chooseCounterPlay(sortedHand, previousHand)
        }
    }

    /**
     * 决策 AI 的开局出牌。
     * 目标：
     * 1. 如果 AI 手中有方块3，则出包含方块3的牌型（优先出小的）。
     * 2. 如果 AI 手中没有方块3，则按通用智能策略出牌（由 GameManager 判断是否有效）。
     * @param hand AI 玩家的手牌。
     * @return AI 决定打出的牌组。
     */
    private fun chooseOpeningPlay(hand: List<Card>): List<Card> {
        val diamond3 = Card(Suit.DIAMONDS, Rank.THREE)
        val hasDiamond3 = hand.contains(diamond3)

        // 生成所有可能的合法牌型
        val allPossiblePlays = generateAllPossiblePlays(hand)
            // 过滤掉 evaluateHand 无法处理的牌型 (通过捕获异常)
            .filter { cards ->
                try {
                    evaluateHand(cards)
                    true // 如果 evaluateHand 成功，则为合法牌型
                } catch (e: IllegalArgumentException) {
                    false // 否则为非法牌型
                }
            }

        val candidatePlays: List<List<Card>>

        if (hasDiamond3) {
            // 如果有方块3，只考虑包含方块3的牌型
            candidatePlays = allPossiblePlays.filter { it.contains(diamond3) }
            // 如果没有包含方块3的合法牌型 (这应该不会发生，至少可以出单张方块3)
            if (candidatePlays.isEmpty()) {
                println("HardAI: 手中有方块3但未能找到包含方块3的合法牌型！尝试出单张方块3作为兜底。")
                // 兜底方案，直接出单张方块3，前提是手中有方块3且单张合法
                if (hand.contains(diamond3)) {
                    return listOf(diamond3)
                }
                return emptyList() // 如果连单张方块3都出不了（不合法），则返回空
            }
        } else {
            // 如果没有方块3，则按通用智能策略出牌 (GameManager 会进行后续验证)
            candidatePlays = allPossiblePlays
        }

        // 对候选牌型进行策略性排序
        val sortedCandidatePlays = candidatePlays.sortedWith(compareBy<List<Card>> { cards ->
            val handType = evaluateHand(cards).type
            val rankValue = evaluateHand(cards).rank.value

            // 优先出普通牌型，将炸弹和同花顺等强牌“推后”
            when (handType) {
                HandType.BOMB, HandType.FOUR_OF_A_KIND, HandType.STRAIGHT_FLUSH -> {
                    // 如果手牌数量很少，或者此牌型刚好可以清牌，则优先级提高
                    if (hand.size <= cards.size + 1 || hand.size <= 5) {
                        // 优先级设为与普通牌型接近，但仍然略高，以便在清牌时优先考虑
                        handType.ordinal - 100 // 负值表示更优先，但仍低于实际的最低优先级
                    } else {
                        // 否则优先级降低，尽可能保留
                        handType.ordinal + 100 // 正值表示更不优先
                    }
                }
                else -> handType.ordinal // 普通牌型按正常优先级排序
            }
        }.thenBy { cards ->
            evaluateHand(cards).rank.value // 点数小的优先
        }.thenBy { cards ->
            cards.size // 牌数量小的优先
        })

        // 返回最佳开局牌型
        return sortedCandidatePlays.firstOrNull() ?: emptyList()
    }

    /**
     * 决策 AI 的压牌出牌。
     * @param hand AI 玩家的手牌。
     * @param previousHand 上一个玩家打出的牌组。
     * @param opponentHandSizes 可选：其他玩家的剩余手牌数量，用于更智能判断。
     * @return AI 决定打出的牌组，如果选择过牌则返回空列表。
     */
    private fun chooseCounterPlay(hand: List<Card>, previousHand: List<Card>,
                                  opponentHandSizes: List<Int> = emptyList()): List<Card> {
        val previousResult = evaluateHand(previousHand)
        val isPreviousBombOrSF = (previousResult.type == HandType.BOMB ||
                previousResult.type == HandType.FOUR_OF_A_KIND ||
                previousResult.type == HandType.STRAIGHT_FLUSH)

        val allPossiblePlays = generateAllPossiblePlays(hand)
            // 过滤掉 evaluateHand 无法处理的牌型 (通过捕获异常)
            .filter { cards ->
                try {
                    evaluateHand(cards)
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }

        // 筛选出所有能压过上家的合法牌型
        val validCounterPlays = allPossiblePlays
            .filter { candidate ->
                val candidateResult = evaluateHand(candidate)
                val candidateType = candidateResult.type

                if (candidate.size != previousHand.size) {
                    // 只有炸弹或同花顺才能压不同数量的牌
                    (candidateType == HandType.BOMB || candidateType == HandType.FOUR_OF_A_KIND || candidateType == HandType.STRAIGHT_FLUSH) &&
                            compareHands(candidate, previousHand) > 0
                } else {
                    // 数量一致，直接比较大小
                    compareHands(candidate, previousHand) > 0
                }
            }

        // 根据策略对能压过的牌型进行排序
        val sortedValidCounterPlays = validCounterPlays.sortedWith(compareBy<List<Card>> { cards ->
            val playResult = evaluateHand(cards)
            val playType = playResult.type
            val playRankValue = playResult.rank.value

            // 1. 如果此牌能清空手牌，优先级最高
            if (hand.size == cards.size) {
                return@compareBy -200 // 非常优先
            }

            // 2. 如果上家是炸弹/同花顺，必须用大牌压，优先级提高
            if (isPreviousBombOrSF) {
                return@compareBy playType.ordinal // 直接按牌型优先级和点数来选最小的
            }

            // 3. 否则，优先出普通牌型，将炸弹/四带一/同花顺推后
            when (playType) {
                HandType.BOMB, HandType.FOUR_OF_A_KIND, HandType.STRAIGHT_FLUSH -> {
                    // 对方牌数非常少 (例如只有1-2张)，考虑用大牌终结
                    val opponentMinCards = opponentHandSizes.minOrNull() ?: Int.MAX_VALUE
                    if (opponentMinCards <= 2 || hand.size <= 5 || Random.nextDouble() < 0.2) { // 20% 几率随机使用
                        playType.ordinal - 50 // 略微提高优先级，但在普通牌型之后
                    } else {
                        playType.ordinal + 100 // 降低优先级，尽量保留
                    }
                }
                else -> playType.ordinal // 普通牌型按正常优先级排序
            }
        }.thenBy { cards ->
            evaluateHand(cards).rank.value // 点数小的优先
        }.thenBy { cards ->
            cards.size // 牌数量小的优先
        })

        // 最终决策：
        // 1. 如果有任何能清牌的牌型，立即打出
        val winningPlay = sortedValidCounterPlays.firstOrNull { hand.size == it.size }
        if (winningPlay != null) {
            println("HardAI: 找到能清牌的牌型，立即打出！")
            return winningPlay
        }

        // 2. 优先出非炸弹/非同花顺的牌型
        val bestRegularPlay = sortedValidCounterPlays.firstOrNull {
            val type = evaluateHand(it).type
            type != HandType.BOMB && type != HandType.FOUR_OF_A_KIND && type != HandType.STRAIGHT_FLUSH
        }

        if (bestRegularPlay != null) {
            println("HardAI: 选择最佳常规牌型压制。")
            return bestRegularPlay
        }

        // 3. 如果只有炸弹/四带一/同花顺能压制，并且满足使用条件，则打出
        val bestBigPlay = sortedValidCounterPlays.firstOrNull {
            val type = evaluateHand(it).type
            type == HandType.BOMB || type == HandType.FOUR_OF_A_KIND || type == HandType.STRAIGHT_FLUSH
        }

        if (bestBigPlay != null) {
            println("HardAI: 考虑使用大牌压制。")
            return bestBigPlay
        }

        // 4. 如果没有任何牌能压制，或者所有能压制的都是大牌且不值得压制，则选择过牌
        println("HardAI 选择过牌")
        return emptyList()
    }


    // --- 辅助函数：生成所有可能的牌型组合 ---

    private fun generateAllPossiblePlays(hand: List<Card>): List<List<Card>> {
        val plays = mutableListOf<List<Card>>()

        plays.addAll(hand.map { listOf(it) })
        plays.addAll(generatePairs(hand))
        plays.addAll(generateThreeOfAKinds(hand))
        plays.addAll(generateStraights(hand))
        plays.addAll(generateFlushes(hand))
        plays.addAll(generateFullHouses(hand))
        plays.addAll(generateBombs(hand))
        plays.addAll(generateFourOfAKindsWithKicker(hand))
        plays.addAll(generateStraightFlushes(hand))

        // 过滤掉 evaluateHand 无法处理的牌型 (通过捕获异常)
        return plays.filter { cards ->
            try {
                evaluateHand(cards)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }
    }

    private fun generatePairs(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.rank }.values.flatMap { group ->
            group.combinations(2)
        }
    }

    private fun generateThreeOfAKinds(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.rank }.values.flatMap { group ->
            group.combinations(3)
        }
    }

    private fun generateBombs(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.rank }
            .filter { it.value.size >= 4 }
            .map { it.value.take(4) }
    }

    private fun generateFourOfAKindsWithKicker(hand: List<Card>): List<List<Card>> {
        val fourOfAKinds = generateBombs(hand)
        val plays = mutableListOf<List<Card>>()

        for (fourCards in fourOfAKinds) {
            val remainingCards = hand.filter { !fourCards.contains(it) }
            if (remainingCards.isNotEmpty()) {
                for (kicker in remainingCards) {
                    val combined = fourCards + kicker
                    if (combined.size == 5) { // 检查数量是否为5
                        try {
                            // 再次验证 evaluateHand 确实识别为 FOUR_OF_A_KIND
                            if (evaluateHand(combined).type == HandType.FOUR_OF_A_KIND) {
                                plays.add(combined)
                            }
                        } catch (e: IllegalArgumentException) {
                            // 捕获异常，表示这不是合法的四带一
                        }
                    }
                }
            }
        }
        return plays
    }


    private fun generateFullHouses(hand: List<Card>): List<List<Card>> {
        val groups = hand.groupBy { it.rank }
        val threes = groups.values.filter { it.size >= 3 }.flatMap { it.combinations(3) }
        val pairs = groups.values.filter { it.size >= 2 }.flatMap { it.combinations(2) }

        return threes.flatMap { three ->
            pairs.filter { pair ->
                pair[0].rank != three[0].rank
            }.map { pair ->
                val combined = three + pair
                try {
                    // 再次验证，确保 evaluateHand 确实识别为 FULL_HOUSE
                    if (evaluateHand(combined).type == HandType.FULL_HOUSE) {
                        combined
                    } else {
                        emptyList()
                    }
                } catch (e: IllegalArgumentException) {
                    emptyList() // 捕获异常，表示这不是合法的葫芦
                }
            }
        }.filter { it.isNotEmpty() }
    }

    private fun generateFlushes(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.suit }
            .filter { it.value.size >= 5 }
            .flatMap { (_, suitCards) ->
                suitCards.combinations(5)
            }.filter { cards ->
                try {
                    evaluateHand(cards).type == HandType.FLUSH // 确保确实是同花
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
    }

    private fun generateStraights(hand: List<Card>): List<List<Card>> {
        val uniqueRanks = hand.distinctBy { it.rank }
            .sortedBy { it.rank.value }

        val straights = mutableListOf<List<Card>>()

        for (i in 0..(uniqueRanks.size - 5)) {
            val segment = uniqueRanks.subList(i, i + 5)
            val values = segment.map { it.rank.value }

            if (values.zipWithNext().all { (a, b) -> b - a == 1 }) {
                val straightCards = mutableListOf<Card>()
                for (rankCard in segment) {
                    val cardInHand = hand.firstOrNull { it.rank == rankCard.rank }
                    if (cardInHand != null) {
                        straightCards.add(cardInHand)
                    } else {
                        straightCards.clear()
                        break
                    }
                }
                if (straightCards.size == 5) {
                    try {
                        // 再次通过 evaluateHand 验证，确保它确实被识别为顺子
                        if (evaluateHand(straightCards).type == HandType.STRAIGHT) {
                            straights.add(straightCards)
                        }
                    } catch (e: IllegalArgumentException) {
                        // 捕获异常，表示这不是合法的顺子
                    }
                }
            }
        }
        return straights
    }

    private fun generateStraightFlushes(hand: List<Card>): List<List<Card>> {
        return hand.groupBy { it.suit }
            .flatMap { (_, suitCards) ->
                generateStraights(suitCards).filter { cards ->
                    cards.size == 5 && evaluateHand(cards).type == HandType.STRAIGHT_FLUSH
                }
            }
    }

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
}