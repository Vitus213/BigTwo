package bigtwo.app.model

class Deck {

    private val cards: MutableList<Card> = mutableListOf()

    init {
        initializeDeck()
        shuffle()
    }

    // 初始化整副牌（3~15，对应 3~10, J, Q, K, A, 2）
    private fun initializeDeck() {
        cards.clear()
        for (suit in Card.Suit.entries) {
            for (rank in 3..15) {
                cards.add(Card(rank, suit)) //初始化一整副手牌
            }
        }
    }

    // 洗牌
    fun shuffle() {
        cards.shuffle() //对cards元素进行打乱
    }

    // 发牌给 4 个玩家，每人 13 张
    fun deal(): List<List<Card>> {
        if (cards.size < 52) throw IllegalStateException("牌数不足，无法发牌")

        val hands = List(4) { mutableListOf<Card>() }
        for (i in 0 until 52) {
            hands[i % 4].add(cards[i])
        }
        return hands
    }

    // 返回当前剩余牌数
    fun remaining(): Int = cards.size
}
