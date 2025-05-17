package bigtwo.app.ai
import bigtwo.app.utils.combinations
import bigtwo.app.model.Card
import bigtwo.app.player.Player
import bigtwo.app.rules.Rules

class AutoPlayer(private val rules: Rules) {

    fun autoPlayCards(player: Player, previousHand: List<Card>?): List<Card> {
        val playableCards = findPlayableCards(player.getCards(), previousHand)

        return if (playableCards.isEmpty()) {

            emptyList()
        } else {

            playableCards
        }
    }

    private fun findPlayableCards(cards: List<Card>, previousHand: List<Card>?): List<Card> {
        val allCombinations = mutableListOf<List<Card>>()

        // 生成所有可能的牌型组合
        for (i in 1..cards.size) {
            allCombinations.addAll(cards.combinations(i))
        }

        // 筛选出合法的牌型
        val validPlays = allCombinations.filter { combination ->
            try {
                rules.isValidPlay(combination, previousHand)
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        // 随机选择一个合法牌型
        return if (validPlays.isNotEmpty()) validPlays.random() else emptyList()
    }
}