package bigtwo.app.ai

import bigtwo.app.model.Card
import bigtwo.app.player.Player
import bigtwo.app.rules.Rules

class AutoPlayer(private val rules: Rules) {

    fun autoPlayCards(player: Player, previousHand: List<Card>?): List<Card> {
        val playableCards = findPlayableCards(player.getCards(), previousHand)

        return if (playableCards.isEmpty()) {
            println("${player.name} 选择过牌")
            emptyList()
        } else {

            playableCards
        }
    }

    private fun findPlayableCards(cards: List<Card>, previousHand: List<Card>?): List<Card> {
        // 简化实现：只选择单张牌
        if (previousHand == null) {
            // 首次出牌，选择最小的牌
            return if (cards.isNotEmpty()) listOf(cards.first()) else emptyList()
        }

        // 尝试找到能够大过上一手牌的单张
        if (previousHand.size == 1) {
            val validCards = cards.filter { card ->
                rules.isValidPlay(listOf(card), previousHand)
            }
            return if (validCards.isNotEmpty()) listOf(validCards.first()) else emptyList()
        }

        // 其他牌型情况暂不实现
        return emptyList()
    }
}