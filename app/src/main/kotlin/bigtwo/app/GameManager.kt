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
    private val rules = Rules(ruleVariant) // 游戏规则
    private val players = playerInfos.map { Player(it.name, it.isHuman) } // 初始化玩家
    private val deck = Deck() // 初始化牌堆
    private val autoPlayer = AutoPlayer(rules) // 自动出牌逻辑

    // 游戏状态变量
    private var currentPlayerIndex = 0 // 当前玩家索引
    private var previousHand: List<Card>? = null // 上一手牌
    private var lastPlayedBy: Player? = null // 上一手牌的玩家
    private var gameEnded = false // 游戏是否结束

    // 过牌状态跟踪
    private val playerPassStatus = mutableMapOf<Player, Boolean>() // 每个玩家的过牌状态
    private var lastPlayerWhoPlayedIndex = -1 // 最后出牌玩家的索引
    private var consecutivePassCount = 0 // 连续过牌计数

    // 初始化游戏
    fun initGame() {
        val hands = deck.deal() // 发牌
        players.forEachIndexed { index, player ->
            player.receiveCards(hands[index]) // 给每个玩家分配手牌
            player.updateHandTypeList(previousHand = null, rules = rules) // 初始化玩家的合法牌型列表
        }
        currentPlayerIndex = players.indexOfFirst { rules.hasStartingCard(it) } // 确定首出玩家
        println("游戏开始，${players[currentPlayerIndex].name}首先出牌(持有方块3)")
    }

    // 显示首位出牌玩家
    public fun showFirstPlayer(): Player {
        println("首位出牌玩家是 ${players[currentPlayerIndex].name}")
        return players[currentPlayerIndex]
    }

    // 显示指定玩家
    public fun showPlayer(index: Int): Player {
        return players[index]
    }

    // 检查游戏是否结束
    fun showgameended(): Boolean {
        return players.any { it.hasWon() }
    }

    // 获取所有玩家
    public fun getPlayers(): List<Player> = players

    // 获取当前玩家索引
    public fun getCurrentPlayerIndex(): Int = currentPlayerIndex

    // 获取指定玩家的手牌
    public fun getPlayerHand(playerIndex: Int): List<Card> = players[playerIndex].getCards()

    // 获取上一手牌
    public fun getPreviousHand(): List<Card>? = previousHand

    // 检查游戏是否结束
    public fun isGameEnded(): Boolean = gameEnded

    // 重置所有玩家的过牌状态
    private fun resetPassStatus() {
        players.forEach { player ->
            playerPassStatus[player] = false
        }
        consecutivePassCount = 0
    }

    // 游戏主循环
    fun runGame() {
        initGame() // 初始化游戏

        while (!gameEnded) {
            playTurn() // 进行一轮游戏

            // 检测是否连续三个玩家过牌
            if (consecutivePassCount >= 3) {
                println("连续三人过牌！下一位玩家可以任意出牌")
                resetPassStatus() // 重置过牌状态
                previousHand = null // 清空上一手牌，允许任意牌型重新开始
            }
        }

        showResults() // 显示游戏结果
    }

    // 进行一轮游戏
    fun playTurn() {
        val currentPlayer = players[currentPlayerIndex] // 获取当前玩家
        println("\n轮到 ${currentPlayer.name} 出牌")

        // 如果是人类玩家，显示手牌和可用牌型
        if (currentPlayer.isHuman) {
            println("当前手牌: ${currentPlayer.getCards().sorted()}")
            currentPlayer.updateHandTypeList(previousHand = previousHand, rules = rules)
            println("当前可用牌型列表:")
            currentPlayer.printHandTypeList()

            // 显示上一手牌
            if (previousHand != null && lastPlayedBy != null) {
                println("上一手牌：${previousHand}（由 ${lastPlayedBy!!.name} 出）")
            } else {
                println("上一手牌：无")
            }
        }

        // 自动或手动出牌
        if (!currentPlayer.isHuman || autoPlay) {
            val cardsToPlay = autoPlayer.autoPlayCards(currentPlayer, previousHand) // 自动出牌
            handlePlay(currentPlayer, cardsToPlay)
        } else {
            val cardsToPlay = getPlayerInputWithTimeout(currentPlayer) // 获取玩家输入
            handlePlay(currentPlayer, cardsToPlay)
        }

        // 检查是否有玩家获胜
        if (players.any { it.hasWon() }) {
            val winner = players.first { it.hasWon() }
            println("\n🎉 ${winner.name} 获胜！")
            gameEnded = true
            return
        }

        // 切换到下一位玩家
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
    }

    // 处理玩家出牌逻辑
    private fun handlePlay(player: Player, cardsToPlay: List<Card>) {
        if (cardsToPlay.isEmpty()) {
            println("${player.name} 选择过牌")
            playerPassStatus[player] = true // 标记玩家过牌
            consecutivePassCount++ // 增加连续过牌计数
        } else {
            try {
                val previousHandType = previousHand?.let { HandType.from(it) } // 获取上一手牌的牌型
                player.playCards(cardsToPlay, previousHandType) // 验证并出牌
                println("${player.name} 出牌: $cardsToPlay")
                previousHand = cardsToPlay // 更新上一手牌
                lastPlayedBy = player // 更新最后出牌玩家
                lastPlayerWhoPlayedIndex = players.indexOf(player) // 更新最后出牌玩家索引
                resetPassStatus() // 重置过牌状态
            } catch (e: IllegalArgumentException) {//异常处理
                println("出牌不合法：${e.message}，请重新选择出牌")
                val newCardsToPlay = if (!player.isHuman || autoPlay) {
                    autoPlayer.autoPlayCards(player, previousHand) // 自动出牌
                } else {
                    getPlayerInputWithTimeout(player) // 获取玩家输入
                }
                handlePlay(player, newCardsToPlay) // 递归调用重新处理出牌
            }
        }
    }

    // 获取玩家输入，带超时功能（该函数已经大改）
    private fun getPlayerInputWithTimeout(player: Player): List<Card> {
        val timeoutMillis = 15000L // 设置超时时间为15秒
        var result: List<Card> = emptyList()

        val inputThread = Thread {
            while (true) {
                try {
                    println("请输入要出的牌的索引（用逗号分隔，例如: 0,1,2），或输入 pass 过牌：")
                    val input = readLine()?.trim() ?: ""
                    if (input.equals("pass", ignoreCase = true)) {
                        result = emptyList() // 玩家选择过牌
                        break
                    } else {
                        val indices = input.split(",").map { it.trim().toInt() }
                        val selectedCards = indices.map { player.getCards()[it] }

                        // 如果是首轮，必须包含方块三
                        if (previousHand == null && !selectedCards.contains(Card(3, Card.Suit.DIAMOND))) {
                            throw IllegalArgumentException("首轮必须出方块3")
                        }
                        result = selectedCards // 玩家选择出牌
                        break
                    }
                } catch (e: Exception) {
                    println("输入非法：${e.message}，请重新输入")
                }
            }
        }

        inputThread.start()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (!inputThread.isAlive) {
                return result // 返回玩家输入的结果
            }
            Thread.sleep(100) // 避免忙等待
        }

        // 超时处理
        if (inputThread.isAlive) {
            println("超时！系统将自动为 ${player.name} 托管出牌")
            inputThread.interrupt()
            return autoPlayer.autoPlayCards(player, previousHand) // 自动出牌
        }

        return result
    }

    // 显示游戏结果
    private fun showResults() {
        val scores = if (ruleVariant == RuleVariant.SOUTHERN) {
            rules.calculateSouthernScore(players) // 计算南方规则得分
        } else {
            rules.calculateNorthernScore(players) // 计算北方规则得分
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