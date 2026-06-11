package com.erygra.maskoflight.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  EventBus.kt — نظام الأحداث المركزي (Observer Pattern)
 *  Erygra Universe 2.0 | Mask of Light
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  يتيح التواصل بين الأنظمة المختلفة (Physics, Combat, AI, UI...)
 *  دون اقتران مباشر (Tight Coupling).
 *
 *  الاستخدام:
 *  // إرسال حدث:
 *  EventBus.emit(GameEvent.PlayerDamaged(damage = 15, source = "Enemy"))
 *
 *  // الاستماع لحدث:
 *  EventBus.events.filterIsInstance<GameEvent.PlayerDamaged>().collect { event ->
 *      // handle event
 *  }
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─────────────────────────────────────────────────────────────────────────────
// تعريف الأحداث
// ─────────────────────────────────────────────────────────────────────────────

/** GameEvent — الأحداث المركزية في اللعبة */
sealed class GameEvent {

    // ─── أحداث اللاعب ────────────────────────────────────────────────────

    data class PlayerDamaged(
        val damage: Int,
        val source: String,
        val isCritical: Boolean = false
    ) : GameEvent()

    data class PlayerHealed(val amount: Int) : GameEvent()

    data class PlayerDied(val cause: String) : GameEvent()

    data class PlayerLevelUp(val newLevel: Int, val xpGained: Int) : GameEvent()

    data class PlayerMemoryFragmentGained(
        val amount: Int,
        val source: String
    ) : GameEvent()

    data class PlayerForgetfulnessChanged(
        val newValue: Int,
        val change: Int,
        val reason: String
    ) : GameEvent()

    data class PlayerAbilityUsed(
        val abilityId: String,
        val mfCost: Int,
        val fmGain: Int
    ) : GameEvent()

    data class PlayerReachedSanctuary(
        val sanctuaryId: String,
        val isNew: Boolean
    ) : GameEvent()

    data class PlayerEnteredRegion(
        val regionId: String,
        val fromRegionId: String?
    ) : GameEvent()

    // ─── أحداث القتال ────────────────────────────────────────────────────

    data class EnemyDamaged(
        val enemyId: String,
        val damage: Int,
        val isCritical: Boolean = false
    ) : GameEvent()

    data class EnemyDied(
        val enemyId: String,
        val enemyType: String,
        val xpReward: Int,
        val coinsReward: Int
    ) : GameEvent()

    data class BossPhaseChanged(
        val bossId: String,
        val newPhase: Int
    ) : GameEvent()

    data class BossDefeated(
        val bossId: String,
        val regionId: String
    ) : GameEvent()

    data class ParrySuccess(
        val enemyId: String,
        val counterDamage: Int
    ) : GameEvent()

    data class ComboCountChanged(val count: Int) : GameEvent()

    // ─── أحداث العالم ────────────────────────────────────────────────────

    data class WorldEventStarted(
        val eventType: WorldEventType,
        val durationMinutes: Int
    ) : GameEvent()

    data class WorldEventEnded(val eventType: WorldEventType) : GameEvent()

    data class MapTileDiscovered(
        val regionId: String,
        val tileX: Int,
        val tileY: Int
    ) : GameEvent()

    data class MapShardCollected(
        val regionId: String,
        val shardIndex: Int
    ) : GameEvent()

    data class FastTravelUsed(
        val fromSanctuaryId: String,
        val toSanctuaryId: String,
        val cost: Int
    ) : GameEvent()

    // ─── أحداث المهام ────────────────────────────────────────────────────

    data class QuestStarted(val questId: String) : GameEvent()

    data class QuestStepCompleted(
        val questId: String,
        val stepIndex: Int
    ) : GameEvent()

    data class QuestCompleted(
        val questId: String,
        val xpReward: Int,
        val coinsReward: Int
    ) : GameEvent()

    data class QuestFailed(val questId: String, val reason: String) : GameEvent()

    // ─── أحداث NPC والحوار ───────────────────────────────────────────────

    data class DialogueStarted(val npcId: String) : GameEvent()

    data class DialogueChoiceMade(
        val npcId: String,
        val choiceIndex: Int,
        val choiceText: String
    ) : GameEvent()

    data class DialogueEnded(val npcId: String) : GameEvent()

    data class NPCRelationshipChanged(
        val npcId: String,
        val change: Int,
        val newValue: Int
    ) : GameEvent()

    // ─── أحداث المخزون ───────────────────────────────────────────────────

    data class ItemPickedUp(val itemId: String, val quantity: Int) : GameEvent()

    data class ItemUsed(val itemId: String) : GameEvent()

    data class ItemPurchased(
        val itemId: String,
        val cost: Int,
        val currency: String
    ) : GameEvent()

    data class ItemSold(val itemId: String, val coins: Int) : GameEvent()

    // ─── أحداث الألغاز ───────────────────────────────────────────────────

    data class PuzzleStarted(val puzzleId: String) : GameEvent()

    data class PuzzleSolved(
        val puzzleId: String,
        val timeSeconds: Int
    ) : GameEvent()

    data class PuzzleFailed(val puzzleId: String) : GameEvent()

    // ─── أحداث الحفظ ─────────────────────────────────────────────────────

    data class GameSaved(val slotId: Int, val isAutoSave: Boolean) : GameEvent()

    data class GameLoaded(val slotId: Int) : GameEvent()

    object CloudSyncStarted : GameEvent()
    data class CloudSyncCompleted(val success: Boolean) : GameEvent()

    // ─── أحداث UI ────────────────────────────────────────────────────────

    data class GameStateChanged(
        val from: GameState,
        val to: GameState
    ) : GameEvent()

    data class NotificationShown(
        val message: String,
        val type: NotificationType
    ) : GameEvent()

    data class AchievementUnlocked(val achievementId: String) : GameEvent()

    data class ScreenShakeRequested(
        val intensityX: Float,
        val intensityY: Float,
        val durationFrames: Int
    ) : GameEvent()

    // ─── أحداث الصوت ─────────────────────────────────────────────────────

    data class PlaySFX(val sfxId: String, val volume: Float = 1f) : GameEvent()

    data class RegionMusicChanged(val regionId: String) : GameEvent()

    object BossMusicStarted : GameEvent()
    object BossMusicEnded : GameEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// أنواع مساعدة
// ─────────────────────────────────────────────────────────────────────────────

enum class WorldEventType {
    MEMORY_STORM,
    REMNANT_UPRISING,
    NAME_AUCTION,
    CARAVAN_SPAWN,
    WANDERING_GEARWRIGHT,
    LOST_CHILD_ECHO
}

enum class NotificationType {
    INFO,
    WARNING,
    DANGER,
    SUCCESS,
    ACHIEVEMENT,
    QUEST,
    FM_WARNING
}

// ─────────────────────────────────────────────────────────────────────────────
// الـ EventBus نفسه
// ─────────────────────────────────────────────────────────────────────────────

/**
 * EventBus — Singleton يدير إرسال واستقبال الأحداث.
 *
 * يستخدم SharedFlow مع replay=0 (لا يحتفظ بالأحداث السابقة).
 * الـ extraBufferCapacity=64 لتجنب حجب الـ emitter.
 */
object EventBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<GameEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    /** Flow يُستمع إليه لاستقبال الأحداث */
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    /**
     * إرسال حدث — آمن من أي خيط.
     * يُفضَّل استخدامه من Main thread.
     */
    fun emit(event: GameEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    /**
     * إرسال حدث مع انتظار الاستلام (Suspend).
     * للاستخدام من coroutines.
     */
    suspend fun emitSuspend(event: GameEvent) {
        _events.emit(event)
    }

    /**
     * إرسال SFX بسهولة
     */
    fun playSFX(sfxId: String, volume: Float = 1f) {
        emit(GameEvent.PlaySFX(sfxId, volume))
    }

    /**
     * طلب اهتزاز الشاشة
     */
    fun screenShake(
        intensityX: Float = 5f,
        intensityY: Float = 5f,
        durationFrames: Int = 12
    ) {
        emit(GameEvent.ScreenShakeRequested(intensityX, intensityY, durationFrames))
    }

    /**
     * عرض إشعار
     */
    fun notify(
        message: String,
        type: NotificationType = NotificationType.INFO
    ) {
        emit(GameEvent.NotificationShown(message, type))
    }
}