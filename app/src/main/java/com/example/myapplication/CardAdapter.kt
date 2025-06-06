package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.domain.Card
import com.example.myapplication.domain.Rank
import com.example.myapplication.domain.Suit

class CardAdapter(
    private val enableSelection: Boolean = true,
    private val onSelectionChanged: (List<Card>) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private val selectedCards = mutableSetOf<Card>()
    private var isInteractionEnabled = true
    private var currentHand: List<Card> = emptyList()

    private companion object {
        const val FLOAT_DISTANCE = -30f // 上浮距离（负值表示向上）
    }

    fun getCurrentHand() = currentHand.toList()

    // 添加获取选中牌的方法
    fun getSelectedCards() = selectedCards.toList()

    // 添加清除选择的方法
    fun clearSelection() {
        selectedCards.clear()
        notifyDataSetChanged()
        onSelectionChanged(emptyList())
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardImage: ImageView = itemView.findViewById(R.id.iv_card)
        private val root: View = itemView.findViewById(R.id.card_root)
        private val highlight: View = itemView.findViewById(R.id.highlight)

        fun bind(card: Card) {
            // 根据Card数据加载对应图片资源
            val resId = getCardResourceId(card)
            cardImage.setImageResource(resId)

            // 禁用选择功能时不显示高亮和上浮效果
            if (!enableSelection) {
                highlight.visibility = View.GONE
                root.translationY = 0f
                itemView.setOnClickListener(null)
                return
            }

            val isSelected = selectedCards.contains(card)

            // 设置高亮层可见性
            highlight.visibility = if (isSelected) View.VISIBLE else View.GONE

            // 设置上浮效果
            val translationY = if (isSelected) FLOAT_DISTANCE else 0f
            root.translationY = translationY

            // 处理点击事件（仅在启用选择功能时）
            if (isInteractionEnabled) {
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        if (selectedCards.add(card)) {
                            // 选中卡片：上浮并显示高亮
                            root.animate()
                                .translationY(FLOAT_DISTANCE)
                                .setDuration(200)
                                .start()
                            highlight.visibility = View.VISIBLE
                        } else {
                            // 取消选中：恢复原位并隐藏高亮
                            selectedCards.remove(card)
                            root.animate()
                                .translationY(0f)
                                .setDuration(200)
                                .start()
                            highlight.visibility = View.GONE
                        }
                        onSelectionChanged(selectedCards.toList())
                    }
                }
            } else {
                itemView.setOnClickListener(null)
            }
        }

        private fun getCardResourceId(card: Card): Int {
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
                else -> card.rank.value.toString() // 数字牌直接使用数值
            }

            // 组合资源名称（格式示例：hearta, spade10）
            val resName = "${suitPrefix}${rankSuffix}".lowercase()

            return itemView.context.resources.getIdentifier(
                resName,
                "drawable",
                itemView.context.packageName
            )
        }
    }

    fun setInteractionEnabled(enabled: Boolean) {
        isInteractionEnabled = enabled
        notifyDataSetChanged()
    }

    // 更新数据的方法
    fun submitList(list: List<Card>) {
        currentHand = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val currentCard = currentHand[position]
        holder.bind(currentCard)
    }

    override fun getItemCount() = currentHand.size
}