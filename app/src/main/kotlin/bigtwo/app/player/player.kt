package bigtwo.app.player

import bigtwo.app.model.Card
import bigtwo.app.model.HandType
import bigtwo.app.rules.Rules
import bigtwo.app.utils.combinations

interface PlayerInterface {
    val name: String
    val isHuman: Boolean

    fun receiveCards(newCards: List<Card>)
    fun getCards(): List<Card>
    fun playCards(selectedCards: List<Card>, previousHandType: HandType? = null): List<Card>
    fun hasCard(card: Card): Boolean
    fun cardsCount(): Int
    fun hasWon(): Boolean
}

class Player(override val name: String, override val isHuman: Boolean = true) : PlayerInterface {

    // 玩家手牌
    private val cards = mutableListOf<Card>()

    // 新增：存储当前玩家的所有合法牌型
    private val handTypeList = mutableListOf<HandType>()

    // 接收一组牌
    override fun receiveCards(newCards: List<Card>) {
        cards.addAll(newCards)
        sortCards() // 对手牌进行排序
    }

    // 获取当前手牌
    override fun getCards(): List<Card> = cards.toList()

    // 出牌
    override fun playCards(selectedCards: List<Card>, previousHandType: HandType?): List<Card> {
        // 验证选中的牌确实在手牌中
        require(cards.containsAll(selectedCards)) { "选择的牌不在手牌中" }
        // 验证是否符合牌型
        val currentHandType = HandType.from(selectedCards)
        // 如果有前一手牌，需要进行比较
        if (previousHandType != null) {
            // 验证牌型是否相同
            require(currentHandType.javaClass == previousHandType.javaClass) {
                "出牌类型必须与前一手牌相同"
            }

            // 验证当前牌是否大于前一手牌
            require(currentHandType > previousHandType) {
                "出牌必须大于前一手牌"
            }
        }
        // 从手牌中移除打出的牌
        cards.removeAll(selectedCards)

        // 新增：出牌后更新牌型列表
        updateHandTypeList(null)

        return selectedCards
    }

    // 对手牌进行排序
    private fun sortCards() {
        cards.sortBy { it.rank * 10 + it.suit.ordinal } // 进行排序，从小到大
    }

    // 检查玩家是否有特定的牌
    override fun hasCard(card: Card): Boolean = cards.contains(card)

    // 剩余牌数
    override fun cardsCount(): Int = cards.size

    // 判断是否已经出完所有牌
    override fun hasWon(): Boolean = cards.isEmpty()

    // 新增：更新当前玩家的合法牌型列表
    fun updateHandTypeList(previousHand: List<Card>?, rules: Rules? = null) {
        handTypeList.clear() // 清空之前的牌型列表
        for (i in 1..cards.size) { // 从 2 开始，跳过单张牌型
            val combinations = cards.combinations(i) // 生成所有可能的牌型组合
            combinations.forEach { combination ->
                try {
                    // 如果规则对象存在，验证牌型是否合法
                    if (rules == null || rules.isValidPlay(combination, previousHand)) {
                        handTypeList.add(HandType.from(combination)) // 添加合法牌型
                    }
                } catch (_: IllegalArgumentException) {
                    // 忽略非法牌型
                }
            }
        }
    }

    // 新增：获取当前玩家的合法牌型列表
    fun getHandTypeList(): List<HandType> = handTypeList.toList()
    fun printHandTypeList() {
        if (handTypeList.isEmpty()) {
            println("无可用牌型")
            return
        }

        var currentType: HandType.Type? = null
        handTypeList.forEach { handType ->
            if (handType.type != currentType) {
                if (currentType != null) println() // 换行
                currentType = handType.type
                print("${currentType}: ")
            }
            print("${handType.cards} ")
        }
        println() // 最后换行
    }



    // 新增：移除所有手牌（用于重置玩家状态）
    fun removeAllCards() {
        cards.clear()
    }
}