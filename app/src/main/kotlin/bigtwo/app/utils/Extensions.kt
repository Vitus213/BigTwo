package bigtwo.app.utils
    fun <T> List<T>.combinations(k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (this.isEmpty()) return emptyList()
        val head = this.first()
        val tail = this.drop(1)
        val withHead = tail.combinations(k - 1).map { it + head }
        val withoutHead = tail.combinations(k)
        return withHead + withoutHead
    }
