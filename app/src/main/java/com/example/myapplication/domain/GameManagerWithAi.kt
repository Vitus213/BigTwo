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

        // 获取难度设置
        val difficulty = (gameEventListener as? GameActivity)?.intent?.getStringExtra("DIFFICULTY") ?: "NORMAL"

        // 初始化玩家
        players.clear()
        players.add(Player(name="玩家", isHuman = true))  // 人类玩家
        repeat(3) {
            players.add(Player(
                name = "AI",
                isHuman = false,
                difficulty = difficulty  // 传入难度参数
            ))
        }


        consecutivePassCount = 0

        previousHand = emptyList()
        isFirstPlay = true  // 重置首回合标记

        (gameEventListener as? GameActivity)?.viewModel?.updateLastPlayedCards(emptyList())

        // 生成并洗牌
        val deck = generateShuffledDeck()

        // 发牌
        dealCards(deck)


        // 寻找持有方块3的玩家作为起始玩家
        val diamond3 = Card(Suit.DIAMONDS, Rank.THREE)
        currentPlayerIndex = players.indexOfFirst { player ->
            player.hasCard(diamond3)
        }

        println("持有方块3的玩家索引: $currentPlayerIndex")


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

            // 增加 AI "思考"时间
            delay(1500) // AI 思考 1.5 秒


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
                if(consecutivePassCount==3) {

                    previousHand = emptyList()
                    consecutivePassCount=0
                }
                gameEventListener.onInvalidPlay(player)
            }


            // 增加出牌展示时间，让玩家能看清 AI 出的牌
            delay(2000) // 展示牌 2 秒

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



    private var isFirstPlay = true // 添加标记是否为首回合的变量

    private fun validatePlay(player: Player, cards: List<Card>): Boolean {
        // 首回合特殊规则：必须包含方块3
        if (isFirstPlay) {
            val diamond3 = Card(Suit.DIAMONDS, Rank.THREE)
            if (!cards.contains(diamond3)) {
                return false
            }
            // 验证牌型合法性
            return isValidHand(cards)
        }

        // 非首回合规则
        if (previousHand.isEmpty()) {
            return isValidHand(cards)
        }
        if (cards.isEmpty()) {
            return true // 允许过牌
        }
        if (cards.size != previousHand.size) {
            return false
        }
        return compareHands(cards, previousHand) > 0 && isValidHand(cards)

    }

    private fun handleValidPlay(player: Player, cards: List<Card>) {
        previousHand = cards

        player.playCards(previousHand)
        gameEventListener.onCardsPlayed(player, cards)
        consecutivePassCount = 0

        if (isFirstPlay) {
            isFirstPlay = false
        }

        (gameEventListener as? GameActivity)?.viewModel?.updateLastPlayedCards(cards)


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