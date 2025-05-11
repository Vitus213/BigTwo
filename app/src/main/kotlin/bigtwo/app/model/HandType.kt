package bigtwo.app.model

class HandType private constructor(
    val type: Type,
    val cards: List<Card>
) : Comparable<HandType> {  //实现Comparable接口以支持比较

    enum class Type {
        SINGLE, PAIR, TRIPLE, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH
    }
    companion object {
        fun from(cards: List<Card>): HandType { //识别牌型
            require(cards.isNotEmpty()) { "牌组不能为空" }
            return when (cards.size) {
                1 -> HandType(Type.SINGLE, cards)
                2 -> if (isPair(cards)) HandType(Type.PAIR, cards) else throw IllegalArgumentException("非法的对子")
                3 -> if (isTriple(cards)) HandType(Type.TRIPLE, cards) else throw IllegalArgumentException("非法的三张")
                5 -> determineFiveCardType(cards)
                else -> throw IllegalArgumentException("不支持的牌型")
            }
        }

        private fun isPair(cards: List<Card>) = cards[0].rank == cards[1].rank  //对子

        private fun isTriple(cards: List<Card>) = cards[0].rank == cards[1].rank && cards[1].rank == cards[2].rank  //三张

        private fun determineFiveCardType(cards: List<Card>): HandType {
            val sortedCards = cards.sorted()
            val isFlush = sortedCards.all { it.suit == sortedCards[0].suit }    //同花
            val isStraight = sortedCards.zipWithNext { a, b -> b.rank - a.rank == 1 }.all { it }    //顺子

            return when {
                isFlush && isStraight -> HandType(Type.STRAIGHT_FLUSH, sortedCards)
                isFourOfAKind(sortedCards) -> HandType(Type.FOUR_OF_A_KIND, sortedCards)
                isFullHouse(sortedCards) -> HandType(Type.FULL_HOUSE, sortedCards)
                isFlush -> HandType(Type.FLUSH, sortedCards)
                isStraight -> HandType(Type.STRAIGHT, sortedCards)
                else -> throw IllegalArgumentException("非法的五张牌型")
            }
        }

        private fun isFourOfAKind(cards: List<Card>): Boolean {
            val ranks = cards.groupBy { it.rank }
            return ranks.any { it.value.size == 4 }
        }

        private fun isFullHouse(cards: List<Card>): Boolean {
            val ranks = cards.groupBy { it.rank }
            return ranks.size == 2 && ranks.any { it.value.size == 3 }
        }
    }

    override fun compareTo(other: HandType): Int {//对同种牌型的比较
        return if (type != other.type) {
            type.compareTo(other.type)
        } else {
            when (type) {
                Type.SINGLE, Type.PAIR, Type.TRIPLE, Type.STRAIGHT, Type.FLUSH, Type.STRAIGHT_FLUSH -> {
                    cards.max().compareTo(other.cards.max())
                }
                Type.FULL_HOUSE -> {
                    val thisTriple = cards.groupBy { it.rank }.maxBy { it.value.size }!!.key
                    val otherTriple = other.cards.groupBy { it.rank }.maxBy { it.value.size }!!.key
                    thisTriple.compareTo(otherTriple)
                }
                Type.FOUR_OF_A_KIND -> {
                    val thisFour = cards.groupBy { it.rank }.maxBy { it.value.size }!!.key
                    val otherFour = other.cards.groupBy { it.rank }.maxBy { it.value.size }!!.key
                    thisFour.compareTo(otherFour)
                }
            }
        }
    }
}