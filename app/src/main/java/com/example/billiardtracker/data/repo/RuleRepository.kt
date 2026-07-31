package com.example.billiardtracker.data.repo

import com.example.billiardtracker.data.local.dao.RuleDao
import com.example.billiardtracker.data.local.entity.RuleEntity
import com.example.billiardtracker.data.remote.ApiService
import com.example.billiardtracker.domain.rules.GameType
import kotlinx.coroutines.flow.Flow

class RuleRepository(
    private val api: ApiService,
    private val ruleDao: RuleDao,
) {
    fun observeAll(): Flow<List<RuleEntity>> = ruleDao.observeAll()

    suspend fun getBySlug(slug: String): RuleEntity? = ruleDao.getBySlug(slug)

    /**
     * Refresh the list of available rules from `/api/rules`. Existing markdown
     * cache is preserved — only [RuleEntity.displayName] is updated (and new
     * slugs are inserted with an empty markdown, to be fetched on demand).
     */
    suspend fun refreshList(): Result<Unit> = try {
        val res = api.listRules()
        if (!res.isSuccessful) {
            Result.failure(IllegalStateException("HTTP ${res.code()}"))
        } else {
            val list = res.body()!!.rules
            for (r in list) {
                val existing = ruleDao.getBySlug(r.slug)
                ruleDao.upsert(
                    RuleEntity(
                        slug = r.slug,
                        displayName = r.displayName,
                        markdown = existing?.markdown ?: "",
                        cachedAt = existing?.cachedAt ?: 0L,
                    ),
                )
            }
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Fetch the markdown body of a single rule from `/api/rules/:slug` and
     * upsert it into the Room cache.
     */
    suspend fun refreshMarkdown(slug: String): Result<String> = try {
        val res = api.getRuleMarkdown(slug)
        if (!res.isSuccessful) {
            Result.failure(IllegalStateException("HTTP ${res.code()}"))
        } else {
            val md = res.body()!!.string()
            val existing = ruleDao.getBySlug(slug)
            val displayName = existing?.displayName ?: fallbackName(slug)
            ruleDao.upsert(
                RuleEntity(
                    slug = slug,
                    displayName = displayName,
                    markdown = md,
                    cachedAt = System.currentTimeMillis(),
                ),
            )
            Result.success(md)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun fallbackName(slug: String): String =
        GameType.entries.firstOrNull { it.ruleFileSlug == slug }?.displayName ?: slug
}
