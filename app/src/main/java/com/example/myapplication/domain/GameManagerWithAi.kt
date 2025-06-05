package com.example.myapplication.domain

import com.example.myapplication.GameActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Parcelable
import java.io.Serializable

class GameManager(
    private val gameEventListener: GameEventListener,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val players = mutableListOf<Player>()
    var currentPlayerIndex = 0
    private var previousHand = emptyList<Card>()
    private var isGameRunning = false
    private var consecutivePassCount = 0
    // 添加公开访问器（返回不可修改的副本）
    val lastPlayedCards: List<Card> get() = previousHand.toList()

    // 游戏事件监听接口
    interface GameEventListener {
        fun onGameStarted(players: List<Player>)
        fun onPlayerTurnStarted(player: Player)
        fun onCardsPlayed(player: Player, cards: List<Card>)
        fun onGameEnded(winner: Player, gameResult: GameManager.GameResult)
        fun onInvalidPlay(player: Player)
    }

    fun startNewGame() {
        // 初始化玩家
        players.clear()
        players.add(Player(isHuman = true,name="玩家"))  // 人类玩家
        repeat(3) { players.add(Player(isHuman = false,name="AI")) }  // AI玩家
        consecutivePassCount = 0

        previousHand = emptyList()
        (gameEventListener as? GameActivity)?.viewModel?.updateLastPlayedCards(emptyList())

        // 生成并洗牌
        val deck = generateShuffledDeck()

        // 发牌
        dealCards(deck)

        // 随机选择起始玩家
        currentPlayerIndex = 0
        isGameRunning = true

        // 通知UI游戏开始
        gameEventListener.onGameStarted(players.toList())
        startTurn()
    }

    private fun generateShuffledDeck(): List<Card> {
        return Suit.values().flatMap { suit ->
            Rank.values().map { rank ->
                Card(suit, rank)
            }
        }.shuffled()
    }

    private fun dealCards(deck: List<Card>) {
        val cardsPerPlayer = deck.chunked(13)
        players.forEachIndexed { index, player ->
            player.clearHand()
            player.addCards(cardsPerPlayer[index])
        }
    }

    private fun startTurn() {
        if (!isGameRunning) return

        val currentPlayer = players[currentPlayerIndex]
        println(currentPlayer.getHand())
        gameEventListener.onPlayerTurnStarted(currentPlayer)

        if (currentPlayer.isHuman) {
            // 人类玩家通过UI操作，此处无需自动处理
        } else {
            processAITurn(currentPlayer)
        }
    }

    private fun processAITurn(player: Player) {
        coroutineScope.launch {
            // AI思考延迟
//            delay(500)

            println("ai思考中")
            println(previousHand)
            val playedCards = player.playCards(previousHand)
            println(playedCards)
            if (playedCards.isNotEmpty()) {
                println("AI Player ${players.indexOf(player)} played: ${playedCards.joinToString()}")
                handleValidPlay(player, playedCards)
            } else {
                println("没有合法牌型")
                consecutivePassCount++
                if(consecutivePassCount==3)
                {
                    previousHand = emptyList()
                    consecutivePassCount=0
                }
                gameEventListener.onInvalidPlay(player)
            }

            // 出牌展示延迟
            delay(1000)
            proceedToNextPlayer()
        }
    }

    fun submitHumanPlay(cards: List<Card>) {
        if (!isGameRunning) return

        val currentPlayer = players[currentPlayerIndex]
        if (!currentPlayer.isHuman) return

        if (validatePlay(currentPlayer, cards)) {
            consecutivePassCount = 0
            handleValidPlay(currentPlayer, cards)
            proceedToNextPlayer()
        } else {
            consecutivePassCount++
            if(consecutivePassCount==3)
            {
                previousHand = emptyList()
                consecutivePassCount=0
            }
            gameEventListener.onInvalidPlay(currentPlayer)
        }
    }

    private fun validatePlay(player: Player, cards: List<Card>): Boolean {
        if(previousHand.isEmpty())
        {
            return true
        }
        if(cards.isEmpty())
        {
            return true
        }
        if(cards.size!=previousHand.size)
        {
            return false
        }
        if(compareHands(cards,previousHand)>0)
        {
            return true
        }
        return false
        // 这里可以添加更多游戏规则验证
    }

    private fun handleValidPlay(player: Player, cards: List<Card>) {
        previousHand = cards
        player.playCards(previousHand)  // 从手牌中移除
        gameEventListener.onCardsPlayed(player, cards)
        consecutivePassCount = 0

        (gameEventListener as? GameActivity)?.viewModel?.updateLastPlayedCards(cards)
        // 检查胜利条件
        if (player.handSize == 0) {
            endGame(player)
        }
    }

    private fun proceedToNextPlayer() {
        if (!isGameRunning) return

        currentPlayerIndex = (currentPlayerIndex + 1) % 4
        startTurn()
    }

    data class GameResult(
        val winner: Player,
        val playerBaseScores: Map<Player, Int>,      // 玩家基础牌分
        val playerFinalScores: Map<Player, Int>      // 玩家最终得分


    ) : Serializable

    private fun endGame(winner: Player) {
        isGameRunning = false

        // 计算游戏结果
        val gameResult = calculateGameResult(winner)

        gameEventListener.onGameEnded(winner, gameResult)
    }

    private fun calculateGameResult(winner: Player): GameResult {
        // 1. 计算每个玩家的基础牌分
        val baseScores = players.associateWith { player ->
            calculatePlayerBaseScore(player)
        }

        // 2. 计算每个玩家的最终得分
        val finalScores = players.associateWith { player ->
            calculatePlayerFinalScore(player, baseScores)
        }

        return GameResult(
            winner = winner,
            playerBaseScores = baseScores,
            playerFinalScores = finalScores
        )
    }

    private fun calculatePlayerBaseScore(player: Player): Int {
        val n = player.handSize
        var score = when {
            n < 8 -> n
            n < 10 -> 2 * n
            n < 13 -> 3 * n
            n == 13 -> 4 * n
            else -> n // 理论上不会发生
        }

        // 黑桃2加倍规则
        if (n >= 8 && player.hasCard(Card(Suit.SPADES, Rank.TWO))) {
            score *= 2
        }

        return score
    }

    private fun calculatePlayerFinalScore(
        currentPlayer: Player,
        baseScores: Map<Player, Int>
    ): Int {
        val totalScore = baseScores.values.sum()
        return totalScore - 4 * baseScores[currentPlayer]!!
    }
}