package bigtwo.app.player

import bigtwo.app.model.Card
import bigtwo.app.model.HandType


class Player(val name: String, val isHuman: Boolean = true) {

    // 玩家手牌
    private val cards = mutableListOf<Card>()

    // 接收一组牌
    fun receiveCards(newCards: List<Card>) {
        cards.addAll(newCards)
        sortCards()
    }

    // 获取当前手牌
    fun getCards(): List<Card> = cards.toList()

    // 出牌
    fun playCards(selectedCards: List<Card>,previousHandType:HandType?=null ): List<Card> {
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

        return selectedCards
    }

    // 对手牌进行排序
    private fun sortCards() {
        cards.sortBy { it.rank * 10 + it.suit.ordinal }//进行排序，从小到大
    }

    // 检查玩家是否有特定的牌
    fun hasCard(card: Card): Boolean = cards.contains(card)

    // 剩余牌数
    fun cardsCount(): Int = cards.size

    // 判断是否已经出完所有牌
    fun hasWon(): Boolean = cards.isEmpty()


}