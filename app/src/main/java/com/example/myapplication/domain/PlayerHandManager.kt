package com.example.myapplication.domain

import android.util.Log

class PlayerHandManager {
    private val playerHands = mutableMapOf<String, MutableList<Card>>()
    private val TAG = "PlayerHandManager"

    // 初始化玩家手牌
    fun initializePlayerHand(playerId: String, cards: List<Card>) {
        playerHands[playerId] = cards.toMutableList()
        Log.d(TAG, "初始化玩家 [$playerId] 手牌: ${cards.size} 张")
    }

    // 更新玩家手牌（移除已出的牌）
    fun updatePlayerHand(playerId: String, playedCards: List<Card>) {
        val currentHand = playerHands[playerId] ?: return
        Log.d(TAG, "更新玩家 [$playerId] 手牌: 原有 ${currentHand.size} 张，移除 ${playedCards.size} 张")
        
        playedCards.forEach { playedCard ->
            val index = currentHand.indexOfFirst { it.suit == playedCard.suit && it.rank == playedCard.rank }
            if (index != -1) {
                currentHand.removeAt(index)
                Log.d(TAG, "移除牌: ${playedCard.suit}${playedCard.rank}")
            } else {
                Log.w(TAG, "警告：尝试移除不存在的牌 ${playedCard.suit}${playedCard.rank}")
            }
        }
        
        Log.d(TAG, "玩家 [$playerId] 剩余手牌: ${currentHand.size} 张")
    }

    // 获取玩家手牌
    fun getPlayerHand(playerId: String): List<Card> {
        return playerHands[playerId]?.toList() ?: emptyList()
    }

    // 检查玩家是否还有牌
    fun hasCards(playerId: String): Boolean {
        return playerHands[playerId]?.isNotEmpty() ?: false
    }

    // 获取玩家剩余牌数
    fun getRemainingCardsCount(playerId: String): Int {
        return playerHands[playerId]?.size ?: 0
    }

    // 清空所有玩家手牌
    fun clearAllHands() {
        playerHands.clear()
        Log.d(TAG, "清空所有玩家手牌")
    }

    // 移除玩家
    fun removePlayer(playerId: String) {
        playerHands.remove(playerId)
        Log.d(TAG, "移除玩家 [$playerId] 的手牌记录")
    }
} 