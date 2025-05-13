package bigtwo.app.rules

import bigtwo.app.model.Card
import bigtwo.app.model.Card.Suit
import bigtwo.app.model.HandType
import bigtwo.app.player.Player

// 规则类型枚举
enum class RuleVariant {
    SOUTHERN, // 南方规则
    NORTHERN  // 北方规则
}
// 根据牌型获取类别
private fun getCategory(type: HandType.Type): Int {
    return when (type) {
        HandType.Type.SINGLE -> 1
        HandType.Type.PAIR -> 2
        HandType.Type.TRIPLE -> 3
        HandType.Type.STRAIGHT -> 5
        HandType.Type.FLUSH -> 5
        HandType.Type.FULL_HOUSE -> 5
        HandType.Type.FOUR_OF_A_KIND-> 5
        HandType.Type.STRAIGHT_FLUSH -> 5
    }
}
class Rules(val variant: RuleVariant = RuleVariant.SOUTHERN) {

    // 判断牌型大小顺序
    fun compareHands(hand1: List<Card>, hand2: List<Card>): Int {
        val type1 = HandType.from(hand1)
        val type2 = HandType.from(hand2)

        // 不同牌型比较
        if (type1.type != type2.type) {
            return compareHandTypes(type1, type2)
        }
        // 同类型牌型比较 - 按最大牌点数和花色
        return type1.compareTo(type2)
    }

    // 比较不同牌型 (南北方规则略有不同)
    private fun compareHandTypes(type1: HandType, type2: HandType): Int {
        // 使用HandType.Type枚举作为键，而不是字符串
        val southernOrder = mapOf(
            HandType.Type.STRAIGHT_FLUSH to 5,
            HandType.Type.FOUR_OF_A_KIND to 4,
            HandType.Type.FULL_HOUSE to 3,
            HandType.Type.FLUSH to 2,
            HandType.Type.STRAIGHT to 1,
            HandType.Type.TRIPLE to 0,
            HandType.Type.PAIR to 0,
            HandType.Type.SINGLE to 0
        )

        val northernOrder = mapOf(
            HandType.Type.STRAIGHT_FLUSH to 5,
            HandType.Type.FOUR_OF_A_KIND to 4,
            HandType.Type.FLUSH to 3,          // 北方规则中同花>葫芦
            HandType.Type.FULL_HOUSE to 2,
            HandType.Type.STRAIGHT to 1,
            HandType.Type.TRIPLE to 0,
            HandType.Type.PAIR to 0,
            HandType.Type.SINGLE to 0
        )

        val order = if (variant == RuleVariant.SOUTHERN) southernOrder else northernOrder

        // 使用type1.type访问枚举类型
        return order[type1.type]!!.compareTo(order[type2.type]!!)
    }


    // 计算南方规则得分
    fun calculateSouthernScore(players: List<Player>): Map<Player, Int> {
        val scores = mutableMapOf<Player, Int>()
        val cardPoints = players.associateWith { calculateCardPoints(it, true) }

        players.forEach { player ->
            val otherPoints = players.filter { it != player }.sumOf { cardPoints[it]!! }
            val playerPoints = cardPoints[player]!!
            scores[player] = otherPoints - 3 * playerPoints
        }

        return scores
    }

    // 计算北方规则得分
    fun calculateNorthernScore(players: List<Player>,): Map<Player, Int> {
        val scores = mutableMapOf<Player, Int>()
        // 使用 associateWith 创建初始映射，参数 false 表示使用北方规则
        val cardPoints = players.associateWith { calculateCardPoints(it, false) }.toMutableMap()

        players.forEach { player ->
            var points = cardPoints[player]!!

            // 黑桃2加倍
            if (points >= 8 && player.hasCard(Card(2, Suit.SPADE))) {
                points *= 2
            }

            // 炒地皮惩罚 (全程未出牌)
            if (player.cardsCount()==13) {
                points *= 4
            }

            cardPoints[player] = points
        }

        players.forEach { player ->
            val otherPoints = players.filter { it != player }.sumOf { cardPoints[it]!! }
            val playerPoints = cardPoints[player]!!
            scores[player] = otherPoints - 3 * playerPoints
        }

        return scores
    }

    // 计算牌分
    private fun calculateCardPoints(player: Player, isSouthern: Boolean): Int {
        val n = player.cardsCount()

        if (isSouthern) {
            // 南方计分规则
            return when {
                n < 8 -> n
                n < 10 -> 2 * n
                n < 13 -> 3 * n
                else -> 4 * n
            } * if (n >= 8 && player.hasCard(Card(2, Suit.SPADE))) 2 else 1
        } else {
            // 北方计分规则
            return n
        }
    }

    // 检查玩家是否持有方块3
    fun hasStartingCard(player: Player): Boolean {
        return player.hasCard(Card(3, Suit.DIAMOND))
    }

    // 验证出牌是否合法
    fun isValidPlay(cards: List<Card>, previousHand: List<Card>?): Boolean {
        // 首次出牌或前面都过
        if (previousHand == null || previousHand.isEmpty()) {
            try{
                HandType.from(cards)
                return true
            }catch (e: IllegalArgumentException) {
                return false
            }
        }

        // 检查牌型是否相同
        val currentType = HandType.from(cards)
        val previousType = HandType.from(previousHand)
        if (cards.size != previousHand.size || getCategory(currentType.type) != getCategory(previousType.type)) {
            return false
        }
     //   print("比较牌型结束，开始比较大小\n")
        // 比较大小
        return compareHands(cards, previousHand) > 0
    }

}

