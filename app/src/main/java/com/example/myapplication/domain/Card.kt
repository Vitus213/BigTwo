package com.example.myapplication.domain

import java.io.Serializable

// 扑克花色枚举
enum class Suit {



    DIAMONDS,// 方块
    CLUBS,     // 梅花
    HEARTS,    // 红心
    SPADES// 黑桃
}

// 扑克大小枚举（带数值表示）
enum class Rank(val value: Int) {



    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),

    JACK(11),
    QUEEN(12),
    KING(13),
    ACE(14), // A现在是14
    TWO(15); // 2现在是15，最大

}

// 扑克牌数据类
data class Card(
    val suit: Suit,  // 花色
    val rank: Rank   // 大小
) : Comparable<Card>, Serializable {
    // 实现比较接口（按数值排序）
    override fun compareTo(other: Card): Int {
        return this.rank.value.compareTo(other.rank.value)
    }

    // 使用Unicode字符的toString表示
    override fun toString(): String {
        val suitSymbol = when (suit) {
            Suit.SPADES -> "♠"
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
        }

        val rankSymbol = when (rank) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"

            Rank.TWO -> "2"


            else -> rank.value.toString()
        }

        return "$suitSymbol$rankSymbol"
    }
}

