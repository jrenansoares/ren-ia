package br.com.projetoia.app.data.repository

import br.com.projetoia.app.data.db.entity.FavoriteEntity
import br.com.projetoia.app.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
