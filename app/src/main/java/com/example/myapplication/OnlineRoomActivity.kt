package com.example.myapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.bluetooth.BluetoothClient
import com.example.myapplication.bluetooth.BluetoothPermissionManager
import com.example.myapplication.bluetooth.BluetoothServer
import com.example.myapplication.domain.Card
import com.example.myapplication.domain.PlayerHandManager
import com.example.myapplication.domain.Rank
import com.example.myapplication.domain.Suit
import com.example.myapplication.domain.evaluateHand
import com.example.myapplication.domain.isValidHand
import com.example.myapplication.domain.sortCards
import com.example.myapplication.domain.compareHands
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay

private const val TAG = "OnlineRoomActivity"

data class ConnectedClient(
    val id: String,
    val name: String,
    val avatar: String,
    val playerCards: List<Card> = emptyList()  // 添加手牌字段
)

// 修改 GameState 数据类，添加游戏状态
data class GameState(
    val playerCards: List<Card> = emptyList(),
    val lastPlayedCards: List<Card> = emptyList(),
    val lastPlayedBy: String = "",
    val currentPlayer: String = "",  // 当前玩家ID
    val isMyTurn: Boolean = false,
    val players: List<ConnectedClient> = emptyList(),
    val isDealing: Boolean = false,  // 是否正在发牌
    val dealerIndex: Int = 0,        // 当前发牌玩家的索引
    val cardsDealt: Int = 0,         // 已发的牌数
    val isGameReady: Boolean = false,  // 游戏是否准备就绪（已发牌）
    val isGameRunning: Boolean = false,  // 游戏是否正在运行
    val currentPlayerIndex: Int = 0  // 当前玩家在players列表中的索引
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false

        return playerCards == other.playerCards &&
                lastPlayedCards == other.lastPlayedCards &&
                lastPlayedBy == other.lastPlayedBy &&
                currentPlayer == other.currentPlayer &&
                isMyTurn == other.isMyTurn &&
                players == other.players &&
                isDealing == other.isDealing &&
                dealerIndex == other.dealerIndex &&
                cardsDealt == other.cardsDealt &&
                isGameReady == other.isGameReady &&
                isGameRunning == other.isGameRunning &&
                currentPlayerIndex == other.currentPlayerIndex
    }

    override fun hashCode(): Int {
        var result = playerCards.hashCode()
        result = 31 * result + lastPlayedCards.hashCode()
        result = 31 * result + lastPlayedBy.hashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + isMyTurn.hashCode()
        result = 31 * result + players.hashCode()
        result = 31 * result + isDealing.hashCode()
        result = 31 * result + dealerIndex
        result = 31 * result + cardsDealt
        result = 31 * result + isGameReady.hashCode()
        result = 31 * result + isGameRunning.hashCode()
        result = 31 * result + currentPlayerIndex
        return result
    }
}

// 添加 Card 扩展属性
private var Card.isSelected: Boolean
    get() = false // 默认未选中
    set(value) {} // 不允许直接设置

class OnlineRoomActivity : ComponentActivity() {

    private lateinit var permissionManager: BluetoothPermissionManager
    private lateinit var bluetoothClient: BluetoothClient
    private lateinit var bluetoothServer: BluetoothServer

    // 使用 StateFlow 管理连接的客户端列表
    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val playerHandManager = PlayerHandManager()  // 添加 PlayerHandManager 实例

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionManager = BluetoothPermissionManager(this, activityResultRegistry, this)
        bluetoothClient = BluetoothClient(this)
        bluetoothServer = BluetoothServer(this)

        setContent {
            MyApplicationTheme {
                OnlineRoomScreen(
                    permissionManager = permissionManager,
                    bluetoothClient = bluetoothClient,
                    bluetoothServer = bluetoothServer,
                    activityContext = this,
                    isHostFlow = isHost,
                    connectedClientsFlow = connectedClients,
                    onClientListUpdated = { newList -> _connectedClients.value = newList },
                    onSetIsHost = { host -> _isHost.value = host },
                    playerHandManager = playerHandManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在 Activity 销毁时关闭蓝牙连接
        bluetoothClient.close()
        bluetoothServer.close()
    }

    // 处理消息 (这个方法是在 Activity 中定义的，现在不需要了，因为处理逻辑移到了 Composable 中)
    // private fun handleMessage(message: String) { ... }

    // 添加创建牌组的函数
    fun createDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                deck.add(Card(suit, rank))
            }
        }
        return deck.shuffled()
    }

    // 添加发牌函数
    fun dealCards(players: List<ConnectedClient>, currentState: GameState, onGameStateUpdate: (GameState) -> Unit) {
        Log.d("OnlineRoomScreen", "开始发牌，玩家数量: ${players.size}")

        // 创建并洗牌
        val deck = mutableListOf<Card>().apply {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    add(Card(suit, rank))
                }
            }
        }.shuffled()

        Log.d("OnlineRoomScreen", "牌组创建完成，总牌数: ${deck.size}")

        val cardsPerPlayer = 13 // 每人13张牌

        // 给每个玩家发牌
        val updatedPlayers = players.mapIndexed { index, player ->
            val startIndex = index * cardsPerPlayer
            val endIndex = (index + 1) * cardsPerPlayer
            val playerCards = deck.subList(startIndex, endIndex)

            Log.d("OnlineRoomScreen", "给玩家 ${player.name}(${player.id}) 发牌，牌数: ${playerCards.size}")
            Log.d("OnlineRoomScreen", "玩家 ${player.name} 的手牌: ${playerCards.joinToString { "${it.rank}${it.suit}" }}")

            // 使用 PlayerHandManager 记录玩家手牌
            playerHandManager.initializePlayerHand(player.id, playerCards)
            Log.d("OnlineRoomScreen", "PlayerHandManager 记录玩家 ${player.name}(${player.id}) 的手牌: ${playerHandManager.getPlayerHand(player.id).joinToString { "${it.rank}${it.suit}" }}")

            val cardsJson = JSONArray().apply {
                playerCards.forEach { card ->
                    put(JSONObject().apply {
                        put("suit", card.suit.toString())
                        put("value", when (card.rank) {
                            Rank.ACE -> "A"
                            Rank.JACK -> "J"
                            Rank.QUEEN -> "Q"
                            Rank.KING -> "K"
                            else -> card.rank.value.toString()
                        })
                    })
                }
            }.toString()

            // 发送牌给玩家
            if (player.id == "LOCAL_HOST_ID") {
                Log.d("OnlineRoomScreen", "更新主机手牌")
                // 如果是主机，直接更新状态
                val newState = currentState.copy(
                    playerCards = playerCards,
                    isDealing = false,
                    currentPlayerIndex = 0,  // 设置主机为第一个出牌的玩家
                    currentPlayer = "LOCAL_HOST_ID",
                    isMyTurn = true,  // 主机先出牌
                    isGameReady = true,
                    isGameRunning = true,
                    players = players.map { if (it.id == "LOCAL_HOST_ID") it.copy(playerCards = playerCards) else it }  // 更新主机手牌
                )
                Log.d("OnlineRoomScreen", "主机游戏状态更新: currentPlayer=${newState.currentPlayer}, isMyTurn=${newState.isMyTurn}, players=${newState.players.size}")
                onGameStateUpdate(newState)
                player.copy(playerCards = playerCards)
            } else {
                Log.d("OnlineRoomScreen", "发送手牌给客户端 ${player.name}(${player.id})")
                // 如果是其他玩家，发送消息
                bluetoothServer.sendDataToClient(player.id, "DEAL_CARDS:$cardsJson")
                player.copy(playerCards = playerCards)
            }
        }

        // 广播游戏开始和初始玩家信息
        bluetoothServer.sendDataToAllClients("GAME_STARTED:0")  // 0 表示主机先出牌
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineRoomScreen(
    permissionManager: BluetoothPermissionManager,
    bluetoothClient: BluetoothClient,
    bluetoothServer: BluetoothServer,
    activityContext: Context,
    isHostFlow: StateFlow<Boolean>,
    connectedClientsFlow: StateFlow<List<ConnectedClient>>,
    onClientListUpdated: (List<ConnectedClient>) -> Unit,
    onSetIsHost: (Boolean) -> Unit,
    playerHandManager: PlayerHandManager
) {
    val playerNickname = remember { mutableStateOf("") }
    val showNicknameDialog = remember { mutableStateOf(true) }

    // Collect StateFlows as Compose States
    val isHost by isHostFlow.collectAsState()
    val connectedClients by connectedClientsFlow.collectAsState()

    var isBluetooth by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }

    val showSearchDialog = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val hasBluetoothScanPermission by permissionManager.hasBluetoothScanPermission
    val hasBluetoothConnectPermission by permissionManager.hasBluetoothConnectPermission

    // 用于控制客户端PLAYER_JOIN消息只发送一次
    var hasJoinedRoom by remember { mutableStateOf(false) }

    // 游戏状态
    var gameState by remember { mutableStateOf(GameState()) }
    var isGameStarted by remember { mutableStateOf(false) }

    // 添加发牌函数
    fun dealCards(players: List<ConnectedClient>, currentState: GameState, onGameStateUpdate: (GameState) -> Unit) {
        Log.d("OnlineRoomScreen", "开始发牌，玩家数量: ${players.size}")

        // 创建并洗牌
        val deck = mutableListOf<Card>().apply {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    add(Card(suit, rank))
                }
            }
        }.shuffled()

        Log.d("OnlineRoomScreen", "牌组创建完成，总牌数: ${deck.size}")

        val cardsPerPlayer = 13 // 每人13张牌

        // 给每个玩家发牌
        val updatedPlayers = players.mapIndexed { index, player ->
            val startIndex = index * cardsPerPlayer
            val endIndex = (index + 1) * cardsPerPlayer
            val playerCards = deck.subList(startIndex, endIndex)

            Log.d("OnlineRoomScreen", "给玩家 ${player.name}(${player.id}) 发牌，牌数: ${playerCards.size}")
            Log.d("OnlineRoomScreen", "玩家 ${player.name} 的手牌: ${playerCards.joinToString { "${it.rank}${it.suit}" }}")

            // 使用 PlayerHandManager 记录玩家手牌
            playerHandManager.initializePlayerHand(player.id, playerCards)
            Log.d("OnlineRoomScreen", "PlayerHandManager 记录玩家 ${player.name}(${player.id}) 的手牌: ${playerHandManager.getPlayerHand(player.id).joinToString { "${it.rank}${it.suit}" }}")

            val cardsJson = JSONArray().apply {
                playerCards.forEach { card ->
                    put(JSONObject().apply {
                        put("suit", card.suit.toString())
                        put("value", when (card.rank) {
                            Rank.ACE -> "A"
                            Rank.JACK -> "J"
                            Rank.QUEEN -> "Q"
                            Rank.KING -> "K"
                            else -> card.rank.value.toString()
                        })
                    })
                }
            }.toString()

            // 发送牌给玩家
            if (player.id == "LOCAL_HOST_ID") {
                Log.d("OnlineRoomScreen", "更新主机手牌")
                // 如果是主机，直接更新状态
                val newState = currentState.copy(
                    playerCards = playerCards,
                    isDealing = false,
                    currentPlayerIndex = 0,  // 设置主机为第一个出牌的玩家
                    currentPlayer = "LOCAL_HOST_ID",
                    isMyTurn = true,  // 主机先出牌
                    isGameReady = true,
                    isGameRunning = true,
                    players = players.map { if (it.id == "LOCAL_HOST_ID") it.copy(playerCards = playerCards) else it }  // 更新主机手牌
                )
                Log.d("OnlineRoomScreen", "主机游戏状态更新: currentPlayer=${newState.currentPlayer}, isMyTurn=${newState.isMyTurn}, players=${newState.players.size}")
                onGameStateUpdate(newState)
                player.copy(playerCards = playerCards)
            } else {
                Log.d("OnlineRoomScreen", "发送手牌给客户端 ${player.name}(${player.id})")
                // 如果是其他玩家，发送消息
                bluetoothServer.sendDataToClient(player.id, "DEAL_CARDS:$cardsJson")
                player.copy(playerCards = playerCards)
            }
        }

        // 广播游戏开始和初始玩家信息
        bluetoothServer.sendDataToAllClients("GAME_STARTED:0")  // 0 表示主机先出牌
    }

    // 根据游戏状态显示不同的界面
    if (isGameStarted) {
        Log.d("OnlineRoomScreen", "显示游戏界面，当前手牌数: ${gameState.playerCards.size}, currentPlayer=${gameState.currentPlayer}, isMyTurn=${gameState.isMyTurn}")
        // 游戏界面
        GameScreen(
            gameState = gameState,
            onCardSelected = { card ->
                // 卡牌选择逻辑已经在 GameScreen 中处理
            },
            onStartDealing = {
                if (isHost) {
                    if (connectedClients.size >= 1) {
                        Log.d("OnlineRoomScreen", "主机开始发牌，当前玩家数: ${connectedClients.size}")
                        Toast.makeText(activityContext, "开始发牌！", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            try {
                                // 执行发牌
                                dealCards(connectedClients, gameState) { newState ->
                                    gameState = newState
                                }
                                Log.d("OnlineRoomScreen", "发牌完成")
                            } catch (e: Exception) {
                                Log.e("OnlineRoomScreen", "发牌过程出错: ${e.message}", e)
                                Toast.makeText(activityContext, "发牌失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(activityContext, "至少需要一名玩家才能开始游戏", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(activityContext, "只有主机才能开始发牌", Toast.LENGTH_SHORT).show()
                }
            },
            isHost = isHost,
            bluetoothServer = if (isHost) bluetoothServer else null,
            bluetoothClient = if (!isHost) bluetoothClient else null,
            onGameStateUpdate = { newState ->
                gameState = newState
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // 房间界面
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "联机房间",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )

                    // 房间状态显示
                    Text(
                        text = when {
                            isHost -> "状态: 已开启房间 (${connectedClients.size}/4人)"
                            isConnected -> "状态: 已连接到房间 (${connectedClients.size}/4人)"
                            else -> "状态: 未连接"
                        },
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 玩家头像显示区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Add logging here to check the list state at drawing time
                        Log.d("OnlineRoomScreen_UI", "UI绘制时 - connectedClients大小: ${connectedClients.size}")
                        connectedClients.forEachIndexed { index, client ->
                            Log.d("OnlineRoomScreen_UI", "UI绘制时 - connectedClients[${index}]: id=${client.id}, name=${client.name}")
                        }

                        // 获取主机和当前客户端的信息
                        val hostPlayer = connectedClients.firstOrNull { it.id == "LOCAL_HOST_ID" }
                        val selfPlayer = connectedClients.firstOrNull { it.name == playerNickname.value }

                        // 收集其他玩家 (排除主机和自身)
                        val otherPlayers = connectedClients.filter { it.id != "LOCAL_HOST_ID" && it.name != playerNickname.value }

                        // 按照固定顺序绘制：自身(索引0)，主机(索引1)，其他玩家(索引2 onwards)
                        var currentAvatarIndex = 0

                        // 绘制客户端自身 (如果存在)
                        AvatarBox(
                            index = currentAvatarIndex,
                            nickname = selfPlayer?.name ?: playerNickname.value.ifBlank { "你" }
                        )
                        currentAvatarIndex++

                        // 绘制主机 (如果存在且不是客户端自身)
                        if (hostPlayer != null && hostPlayer.id != (selfPlayer?.id ?: "")) {
                            AvatarBox(
                                index = currentAvatarIndex,
                                nickname = hostPlayer.name
                            )
                            currentAvatarIndex++
                        }

                        // 绘制其他玩家
                        otherPlayers.forEach { client ->
                            AvatarBox(
                                index = currentAvatarIndex,
                                nickname = client.name
                            )
                            currentAvatarIndex++
                        }

                        // 填充剩余的空位，最多4人
                        while (currentAvatarIndex < 4) {
                            AvatarBox(index = currentAvatarIndex)
                            currentAvatarIndex++
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // --- 连接选择器 (开启/搜索房间按钮) ---
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    ConnectionSelector(
                        isBluetooth = isBluetooth,
                        onCheckedChange = { /* 目前禁用，因为只支持蓝牙 */ },
                        onHostRoom = {
                            @SuppressLint("MissingPermission")
                            if (!hasBluetoothConnectPermission) {
                                Toast.makeText(activityContext, "需要蓝牙连接权限才能开启房间，请在应用设置中授予。", Toast.LENGTH_LONG).show()
                                permissionManager.manageBluetoothPermissions()
                                return@ConnectionSelector
                            }
                            if (isHost) {
                                Toast.makeText(activityContext, "房间已开启", Toast.LENGTH_SHORT).show()
                                return@ConnectionSelector
                            }
                            if (isConnected) {
                                Toast.makeText(activityContext, "你已连接到其他房间，请先断开", Toast.LENGTH_SHORT).show()
                                return@ConnectionSelector
                            }
                            bluetoothClient.close() // 确保客户端连接已关闭
                            coroutineScope.launch {
                                // 添加主机信息到连接列表
                                val hostClient = ConnectedClient(
                                    id = "LOCAL_HOST_ID",
                                    name = playerNickname.value,
                                    avatar = ""
                                )
                                onClientListUpdated(listOf(hostClient))
                                onSetIsHost(true)
                                hasJoinedRoom = true // 主机也视为已加入房间
                                Toast.makeText(activityContext, "房间已开启！", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSearchRoom = {
                            @SuppressLint("MissingPermission")
                            if (!hasBluetoothScanPermission) {
                                Toast.makeText(activityContext, "需要蓝牙扫描权限才能搜索房间，请在应用设置中授予。", Toast.LENGTH_LONG).show()
                                permissionManager.manageBluetoothPermissions()
                                return@ConnectionSelector
                            }
                            if (isHost) {
                                Toast.makeText(activityContext, "你已开启房间，无法搜索", Toast.LENGTH_SHORT).show()
                                return@ConnectionSelector
                            }
                            if (isConnected) {
                                Toast.makeText(activityContext, "你已连接到其他房间，请先断开", Toast.LENGTH_SHORT).show()
                                return@ConnectionSelector
                            }

                            bluetoothServer.close() // 确保服务器已关闭
                            showSearchDialog.value = true
                        }
                    )
                }

                // --- 开始游戏按钮 ---
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    StartGameButton(
                        onStartGame = {
                            if (isHost) {
                                if (connectedClients.size >= 1) {
                                    Log.d("OnlineRoomScreen", "主机开始游戏，当前玩家数: ${connectedClients.size}")
                                    Toast.makeText(activityContext, "进入游戏界面！", Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch {
                                        // 广播游戏开始消息
                                        Log.d("OnlineRoomScreen", "广播游戏开始消息")
                                        bluetoothServer.sendDataToAllClients("START_GAME:")

                                        // 更新游戏状态
                                        withContext(Dispatchers.Main) {
                                            isGameStarted = true
                                            gameState = gameState.copy(
                                                isGameRunning = true,
                                                isGameReady = false
                                            )
                                            Log.d("OnlineRoomScreen", "游戏状态已更新，isGameStarted: $isGameStarted")
                                        }
                                    }
                                } else {
                                    Toast.makeText(activityContext, "至少需要一名玩家才能开始游戏", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(activityContext, "只有主机才能开始游戏", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // 修改消息处理部分
    LaunchedEffect(Unit) {
        bluetoothClient.messageReceived.collect { message ->
            coroutineScope.launch {
                Log.d("OnlineRoomScreen", "收到消息: $message")

                // 处理可能包含多个消息的情况
                val messages = message.split("\n").filter { it.isNotBlank() }
                if (messages.isEmpty()) {
                    Log.d("OnlineRoomScreen", "收到空消息，跳过处理")
                    return@launch
                }

                // 处理每个消息
                messages.forEach { singleMessage ->
                    try {
                        when {
                            singleMessage.startsWith("UPDATE_HAND:") -> {
                                Log.d("OnlineRoomScreen", "处理 UPDATE_HAND 消息: $singleMessage")
                                val handJson = singleMessage.substringAfter("UPDATE_HAND:")
                                try {
                                    Log.d("OnlineRoomScreen", "开始解析 UPDATE_HAND 消息: $handJson")
                                    val cardsJsonArray = JSONArray(handJson)
                                    val updatedCards = mutableListOf<Card>()

                                    Log.d("OnlineRoomScreen", "解析卡牌数组，长度: ${cardsJsonArray.length()}")
                                    for (i in 0 until cardsJsonArray.length()) {
                                        val cardObj = cardsJsonArray.getJSONObject(i)
                                        val suitStr = cardObj.getString("suit").lowercase()
                                        val valueStr = cardObj.getString("value")

                                        Log.d("OnlineRoomScreen", "解析卡牌: suit=$suitStr, value=$valueStr")

                                        val suit = when (suitStr) {
                                            "hearts" -> Suit.HEARTS
                                            "spades" -> Suit.SPADES
                                            "diamonds" -> Suit.DIAMONDS
                                            "clubs" -> Suit.CLUBS
                                            else -> throw IllegalArgumentException("Invalid suit: $suitStr")
                                        }

                                        val rank = when (valueStr) {
                                            "A" -> Rank.ACE
                                            "J" -> Rank.JACK
                                            "Q" -> Rank.QUEEN
                                            "K" -> Rank.KING
                                            else -> {
                                                val numericValue = valueStr.toIntOrNull()
                                                if (numericValue != null) {
                                                    Rank.values().find { it.value == numericValue }
                                                        ?: throw IllegalArgumentException("Invalid rank value: $numericValue")
                                                } else {
                                                    throw IllegalArgumentException("Invalid rank format: $valueStr")
                                                }
                                            }
                                        }

                                        val card = Card(suit, rank)
                                        updatedCards.add(card)
                                        Log.d("OnlineRoomScreen", "成功添加卡牌: ${card.rank}${card.suit}")
                                    }

                                    // 更新本地玩家手牌
                                    val clientId = bluetoothClient.getClientId()
                                    Log.d("OnlineRoomScreen", "更新玩家 [$clientId] 手牌，新牌数: ${updatedCards.size}")
                                    Log.d("OnlineRoomScreen", "新牌列表: ${updatedCards.joinToString { "${it.rank}${it.suit}" }}")

                                    // 使用 initializePlayerHand 完全替换手牌
                                    playerHandManager.initializePlayerHand(clientId, updatedCards)

                                    // 更新游戏状态中的手牌
                                    val newState = gameState.copy(
                                        playerCards = updatedCards,
                                        players = gameState.players.map { player ->
                                            if (player.id == clientId) {
                                                player.copy(playerCards = updatedCards)
                                            } else {
                                                player
                                            }
                                        }
                                    )
                                    gameState = newState
                                    Log.d("OnlineRoomScreen", "更新手牌成功: ${updatedCards.size} 张")
                                } catch (e: Exception) {
                                    Log.e("OnlineRoomScreen", "解析 UPDATE_HAND 消息失败: ${e.message}", e)
                                    Log.e("OnlineRoomScreen", "错误详情: ${e.stackTraceToString()}")
                                    Toast.makeText(activityContext, "更新手牌失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            singleMessage.startsWith("TURN_CHANGED:") -> {
                                Log.d("OnlineRoomScreen", "处理 TURN_CHANGED 消息: $singleMessage")
                                try {
                                    val nextPlayerIndex = singleMessage.substringAfter("TURN_CHANGED:").toInt()
                                    val newState = gameState.copy(
                                        currentPlayerIndex = nextPlayerIndex,
                                        currentPlayer = gameState.players.getOrNull(nextPlayerIndex)?.id ?: "",
                                        isMyTurn = if (isHost) nextPlayerIndex == 0 else nextPlayerIndex == 1
                                    )
                                    gameState = newState
                                    Log.d("OnlineRoomScreen", "回合变更: 下一个玩家索引=$nextPlayerIndex, 是否我的回合=${newState.isMyTurn}")
                                } catch (e: Exception) {
                                    Log.e("OnlineRoomScreen", "处理回合变更消息失败: ${e.message}", e)
                                }
                            }
                            else -> {
                                Log.d("OnlineRoomScreen", "收到未知消息: $singleMessage")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OnlineRoomScreen", "处理消息失败: ${e.message}", e)
                        Toast.makeText(activityContext, "处理消息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 监听连接状态变化
    LaunchedEffect(Unit) {
        bluetoothClient.connectionState.collectLatest { connected ->
            Log.d("OnlineRoomScreen", "蓝牙连接状态变化: $connected")
            isConnected = connected
            if (!connected && !isHost) {
                Log.d("OnlineRoomScreen", "客户端断开连接，清空列表")
                onClientListUpdated(emptyList())
                hasJoinedRoom = false // 重置加入状态
            }
        }
    }

    // --- 蓝牙服务器逻辑 (主机端) ---
    LaunchedEffect(bluetoothServer) {
        bluetoothServer.startServer(
            onMessageReceived = { message, senderId ->
                val trimmedMsg = message.trim()
                Log.d("OnlineRoomScreen", "主机收到来自 [$senderId] 的消息: $trimmedMsg")
                Log.d("OnlineRoomScreen", "消息长度: ${trimmedMsg.length}, 消息前缀: ${trimmedMsg.take(10)}")
                Log.d("OnlineRoomScreen", "开始处理消息，类型: ${if (trimmedMsg.startsWith("PLAY_CARDS:")) "PLAY_CARDS" else "其他"}")

                when {
                    trimmedMsg.startsWith("PLAY_CARDS:") -> {
                        Log.d("OnlineRoomScreen", "开始处理 PLAY_CARDS 消息")
                        val cardsJson = trimmedMsg.substringAfter("PLAY_CARDS:")
                        try {
                            Log.d("OnlineRoomScreen", "当前连接的客户端列表: ${connectedClients.joinToString { "${it.name}(${it.id})" }}")
                            val sender = connectedClients.find { it.id == senderId }
                            val clientName = sender?.name ?: "未知玩家"
                            Log.d("OnlineRoomScreen", "找到出牌者: $clientName (ID: $senderId)")
                            val cards = JSONArray(cardsJson)
                            val playedCards = mutableListOf<Card>()
                            for (i in 0 until cards.length()) {
                                val cardObj = cards.getJSONObject(i)
                                val suit = when (cardObj.getString("suit").lowercase()) {
                                    "hearts" -> Suit.HEARTS
                                    "spades" -> Suit.SPADES
                                    "diamonds" -> Suit.DIAMONDS
                                    "clubs" -> Suit.CLUBS
                                    else -> throw IllegalArgumentException("Invalid suit")
                                }
                                val rank = when (cardObj.getString("value")) {
                                    "A" -> Rank.ACE
                                    "J" -> Rank.JACK
                                    "Q" -> Rank.QUEEN
                                    "K" -> Rank.KING
                                    else -> Rank.values().find { it.value == cardObj.getString("value").toInt() }
                                        ?: throw IllegalArgumentException("Invalid rank")
                                }
                                playedCards.add(Card(suit, rank))
                            }
                            Log.d("OnlineRoomScreen", "解析出牌数据: ${playedCards.joinToString { "${it.rank}${it.suit}" }}")

                            // 更新发送者的手牌
                            val updatedClients = connectedClients.map { client ->
                                if (client.id == senderId) {
                                    // 获取发送者原有的手牌
                                    val originalHand = playerHandManager.getPlayerHand(senderId)
                                    Log.d("OnlineRoomScreen", "发送者 [$senderId] 原有手牌: ${originalHand.joinToString { "${it.rank}${it.suit}" }}")
                                    Log.d("OnlineRoomScreen", "发送者 [$senderId] 原有手牌数量: ${originalHand.size}")
                                    // 从发送者的手牌中移除已出的牌
                                    val remainingCards = originalHand.filter { card -> !playedCards.contains(card) }
                                    Log.d("OnlineRoomScreen", "更新客户端 ${client.name} 的手牌: 原有 ${originalHand.size} 张，移除 ${playedCards.size} 张，剩余 ${remainingCards.size} 张")
                                    client.copy(playerCards = remainingCards)
                                } else {
                                    client
                                }
                            }

                            val nextPlayerIndex = (gameState.currentPlayerIndex + 1) % gameState.players.size
                            Log.d("OnlineRoomScreen", "下一个玩家索引: $nextPlayerIndex, 当前玩家列表: ${gameState.players.joinToString { "${it.name}(${it.id})" }}")

                            coroutineScope.launch(Dispatchers.Main) {
                                try {
                                    Log.d("OnlineRoomScreen", "开始更新游戏状态")
                                    val newState = GameState(
                                        playerCards = gameState.playerCards,
                                        lastPlayedCards = playedCards,
                                        lastPlayedBy = clientName,
                                        currentPlayerIndex = nextPlayerIndex,
                                        currentPlayer = gameState.players[nextPlayerIndex].id,
                                        isMyTurn = nextPlayerIndex == 0,
                                        players = updatedClients,  // 使用更新后的客户端列表
                                        isDealing = gameState.isDealing,
                                        dealerIndex = gameState.dealerIndex,
                                        cardsDealt = gameState.cardsDealt,
                                        isGameReady = gameState.isGameReady,
                                        isGameRunning = gameState.isGameRunning
                                    )
                                    Log.d("OnlineRoomScreen", "更新前状态: lastPlayedCards=${gameState.lastPlayedCards.size}, lastPlayedBy=${gameState.lastPlayedBy}")
                                    gameState = newState
                                    Log.d("OnlineRoomScreen", "更新后状态: lastPlayedCards=${gameState.lastPlayedCards.size}, lastPlayedBy=${gameState.lastPlayedBy}")

                                    // 发送更新后的手牌给出牌的客户端
                                    val sender = updatedClients.find { it.id == senderId }
                                    if (sender != null) {
                                        val remainingCardsJson = JSONArray().apply {
                                            sender.playerCards.forEach { card ->
                                                put(JSONObject().apply {
                                                    put("suit", card.suit.toString())
                                                    put("value", when (card.rank) {
                                                        Rank.ACE -> "A"
                                                        Rank.JACK -> "J"
                                                        Rank.QUEEN -> "Q"
                                                        Rank.KING -> "K"
                                                        else -> card.rank.value.toString()
                                                    })
                                                })
                                            }
                                        }.toString()
                                        Log.d("OnlineRoomScreen", "发送更新后的手牌给客户端 ${sender.name}: $remainingCardsJson")
                                        bluetoothServer.sendDataToClient(senderId, "UPDATE_HAND:$remainingCardsJson")
                                    }

                                    delay(1000)
                                    Log.d("OnlineRoomScreen", "广播出牌信息: CARD_PLAYED:$clientName:$cardsJson")
                                    bluetoothServer.sendDataToAllClients("CARD_PLAYED:$clientName:$cardsJson")

                                    Log.d("OnlineRoomScreen", "广播回合变更: TURN_CHANGED:$nextPlayerIndex")
                                    bluetoothServer.sendDataToAllClients("TURN_CHANGED:$nextPlayerIndex")
                                    Log.d("OnlineRoomScreen", "消息处理完成")
                                } catch (e: Exception) {
                                    Log.e("OnlineRoomScreen", "更新游戏状态失败: ${e.message}", e)
                                    Log.e("OnlineRoomScreen", "错误详情: ${e.stackTraceToString()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("OnlineRoomScreen", "处理出牌信息失败: ${e.message}", e)
                            Log.e("OnlineRoomScreen", "错误详情: ${e.stackTraceToString()}")
                        }
                    }
                    trimmedMsg.startsWith("PLAYER_JOIN:") -> {
                        val clientNickname = trimmedMsg.substringAfter("PLAYER_JOIN:")
                        val newClient = ConnectedClient(
                            id = senderId,
                            name = clientNickname,
                            avatar = ""
                        )
                        val existingClientIndex = connectedClients.indexOfFirst { it.id == senderId }
                        val updatedClients = if (existingClientIndex != -1) {
                            // 更新现有客户端的昵称
                            connectedClients.toMutableList().apply {
                                this[existingClientIndex] = newClient
                            }
                        } else {
                            // 添加新客户端
                            connectedClients + newClient
                        }

                        // 更新列表
                        onClientListUpdated(updatedClients)
                        Toast.makeText(activityContext, "客户端 ${clientNickname} ${if (existingClientIndex != -1) "(已更新) " else ""}已加入房间", Toast.LENGTH_SHORT).show()

                        // 构建 ROOM_UPDATE 消息
                        val currentPlayersJsonArray = JSONArray().apply {
                            updatedClients.forEach { client ->
                                put(JSONObject().apply {
                                    put("id", client.id)
                                    put("name", client.name)
                                    put("avatar", client.avatar)
                                })
                            }
                        }
                        val roomUpdateMessage = JSONObject().apply {
                            put("type", "ROOM_UPDATE")
                            put("clients", currentPlayersJsonArray)
                        }.toString()

                        // 发送 ROOM_UPDATE 给所有客户端
                        updatedClients.filter { it.id != "LOCAL_HOST_ID" }
                            .forEach { client ->
                                Log.d("OnlineRoomScreen", "主机发送 ROOM_UPDATE 给客户端 [${client.id}]: $roomUpdateMessage")
                                bluetoothServer.sendDataToClient(client.id, roomUpdateMessage)
                            }
                    }
                    trimmedMsg.startsWith("PLAYER_ACTION:") -> {
                        Toast.makeText(activityContext, "主机处理玩家操作: $trimmedMsg", Toast.LENGTH_SHORT).show()
                        bluetoothServer.sendDataToAllClients(trimmedMsg)
                    }
                    else -> {
                        Log.d("OnlineRoomScreen", "主机收到未知消息: [$trimmedMsg]")
                        Log.d("OnlineRoomScreen", "消息格式检查: startsWith PLAY_CARDS: ${trimmedMsg.startsWith("PLAY_CARDS:")}")
                        Toast.makeText(activityContext, "主机收到未知消息: [$trimmedMsg]", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClientConnected = { clientId, clientName ->
                Log.d("OnlineRoomScreen", "新客户端连接 (未加入房间): $clientId, $clientName")
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(activityContext, "新客户端 ${clientName} 已连接，等待加入信息...", Toast.LENGTH_SHORT).show()
                }
            },
            onClientDisconnected = { clientId ->
                Log.d("OnlineRoomScreen", "客户端断开连接: $clientId")
                val disconnectedClient = connectedClients.find { it.id == clientId }
                onClientListUpdated(connectedClients.filter { it.id != clientId })
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(activityContext, "${disconnectedClient?.name ?: clientId} 已断开连接", Toast.LENGTH_SHORT).show()
                }
                bluetoothServer.sendDataToAllClients("PLAYER_LEFT:$clientId")
            }
        )
    }

    // 修改连接成功后的处理
    LaunchedEffect(isConnected) {
        Log.d("OnlineRoomScreen", "LaunchedEffect(isConnected) 触发, isConnected: $isConnected")
        if (isConnected && !isHost && !hasJoinedRoom) {
            Log.d("OnlineRoomScreen", "客户端连接成功，准备发送 PLAYER_JOIN 消息")
            try {
                // 等待一小段时间确保连接稳定
                delay(1000)
                if (isConnected) { // 再次检查连接状态
                    Log.d("OnlineRoomScreen", "发送 PLAYER_JOIN 消息")
                    bluetoothClient.sendData("PLAYER_JOIN:${playerNickname.value}")
                    hasJoinedRoom = true
                }
            } catch (e: Exception) {
                Log.e("OnlineRoomScreen", "发送 PLAYER_JOIN 消息失败: ${e.message}", e)
                Toast.makeText(activityContext, "加入房间失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- 昵称输入对话框 ---
    if (showNicknameDialog.value) {
        AlertDialog(
            onDismissRequest = { /* Cannot be dismissed, nickname is mandatory */ },
            title = { Text("输入游戏昵称") },
            text = {
                TextField(
                    value = playerNickname.value,
                    onValueChange = { playerNickname.value = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playerNickname.value.isNotBlank()) {
                            showNicknameDialog.value = false
                        } else {
                            Toast.makeText(activityContext, "昵称不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = playerNickname.value.isNotBlank()
                ) {
                    Text("确认")
                }
            }
        )
    }

    // --- 搜索房间对话框 ---
    @SuppressLint("MissingPermission")
    if (showSearchDialog.value) {
        val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }

        // 获取已配对设备列表
        LaunchedEffect(Unit) {
            val devices = bluetoothServer.getPairedDevices()
            pairedDevices.clear()
            pairedDevices.addAll(devices)
        }

        AlertDialog(
            onDismissRequest = {
                showSearchDialog.value = false
            },
            title = { Text("选择房间 (已配对设备)") },
            text = {
                Column {
                    Text("请选择要连接的房间：")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pairedDevices.isEmpty()) {
                        Text("没有已配对的设备。请先在系统设置中配对设备。", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        LazyColumn {
                            items(pairedDevices) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSearchDialog.value = false
                                            bluetoothServer.close()

                                            coroutineScope.launch {
                                                try {
                                                    bluetoothClient.connectToServer(
                                                        device,
                                                        onConnected = {
                                                            isConnected = true
                                                            onSetIsHost(false)
                                                            hasJoinedRoom = false
                                                            Toast.makeText(activityContext, "已连接到 ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onDisconnected = {
                                                            isConnected = false
                                                            onClientListUpdated(emptyList())
                                                            Toast.makeText(activityContext, "已断开连接", Toast.LENGTH_SHORT).show()
                                                            hasJoinedRoom = false
                                                        },
                                                        onFailed = { errorMessage ->
                                                            isConnected = false
                                                            onClientListUpdated(emptyList())
                                                            Toast.makeText(activityContext, "连接失败: $errorMessage", Toast.LENGTH_LONG).show()
                                                            hasJoinedRoom = false
                                                        }
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e("OnlineRoomScreen", "连接失败: ${e.message}", e)
                                                    Toast.makeText(activityContext, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.LightGray)
                                            .border(1.dp, Color.DarkGray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "B", color = Color.DarkGray)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = device.name ?: "未知设备",
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = device.address,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSearchDialog.value = false
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun AvatarBox(index: Int, nickname: String = "等待玩家") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (index == 0) Color(0xFFADD8E6) else Color.LightGray)
                .border(2.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (nickname == "等待玩家" || nickname.isBlank()) {
                Text(text = "?", color = Color.DarkGray, fontSize = 36.sp)
            } else {
                // 如果是自己的昵称，显示"你"
                if (nickname == "你") {
                    Text(text = "你", color = Color.DarkGray, fontSize = 36.sp)
                } else {
                    Text(text = nickname.first().uppercase(), color = Color.DarkGray, fontSize = 36.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = nickname, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ConnectionSelector(
    isBluetooth: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHostRoom: () -> Unit,
    onSearchRoom: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "蓝牙", color = if (isBluetooth) Color.Blue else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isBluetooth,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Blue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    ),
                    enabled = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "WiFi", color = if (!isBluetooth) Color.Red else Color.Gray)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Button(
                onClick = onHostRoom,
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                )
            ) {
                Text(text = "开启房间", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSearchRoom,
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White
                )
            ) {
                Text(text = "搜索房间", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun StartGameButton(onStartGame: () -> Unit) {
    Button(
        onClick = onStartGame,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(text = "开始游戏", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun GameScreen(
    gameState: GameState,
    onCardSelected: (Card) -> Unit,
    onStartDealing: () -> Unit,
    isHost: Boolean,
    bluetoothServer: BluetoothServer?,
    bluetoothClient: BluetoothClient?,
    onGameStateUpdate: (GameState) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("GameScreen", "开始渲染游戏界面")
    Log.d("GameScreen", "当前状态: 手牌数=${gameState.playerCards.size}, 最后出牌=${gameState.lastPlayedCards.size}, 出牌者=${gameState.lastPlayedBy}, 当前玩家=${gameState.currentPlayer}, 是否我的回合=${gameState.isMyTurn}")

    val selectedCards = remember { mutableStateListOf<Card>() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 使用 mutableStateOf 来跟踪按钮状态
    var canPlay by remember { mutableStateOf(false) }

    // 在 LaunchedEffect 中更新 canPlay 状态
    LaunchedEffect(selectedCards.size, gameState.isMyTurn) {
        // 只要有选中的牌且轮到自己的回合就可以出牌
        canPlay = selectedCards.isNotEmpty() && gameState.isMyTurn
        Log.d("GameScreen", "更新canPlay: selectedCards=${selectedCards.size}, isMyTurn=${gameState.isMyTurn}, canPlay=$canPlay")
    }

    // 添加对 gameState 变化的监听
    LaunchedEffect(gameState) {
        Log.d("GameScreen", "gameState 发生变化")
        Log.d("GameScreen", "新的状态: 手牌数=${gameState.playerCards.size}, 最后出牌=${gameState.lastPlayedCards.size}, 出牌者=${gameState.lastPlayedBy}, 当前玩家=${gameState.currentPlayer}, 是否我的回合=${gameState.isMyTurn}")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部玩家信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 显示四个玩家位置
                for (i in 0..3) {
                    val player = gameState.players.getOrNull(i)
                    val isCurrentPlayer = player?.id == gameState.currentPlayer
                    Log.d("GameScreen", "玩家 ${player?.name} 状态: isCurrentPlayer=$isCurrentPlayer, id=${player?.id}, currentPlayer=${gameState.currentPlayer}")

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                width = 2.dp,
                                color = if (isCurrentPlayer)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray,
                                shape = CircleShape
                            )
                            .background(
                                color = if (isCurrentPlayer)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (player != null) {
                            Text(
                                text = player.name.first().uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 最后出牌信息 - 放在屏幕中间
            if (gameState.lastPlayedCards.isNotEmpty()) {
                Log.d("GameScreen", "渲染最后出牌区域: 牌数=${gameState.lastPlayedCards.size}, 出牌者=${gameState.lastPlayedBy}")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)  // 恢复为1f
                        .padding(vertical = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${gameState.lastPlayedBy} 出牌",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)  // 设置固定高度
                        ) {
                            gameState.lastPlayedCards.forEach { card ->
                                Log.d("GameScreen", "渲染卡牌: ${card.rank}${card.suit}")
                                CardItem(
                                    card = card,
                                    isSelectable = false,
                                    modifier = Modifier
                                        .size(width = 120.dp, height = 180.dp)  // 调整到更合适的尺寸
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Log.d("GameScreen", "渲染等待出牌区域")
                // 如果没有出牌，显示一个占位区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "等待出牌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!gameState.isGameReady) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onStartDealing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text("开始发牌", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // 操作按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (!gameState.isMyTurn) {
                                Toast.makeText(context, "还没轮到你的回合", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // 在点击按钮时验证出牌是否有效
                            val isValidPlay = if (gameState.lastPlayedCards.isEmpty()) {
                                // 如果是第一手牌，只需要验证牌型是否有效
                                Log.d("GameScreen", "验证第一手牌: ${selectedCards.size}张, 牌型: ${selectedCards.joinToString { "${it.rank}${it.suit}" }}")
                                val isValid = isValidHand(selectedCards)
                                Log.d("GameScreen", "第一手牌验证结果: $isValid")
                                isValid
                            } else {
                                try {
                                    // 如果不是第一手牌，需要验证：
                                    // 1. 牌型是否有效
                                    // 2. 牌数是否相同
                                    // 3. 是否大于上一手牌
                                    Log.d("GameScreen", "验证出牌: ${selectedCards.size}张, 牌型: ${selectedCards.joinToString { "${it.rank}${it.suit}" }}")
                                    Log.d("GameScreen", "上一手牌: ${gameState.lastPlayedCards.size}张, 牌型: ${gameState.lastPlayedCards.joinToString { "${it.rank}${it.suit}" }}")

                                    if (selectedCards.size != gameState.lastPlayedCards.size) {
                                        Log.d("GameScreen", "牌数不匹配: 当前${selectedCards.size}张, 上一手${gameState.lastPlayedCards.size}张")
                                        false
                                    } else {
                                        val isValid = isValidHand(selectedCards)
                                        val isBigger = compareHands(selectedCards, gameState.lastPlayedCards) > 0
                                        Log.d("GameScreen", "牌型验证结果: $isValid, 大小比较结果: $isBigger")
                                        isValid && isBigger
                                    }
                                } catch (e: Exception) {
                                    Log.e("GameScreen", "比较牌型失败: ${e.message}")
                                    false
                                }
                            }

                            if (isValidPlay) {
                                try {
                                    // 将选中的牌转换为JSON
                                    val cardsJson = JSONArray().apply {
                                        selectedCards.forEach { card ->
                                            put(JSONObject().apply {
                                                put("suit", card.suit.toString())
                                                put("value", when (card.rank) {
                                                    Rank.ACE -> "A"
                                                    Rank.JACK -> "J"
                                                    Rank.QUEEN -> "Q"
                                                    Rank.KING -> "K"
                                                    else -> card.rank.value.toString()
                                                })
                                            })
                                        }
                                    }.toString()

                                    // 计算下一个玩家
                                    val nextPlayerIndex = (gameState.currentPlayerIndex + 1) % gameState.players.size

                                    // 发送出牌消息
                                    if (isHost) {
                                        val playerName = gameState.players.find { it.id == "LOCAL_HOST_ID" }?.name
                                            ?: throw IllegalStateException("Host player not found")
                                        bluetoothServer?.sendDataToAllClients("CARD_PLAYED:$playerName:$cardsJson")
                                        bluetoothServer?.sendDataToAllClients("TURN_CHANGED:$nextPlayerIndex")

                                        // 更新本地状态
                                        coroutineScope.launch {
                                            // 创建新的手牌列表，移除已出的牌
                                            val newPlayerCards = gameState.playerCards.filter { card -> !selectedCards.contains(card) }
                                            Log.d("GameScreen", "更新前手牌: ${gameState.playerCards.size}, 更新后手牌: ${newPlayerCards.size}")

                                            // 更新游戏状态
                                            val newState = gameState.copy(
                                                lastPlayedCards = selectedCards.toList(),
                                                lastPlayedBy = playerName,
                                                playerCards = newPlayerCards,
                                                currentPlayerIndex = nextPlayerIndex,
                                                currentPlayer = gameState.players.getOrNull(nextPlayerIndex)?.id
                                                    ?: throw IllegalStateException("Next player not found"),
                                                isMyTurn = nextPlayerIndex == 0  // 主机永远是索引0
                                            )

                                            // 更新状态
                                            onGameStateUpdate(newState)
                                            Log.d("GameScreen", "主机更新游戏状态: 剩余手牌=${newState.playerCards.size}, 最后出牌=${newState.lastPlayedCards.size}")

                                            // 清空选中的牌
                                            selectedCards.clear()
                                        }
                                    } else {
                                        bluetoothClient?.sendData("PLAY_CARDS:$cardsJson")
                                    }
                                } catch (e: Exception) {
                                    Log.e("GameScreen", "出牌失败: ${e.message}", e)
                                    Toast.makeText(context, "出牌失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "无效的出牌组合", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canPlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("出牌")
                    }

                    Button(
                        onClick = {
                            if (!gameState.isMyTurn) {
                                Toast.makeText(context, "还没轮到你的回合", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            try {
                                // 计算下一个玩家
                                val nextPlayerIndex = (gameState.currentPlayerIndex + 1) % gameState.players.size

                                // 发送不出牌消息
                                if (isHost) {
                                    bluetoothServer?.sendDataToAllClients("PLAYER_PASSED:${gameState.players.first { it.id == "LOCAL_HOST_ID" }.name}")
                                    bluetoothServer?.sendDataToAllClients("TURN_CHANGED:$nextPlayerIndex")
                                } else {
                                    bluetoothClient?.sendData("PASS:")
                                }

                                // 更新本地状态
                                coroutineScope.launch {
                                    onGameStateUpdate(gameState.copy(
                                        currentPlayerIndex = nextPlayerIndex,
                                        currentPlayer = gameState.players[nextPlayerIndex].id,
                                        isMyTurn = if (isHost) nextPlayerIndex == 0 else nextPlayerIndex == 1
                                    ))
                                }
                                selectedCards.clear()
                            } catch (e: Exception) {
                                Log.e("GameScreen", "不出牌失败: ${e.message}", e)
                                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = gameState.isMyTurn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("不出")
                    }
                }

                // 显示玩家手牌
                if (gameState.playerCards.isNotEmpty()) {
                    Log.d("GameScreen", "显示玩家手牌，数量: ${gameState.playerCards.size}")
                    PlayerCards(
                        cards = sortCards(gameState.playerCards),
                        selectedCards = selectedCards,
                        onCardSelected = { card ->
                            Log.d("GameScreen", "选择卡牌: ${card.rank}${card.suit}")
                            if (selectedCards.contains(card)) {
                                selectedCards.remove(card)
                                Log.d("GameScreen", "移除卡牌，当前选中: ${selectedCards.size}")
                            } else {
                                selectedCards.add(card)
                                Log.d("GameScreen", "添加卡牌，当前选中: ${selectedCards.size}")
                            }
                            onCardSelected(card)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerCards(
    cards: List<Card>,
    selectedCards: List<Card>,
    onCardSelected: (Card) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("PlayerCards", "开始渲染玩家手牌，数量: ${cards.size}, 已选中: ${selectedCards.size}")

    Box(
        modifier = modifier
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(-18.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(cards) { card ->
                Log.d("PlayerCards", "渲染卡牌: ${card.rank}${card.suit}, 是否选中: ${selectedCards.contains(card)}")
                val isSelected = selectedCards.contains(card)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .graphicsLayer {
                            // 选中时上浮效果
                            translationY = if (isSelected) -30f else 0f
                        }
                        .animateContentSize()
                        .clickable {
                            Log.d("PlayerCards", "点击卡牌: ${card.rank}${card.suit}")
                            onCardSelected(card)
                        }
                ) {
                    CardItem(
                        card = card,
                        isSelectable = true,
                        onCardSelected = { onCardSelected(card) }
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = Color(0x40FF4081),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardItem(
    card: Card,
    isSelectable: Boolean = false,
    onCardSelected: ((Card) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Log.d("CardItem", "渲染卡牌: ${card.rank}${card.suit}")

    // 获取花色前缀
    val suitPrefix = when (card.suit) {
        Suit.HEARTS -> "heart"
        Suit.SPADES -> "spade"
        Suit.DIAMONDS -> "diamond"
        Suit.CLUBS -> "club"
    }

    // 获取点数后缀
    val rankSuffix = when (card.rank) {
        Rank.ACE -> "a"
        Rank.JACK -> "j"
        Rank.QUEEN -> "q"
        Rank.KING -> "k"
        else -> card.rank.value.toString()
    }

    // 组合资源名称（格式示例：hearta, spade10）
    val resName = "${suitPrefix}${rankSuffix}".lowercase()

    Box(
        modifier = modifier
            .size(width = 70.dp, height = 105.dp)
            .then(
                if (isSelectable && onCardSelected != null) {
                    Modifier.clickable { onCardSelected(card) }
                } else {
                    Modifier
                }
            )
    ) {
        Image(
            painter = painterResource(id = LocalContext.current.resources.getIdentifier(
                resName,
                "drawable",
                LocalContext.current.packageName
            )),
            contentDescription = "Card ${card.rank}${card.suit}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}