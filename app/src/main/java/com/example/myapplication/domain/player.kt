package com.example.myapplication.domain

import com.example.myapplication.ai.aiPlay
import java.io.Serializable


class Player(
    val name: String,
    val isHuman: Boolean,
    private val hand: MutableList<Card> = mutableListOf()
) : Serializable {

    fun playCards(previousHand: List<Card>): List<Card> {
        return if (isHuman) {
            // 人类玩家通过UI交互出牌，此处返回空表示未处理
            hand.removeAll(previousHand)
            emptyList()
        } else {
            val cardsToPlay = aiPlay(hand, previousHand)
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