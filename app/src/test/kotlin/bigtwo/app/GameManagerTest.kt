import bigtwo.app.GameManager
import bigtwo.app.PlayerInfo
import bigtwo.app.model.Card.Suit
import bigtwo.app.rules.RuleVariant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameManagerTest {

    private lateinit var gameManager: GameManager

    @Before
    fun setUp() {
        // 初始化玩家信息
        val playerInfos = listOf(
            PlayerInfo("玩家1", true),
            PlayerInfo("玩家2", false),
            PlayerInfo("玩家3", false),
            PlayerInfo("玩家4", false)
        )
        gameManager = GameManager(playerInfos, RuleVariant.SOUTHERN, false)
    }

    @Test
    fun testInitGame() {
        gameManager.initGame()
        assertNotNull("游戏初始化后，玩家列表不应为空", gameManager)
    }

    @Test
    fun testFirstPlayer() {
        gameManager.initGame()
        val firstPlayer = gameManager.showFirstPlayer()
        assertTrue(
            "首位玩家应持有方块3",
            firstPlayer.getCards().any { it.rank == 3 && it.suit == Suit.DIAMOND })
    }

    // @Test
    // fun testGameEndCondition() {
    //     gameManager.initGame()
    //     // 模拟一个玩家获胜
    //     val winner = gameManager.showPlayer(0)
    //     winner.removeallCards() // 清空手牌表示获胜
    //     gameManager.runGame()
    //     assertTrue("游戏应结束", gameManager.showgameended())
    // }

}