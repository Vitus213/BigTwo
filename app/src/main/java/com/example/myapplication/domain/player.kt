package com.example.myapplication.domain

import com.example.myapplication.ai.aiPlay
import java.io.Serializable

import com.example.myapplication.ai.HardAI


class Player(
    val name: String,
    val isHuman: Boolean,

    val difficulty: String = "NORMAL",  // 添加难度属性

    private val hand: MutableList<Card> = mutableListOf()
) : Serializable {

    fun playCards(previousHand: List<Card>): List<Card> {
        return if (isHuman) {

            hand.removeAll(previousHand)
            emptyList()
        } else {
            // 根据难度选择不同的 AI 策略
            val cardsToPlay = when (difficulty) {
                "HARD" -> HardAI.play(hand, previousHand)  // 使用困难 AI
                else -> aiPlay(hand, previousHand)         // 使用简单 AI
            }


            if (cardsToPlay.isNotEmpty() && hand.containsAll(cardsToPlay)) {
                hand.removeAll(cardsToPlay)
            }
            cardsToPlay
        }
    }

    fun getHand(): List<Card> = hand.toList()

    fun addCards(cards: List<Card>) {
        hand.addAll(cards)
        // 保持手牌按牌值和花色排序
        hand.sortWith(compareBy(
            { it.rank.value },
            { it.suit }
        ))
    }

    fun clearHand() {
        hand.clear()
    }

    fun hasCard(card: Card): Boolean {
        return hand.contains(card)
    }

    // 可选：手牌数量属性
    val handSize: Int get() = hand.size

    // 可选：检查是否还有牌
    fun hasCards(): Boolean = hand.isNotEmpty()

}