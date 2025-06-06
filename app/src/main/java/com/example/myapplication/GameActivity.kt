package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.domain.Card
import com.example.myapplication.domain.GameManager
import com.example.myapplication.domain.Player

class GameActivity : ComponentActivity(), GameManager.GameEventListener {

    private lateinit var cardAdapter: CardAdapter
    private lateinit var playButton: Button
    private lateinit var passButton: Button
    private lateinit var recyclerView: RecyclerView
    val viewModel: GameViewModel by viewModels()
    private lateinit var gameManager: GameManager
    private lateinit var lastPlayAdapter: CardAdapter
    private lateinit var lastPlayRecyclerView: RecyclerView
    private lateinit var aiAvatar1: ImageView
    private lateinit var aiAvatar2: ImageView
    private lateinit var aiAvatar3: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        recyclerView = findViewById(R.id.rv_player_hand)
        playButton = findViewById(R.id.btn_play)
        passButton = findViewById(R.id.btn_pass)

        lastPlayRecyclerView = findViewById(R.id.rv_last_play)
        setupLastPlayArea()


        // 初始化游戏管理器
        gameManager = GameManager(this, viewModel.coroutineScope)
        viewModel.initializeGameManager(gameManager)

        setupRecyclerView()
        setupPlayButton()
        setupPassButton()
        setupObservers()
        aiAvatar1 = findViewById(R.id.iv_ai1)
        aiAvatar2 = findViewById(R.id.iv_ai2)
        aiAvatar3 = findViewById(R.id.iv_ai3)

        // 开始新游戏
        gameManager.startNewGame()
    }

    private fun setupLastPlayArea() {
        // 设置水平布局管理器
        lastPlayRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        // 初始化适配器（禁用选择功能）
        lastPlayAdapter = CardAdapter { /* 空回调，禁用选择 */ }
        lastPlayRecyclerView.adapter = lastPlayAdapter

        // 禁用用户交互
        lastPlayRecyclerView.isEnabled = false
    }



    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        cardAdapter = CardAdapter { selectedCards ->
            playButton.isEnabled = selectedCards.isNotEmpty()
        }

        recyclerView.adapter = cardAdapter
    }

    private fun setupPlayButton() {
        playButton.setOnClickListener {
            val selectedCards = cardAdapter.getSelectedCards()
            if (selectedCards.isNotEmpty() ) {
                // 提交玩家出牌
                gameManager.submitHumanPlay(selectedCards)
                cardAdapter.clearSelection()
                playButton.isEnabled = false
            }
        }
    }

    private fun setupPassButton() {
        passButton.setOnClickListener {
            gameManager.submitHumanPlay(emptyList()) // 提交空牌组
            cardAdapter.clearSelection() // 清除选择状态
            playButton.isEnabled = false // 禁用出牌按钮
            passButton.isEnabled = false // 禁用过牌按钮
        }
    }

    private fun setupObservers() {
        // 观察玩家手牌变化
        viewModel.humanHand.observe(this, Observer { hand ->
            hand?.let {
                cardAdapter.submitList(it)
            }
        })
        viewModel.lastPlayedCards.observe(this) { cards ->
            lastPlayAdapter.submitList(cards)
            // 滚动到最后一张牌
            if (cards.isNotEmpty()) {
                lastPlayRecyclerView.post {
                    lastPlayRecyclerView.smoothScrollToPosition(cards.size - 1)
                }
            }
        }

    }

    private fun resetAvatarHighlights() {
        aiAvatar1.alpha = 0.5f
        aiAvatar2.alpha = 0.5f
        aiAvatar3.alpha = 0.5f
    }

    // 高亮当前玩家的头像
    private fun highlightCurrentPlayer(playerIndex: Int) {
        resetAvatarHighlights()

        when (playerIndex) {
            3 -> aiAvatar1.alpha = 1.0f
            1 -> aiAvatar2.alpha = 1.0f
            2 -> aiAvatar3.alpha = 1.0f
        }
    }

    // 实现 GameEventListener 接口
    override fun onGameStarted(players: List<Player>) {

        // 设置人类玩家手牌
        val humanPlayer = players.first { it.isHuman }
        viewModel.setHumanHand(humanPlayer.getHand())
    }

    override fun onPlayerTurnStarted(player: Player) {
        runOnUiThread {
            val playerIndex = gameManager.currentPlayerIndex
            highlightCurrentPlayer(playerIndex)
            if (player.isHuman) {
                Toast.makeText(this, "您的回合", Toast.LENGTH_SHORT).show()
                // 启用交互
                passButton.isEnabled = true
                recyclerView.isEnabled = true
                playButton.isEnabled = false // 等待玩家选择卡牌
            } else {
               // Toast.makeText(this, "AI ${players.indexOf(player)}的回合", Toast.LENGTH_SHORT).show()
                // 禁用交互
                recyclerView.isEnabled = false
                playButton.isEnabled = false
            }
        }
    }

    override fun onCardsPlayed(player: Player, cards: List<Card>) {
        runOnUiThread {
            if (player.isHuman) {
                // 更新手牌（已在观察者中处理）
                viewModel.setHumanHand(player.getHand())
                cardAdapter.submitList(player.getHand())
                Toast.makeText(this, "您出牌: ${cards.joinToString()}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "AI 出牌: ${cards.joinToString()}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onGameEnded(winner: Player, gameResult: GameManager.GameResult) {
        runOnUiThread {
            // 显示Toast通知
            Toast.makeText(
                this,
                if (winner.isHuman) "您赢了！" else "AI 赢了",
                Toast.LENGTH_LONG
            ).show()

            // 创建跳转到结算页面的Intent
            val intent = Intent(this, SettlementHallActivity::class.java).apply {

            }
            intent.putExtra("result_data", gameResult)


            // 启动结算页面
            startActivity(intent)

            // 可选：结束当前游戏界面
            finish()
        }
    }

    override fun onInvalidPlay(player: Player) {
        runOnUiThread {
            if (player.isHuman) {
                Toast.makeText(this, "出牌无效，请重新选择", Toast.LENGTH_SHORT).show()
            }
        }
    }


}