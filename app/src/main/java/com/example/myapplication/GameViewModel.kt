package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.Card
import com.example.myapplication.domain.GameManager

class GameViewModel : ViewModel() {
    private val _humanHand = MutableLiveData<List<Card>>()
    val humanHand: LiveData<List<Card>> get() = _humanHand
    lateinit var gameManager: GameManager
    val coroutineScope = viewModelScope

    private val _lastPlayedCards = MutableLiveData<List<Card>>(emptyList())
    val lastPlayedCards: LiveData<List<Card>> get() = _lastPlayedCards

    fun updateLastPlayedCards(cards: List<Card>) {
        _lastPlayedCards.postValue(cards)
    }



    fun initializeGameManager(manager: GameManager) {
        gameManager = manager
    }

    fun setHumanHand(cards: List<Card>) {
        _humanHand.postValue(cards)
    }
}