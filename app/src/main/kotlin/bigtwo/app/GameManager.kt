package bigtwo.app

import bigtwo.app.model.Card
import bigtwo.app.model.Deck
import bigtwo.app.player.Player
import bigtwo.app.rules.RuleVariant
import bigtwo.app.rules.Rules
import java.io.PrintStream

class GameManager(
    private val playerNames: List<String> = listOf("玩家1", "玩家2", "玩家3", "玩家4"),
    private val ruleVariant: RuleVariant = RuleVariant.SOUTHERN,
    private val autoPlay: Boolean = true // 是否自动模拟出牌
) {
    private val rules = Rules(ruleVariant)
    private val players = playerNames.map { Player(it) }
    private val deck = Deck()
    // 当前游戏状态
    private var currentPlayerIndex = 0
    private var previousHand: List<Card>? = null
    private var lastPlayedBy: Player? = null
    private var gameEnded = false

    // 添加过牌状态跟踪
    private val playerPassStatus = mutableMapOf<Player, Boolean>()
    // 添加最后出牌玩家的索引
    private var lastPlayerWhoPlayedIndex = -1
    // 添加连续过牌计数
    private var consecutivePassCount = 0

    // 初始化游戏
    fun initGame() {
        // 发牌
        val hands = deck.deal()
        players.forEachIndexed { index, player ->
            player.receiveCards(hands[index])
            playerPassStatus[player] = false  // 初始化过牌状态
        }

        // 确定首出玩家（持有方块3的玩家）
        currentPlayerIndex = players.indexOfFirst { rules.hasStartingCard(it) }
        println("游戏开始，${players[currentPlayerIndex].name}首先出牌(持有方块3)")
        consecutivePassCount = 0
    }

    // 重置所有玩家的过牌状态
    private fun resetPassStatus() {
        players.forEach { player ->
            playerPassStatus[player] = false
        }
        consecutivePassCount = 0
    }

    // 运行游戏主循环
    fun runGame() {
        initGame()

        while (!gameEnded) {
            playTurn()

            // 检测是否连续三个玩家过牌
            if (consecutivePassCount >= 3) {
                println("连续三人过牌！下一位玩家可以任意出牌")
                // 不需要修改currentPlayerIndex，因为已经移到下一位玩家
                resetPassStatus()
                previousHand = null // 清空上一手牌，允许任意牌型重新开始
            }
        }

        showResults()
    }

    // 玩家回合
    private fun playTurn() {
        val currentPlayer = players[currentPlayerIndex]
        println("\n轮到 ${currentPlayer.name} 出牌")
        println("当前手牌: ${currentPlayer.getCards().sorted()}")

        if (previousHand != null) {
            println("上一手牌: $previousHand 由 ${lastPlayedBy?.name} 出出")
        }

        // 根据是否自动模拟来决定出牌逻辑
        if (autoPlay) {
            autoPlayCards(currentPlayer)
        } else {
            // 交互式出牌（未实现）
            println("请选择要出的牌（暂未实现）")
            // 临时用自动出牌代替
            autoPlayCards(currentPlayer)
        }

        // 检查是否有玩家胜利
        if (players.any { it.hasWon() }) {
            val winner = players.first { it.hasWon() }
            println("\n🎉 ${winner.name} 获胜！")
            gameEnded = true
            return
        }

        // 移动到下一个玩家
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
    }

    // 自动出牌逻辑
    private fun autoPlayCards(player: Player) {
        val playableCards = findPlayableCards(player.getCards())

        if (playableCards.isEmpty()) {
            println("${player.name} 选择过牌")
            playerPassStatus[player] = true  // 标记玩家已过牌
            consecutivePassCount++  // 增加连续过牌计数
        } else {
            player.playCards(playableCards)
            println("${player.name} 出牌: $playableCards")

            previousHand = playableCards
            lastPlayedBy = player
            lastPlayerWhoPlayedIndex = players.indexOf(player)  // 记录最后出牌的玩家索引
            resetPassStatus()  // 重置所有玩家的过牌状态，包括连续过牌计数
        }
    }

    // 找出可以出的牌
    private fun findPlayableCards(cards: List<Card>): List<Card> {
        // 简化实现：只选择单张牌
        if (previousHand == null) {
            // 首次出牌，选择最小的牌
            return listOf(cards.first())
        }

        // 尝试找到能够大过上一手牌的单张
        if (previousHand!!.size == 1) {
            val validCards = cards.filter { card ->
                rules.isValidPlay(listOf(card), previousHand)
            }
            return if (validCards.isNotEmpty()) listOf(validCards.first()) else emptyList()
        }

        // 其他牌型情况暂不实现
        return emptyList()
    }

    // 显示游戏结果
    private fun showResults() {
        val scores = if (ruleVariant == RuleVariant.SOUTHERN) {
            rules.calculateSouthernScore(players)
        } else {
            rules.calculateNorthernScore(players)
        }

        println("\n游戏结束，得分情况：")
        scores.forEach { (player, score) ->
            println("${player.name}: $score 分")
        }
    }
}

fun main() {
    // 设置UTF-8编码解决中文乱码
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    val gameManager = GameManager()
    gameManager.runGame()
}