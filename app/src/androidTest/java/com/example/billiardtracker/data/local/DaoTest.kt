package com.example.billiardtracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.billiardtracker.data.local.entity.ClubEntity
import com.example.billiardtracker.data.local.entity.GameEntity
import com.example.billiardtracker.data.local.entity.ParticipantEntity
import com.example.billiardtracker.data.local.entity.RuleEntity
import com.example.billiardtracker.data.local.entity.ShotEntity
import com.example.billiardtracker.data.local.entity.TournamentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tournament_participant_shot_round_trip() = runBlocking {
        val now = System.currentTimeMillis()
        db.tournamentDao().upsert(
            TournamentEntity(
                id = 1, title = "T", clubId = null, gameType = "svobodnaya-piramida",
                moneyPerBallKop = 10000, createdByUserId = 1, refereeUserId = 1,
                status = "active", startedAt = now, finishedAt = null, lastSyncedAt = now,
            )
        )
        db.participantDao().upsert(
            ParticipantEntity(
                id = 10, tournamentId = 1, userId = 1, displayName = "Alice",
                handicapPoints = 0, perBallOverrideKop = null, lastSyncedAt = now,
            )
        )
        db.gameDao().upsert(
            GameEntity(
                id = 100, tournamentId = 1, orderIndex = 1, status = "active",
                startedAt = now, finishedAt = null, winnerParticipantId = null, lastSyncedAt = now,
            )
        )
        db.shotDao().upsert(
            ShotEntity(
                id = 1000, gameId = 100, participantId = 10, kind = "ball",
                ballNumber = 1, pointsDelta = 1, ts = now, enteredByUserId = 1, lastSyncedAt = now,
            )
        )

        assertNotNull(db.tournamentDao().getById(1))
        assertEquals(1, db.participantDao().observeByTournament(1).first().size)
        assertEquals(1, db.gameDao().observeByTournament(1).first().size)
        assertEquals(1, db.shotDao().observeByGame(100).first().size)
    }

    @Test
    fun rule_cache_round_trip() = runBlocking {
        db.ruleDao().upsert(
            RuleEntity(
                slug = "svobodnaya-piramida",
                displayName = "Свободная пирамида",
                markdown = "# Правила\n\nТекст.",
                cachedAt = System.currentTimeMillis(),
            )
        )
        val r = db.ruleDao().getBySlug("svobodnaya-piramida")
        assertNotNull(r)
        assertEquals("Свободная пирамида", r!!.displayName)
    }

    @Test
    fun club_geo_fields() = runBlocking {
        db.clubDao().upsert(
            ClubEntity(
                id = 5, name = "Балтийская Лоза", address = "Невский пр., 100",
                lat = 59.9311, lon = 30.3609, city = "Санкт-Петербург",
                userAdded = false, addedByUserId = null,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        val list = db.clubDao().observeAll().first()
        assertEquals(1, list.size)
        assertEquals(59.9311, list[0].lat, 0.0001)
    }
}
