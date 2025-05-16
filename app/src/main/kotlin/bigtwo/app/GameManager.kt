package bigtwo.app

import bigtwo.app.ai.AutoPlayer
import bigtwo.app.model.Card
import bigtwo.app.model.Deck
import bigtwo.app.model.HandType
import bigtwo.app.player.Player
import bigtwo.app.rules.RuleVariant
import bigtwo.app.rules.Rules
import java.io.PrintStream

data class PlayerInfo(val name: String, val isHuman: Boolean)
class GameManager(
    private val playerInfos: List<PlayerInfo> = listOf(
        PlayerInfo("玩家1", true),
        PlayerInfo("玩家2", false),
        PlayerInfo("玩家3", false),
        PlayerInfo("玩家4", false)
    ),
    private val ruleVariant: RuleVariant = RuleVariant.SOUTHERN,
    private val autoPlay: Boolean = true // 是否自动模拟出牌
) {
    private val rules = Rules(ruleVariant)
    private val players = playerInfos.map { Player(it.name, it.isHuman) }
    private val deck = Deck()
    private val autoPlayer = AutoPlayer(rules)

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

    public fun showFirstPlayer(): Player {
        println("首位出牌玩家是 ${players[currentPlayerIndex].name}")
        return players[currentPlayerIndex]
    }

    public fun showPlayer(index: Int): Player {
        return players[index]
    }

    public fun showgameended(): Boolean {
        if (players.any { it.hasWon() }) {
            return true
        }
        return false
    }

    public fun getPlayers(): List<Player> = players

    /** 获取当前玩家索引 */
    public fun getCurrentPlayerIndex(): Int = currentPlayerIndex

    /** 获取指定玩家手牌 */
    public fun getPlayerHand(playerIndex: Int): List<Card> = players[playerIndex].getCards()

    /** 获取上一手牌 */
    public fun getPreviousHand(): List<Card>? = previousHand

    /** 检查游戏是否结束 */
    public fun isGameEnded(): Boolean = gameEnded

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

    private fun playTurn() {
        val currentPlayer = players[currentPlayerIndex]
        println("\n轮到 ${currentPlayer.name} 出牌")
        println("当前手牌: ${currentPlayer.getCards().sorted()}")

        if (previousHand != null) {
            println("上一手牌: $previousHand 由 ${lastPlayedBy?.name} 出出")
        }
        // 判断是否是首轮且当前玩家持有方块三
        val isFirstTurn = previousHand == null
        if (isFirstTurn) {
            println("${currentPlayer.name} 持有方块3，必须出牌")
            if (!currentPlayer.isHuman || autoPlay) {
                val cardsToPlay = autoPlayer.autoPlayCards(currentPlayer, previousHand)
                handlePlay(currentPlayer, cardsToPlay)
            } else {
                // 人类玩家交互式出牌
                val cardsToPlay = getPlayerInputWithTimeout(currentPlayer)
                require(cardsToPlay.contains(Card(3, Card.Suit.DIAMOND))) { "必须出方块3" }
                handlePlay(currentPlayer, cardsToPlay)
            }
        } else {
            // 判断是否需要自动出牌
            if (!currentPlayer.isHuman || autoPlay) {
                val cardsToPlay = autoPlayer.autoPlayCards(currentPlayer, previousHand)

                handlePlay(currentPlayer, cardsToPlay)
            } else {
                // 人类玩家交互式出牌
                val cardsToPlay = getPlayerInputWithTimeout(currentPlayer)
                handlePlay(currentPlayer, cardsToPlay)
            }
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

    // 处理玩家出牌逻辑
    private fun handlePlay(player: Player, cardsToPlay: List<Card>) {
        if (cardsToPlay.isEmpty()) {
            println("${player.name} 选择过牌")
            playerPassStatus[player] = true
            consecutivePassCount++
        } else {
            try {
                val previousHandType = previousHand?.let { HandType.from(it) }
                player.playCards(cardsToPlay, previousHandType)
                println("${player.name} 出牌: $cardsToPlay")
                previousHand = cardsToPlay
                lastPlayedBy = player
                lastPlayerWhoPlayedIndex = players.indexOf(player)
                resetPassStatus()
            } catch (e: IllegalArgumentException) {
                println("出牌不合法：${e.message}，请重新选择出牌")
                val newCardsToPlay = if (!player.isHuman || autoPlay) {
                    autoPlayer.autoPlayCards(player, previousHand)
                } else {
                    getPlayerInputWithTimeout(player)
                }
                handlePlay(player, newCardsToPlay) // 递归调用重新处理出牌
            }
        }
    }

    // 获取玩家输入，带超时功能
    private fun getPlayerInputWithTimeout(player: Player): List<Card> {
        val timeoutMillis = 15000L // 设置超时时间为15秒
        var inputReceived = false
        var result: List<Card> = emptyList()

        val inputThread = Thread {
            try {
                println("请输入要出的牌的索引（用逗号分隔，例如: 0,1,2），或输入 pass 过牌：")
                val input = readLine()?.trim() ?: ""
                if (input.equals("pass", ignoreCase = true)) {
                    result = emptyList()
                } else {
                    val indices = input.split(",").map { it.trim().toInt() }
                    val selectedCards = indices.map { player.getCards()[it] }

                    // 如果是首轮，必须包含方块三
                    if (previousHand == null && !selectedCards.contains(Card(3, Card.Suit.DIAMOND))) {
                        throw IllegalArgumentException("首轮必须出方块3")
                    }
                    result = selectedCards
                }
                inputReceived = true
            } catch (e: Exception) {
                println("输入非法：${e.message}，请重新输入")
            }
        }

        inputThread.start()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (inputReceived) {
                inputThread.join() // 等待输入线程结束
                return result
            }
            Thread.sleep(100) // 避免忙等待
        }

        // 超时处理
        if (!inputReceived) {
            println("超时！系统将自动为 ${player.name} 托管出牌")
            return autoPlayer.autoPlayCards(player, previousHand) // 自动出牌
        }

        inputThread.join() // 确保线程结束
        return result
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
    val playerInfos = mutableListOf<PlayerInfo>()
    var playerCount = 4
    println("请输入真人数量（1-4）：")
    val TrueHumanCount = readLine()?.toIntOrNull()?.coerceIn(1, 4) ?: 4

    repeat(TrueHumanCount) { index ->
        println("请输入真人${index + 1}的名称：")
        val name = readLine() ?: "玩家${index + 1}"
        playerInfos.add(PlayerInfo(name, true))

    }
    // 自动填充 AI 玩家
    val aiCount = 4 - TrueHumanCount
    repeat(aiCount) { index ->
        playerInfos.add(PlayerInfo("AI玩家${index + 1}", false))
    }
    val gameManager = GameManager(playerInfos = playerInfos, autoPlay = false)
    gameManager.runGame()
}