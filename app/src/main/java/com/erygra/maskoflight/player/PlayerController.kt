package com.erygra.maskoflight.player

import com.erygra.maskoflight.core.EventBus
import com.erygra.maskoflight.core.GameConfig
import com.erygra.maskoflight.core.GameEvent
import com.erygra.maskoflight.engine.PhysicsEngine
import com.erygra.maskoflight.engine.PhysicsEngine.DashDirection
import com.erygra.maskoflight.engine.PhysicsEngine.JumpType
import com.erygra.maskoflight.engine.PhysicsEngine.PhysicsBody
import com.erygra.maskoflight.engine.PhysicsEngine.PhysicsInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * player/PlayerController.kt
 * ═══════════════════════════════════════════════════════════════════════════════
 * نظام التحكم باللاعب — لعبة "قِنَاعُ النُّور" (Mask of Light)
 * Erygra Universe 2.0
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * الوظائف الرئيسية:
 * - معالجة المدخلات (Touch, D-Pad, Virtual Buttons, Gestures)
 * - تنفيذ الحركة الأساسية (Walk, Run, Jump, Fall)
 * - تنفيذ الحركة المتقدمة (Dash, Dodge Roll, Wall Jump, Climb, Swing)
 * - تفعيل القدرات (Memory Pulse, Echo Recall, Mask Shard Blast)
 * - التفاعل مع البيئة (Interact, Pickup, Talk)
 * - الربط مع PhysicsEngine لتطبيق الحركة
 * - الربط مع PlayerStateManager لتحديث الحالة
 * - إطلاق أحداث اللعب (EventBus)
 *
 * البنية:
 * - InputState: حالة المدخلات الحالية
 * - PlayerController: المتحكم الرئيسي
 * - Input Processing: معالجة المدخلات
 * - Movement Execution: تنفيذ الحركة
 * - Ability Execution: تنفيذ القدرات
 * - Interaction: التفاعل مع العالم
 *
 * @author Erygra Team
 * @version 2.0.0
 * @since 2025-01-09
 */

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: - Input State
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * حالة المدخلات الحالية
 *
 * @property moveX المحور الأفقي (-1.0 = يسار, 0.0 = ثابت, 1.0 = يمين)
 * @property moveY المحور العمودي (-1.0 = أسفل, 0.0 = ثابت, 1.0 = أعلى)
 * @property jumpPressed هل زر القفز مضغوط؟
 * @property jumpHeld هل زر القفز محتفظ به؟
 * @property dashPressed هل زر الاندفاع مضغوط؟
 * @property dodgePressed هل زر التفادي مضغوط؟
 * @property interactPressed هل زر التفاعل مضغوط؟
 * @property lightAttackPressed هل زر الهجوم الخفيف مضغوط؟
 * @property heavyAttackPressed هل زر الهجوم الثقيل مضغوط؟
 * @property parryPressed هل زر الصد مضغوط؟
 * @property memoryPulsePressed هل زر نبضة الذاكرة مضغوط؟
 * @property echoRecallPressed هل زر استدعاء الصدى مضغوط؟
 * @property maskShardPressed هل زر شظية القناع مضغوط؟
 * @property inventoryPressed هل زر المخزون مضغوط؟
 * @property mapPressed هل زر الخريطة مضغوط؟
 * @property pausePressed هل زر الإيقاف المؤقت مضغوط؟
 */
data class InputState(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val jumpPressed: Boolean = false,
    val jumpHeld: Boolean = false,
    val dashPressed: Boolean = false,
    val dodgePressed: Boolean = false,
    val interactPressed: Boolean = false,
    val lightAttackPressed: Boolean = false,
    val heavyAttackPressed: Boolean = false,
    val parryPressed: Boolean = false,
    val memoryPulsePressed: Boolean = false,
    val echoRecallPressed: Boolean = false,
    val maskShardPressed: Boolean = false,
    val inventoryPressed: Boolean = false,
    val mapPressed: Boolean = false,
    val pausePressed: Boolean = false
) {
    /**
     * هل هناك حركة أفقية؟
     */
    val hasHorizontalInput: Boolean
        get() = moveX != 0f

    /**
     * هل هناك حركة عمودية؟
     */
    val hasVerticalInput: Boolean
        get() = moveY != 0f

    /**
     * هل هناك أي حركة؟
     */
    val hasMovementInput: Boolean
        get() = hasHorizontalInput || hasVerticalInput

    /**
     * هل هناك أي إدخال قتالي؟
     */
    val hasCombatInput: Boolean
        get() = lightAttackPressed || heavyAttackPressed || parryPressed

    /**
     * هل هناك أي إدخال قدرة؟
     */
    val hasAbilityInput: Boolean
        get() = memoryPulsePressed || echoRecallPressed || maskShardPressed
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: - Player Action State
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * حالة الإجراء الحالية للاعب
 */
enum class PlayerActionState {
    IDLE,                // وقوف
    WALKING,             // مشي
    RUNNING,             // جري
    JUMPING,             // قفز
    FALLING,             // سقوط
    DASHING,             // اندفاع
    DODGING,             // تفادي
    WALL_SLIDING,        // انزلاق على جدار
    WALL_JUMPING,        // قفز جداري
    CLIMBING,            // تسلق
    SWINGING,            // تأرجح
    ATTACKING_LIGHT,     // هجوم خفيف
    ATTACKING_HEAVY,     // هجوم ثقيل
    PARRYING,            // صد
    USING_ABILITY,       // استخدام قدرة
    INTERACTING,         // تفاعل
    HURT,                // مصاب
    DEAD                 // ميت
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: - Gesture Detection
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * أنواع الإيماءات المدعومة
 */
enum class GestureType {
    NONE,
    SWIPE_UP,           // سحب لأعلى
    SWIPE_DOWN,         // سحب لأسفل
    SWIPE_LEFT,         // سحب لليسار
    SWIPE_RIGHT,        // سحب لليمين
    DOUBLE_TAP,         // نقر مزدوج
    LONG_PRESS,         // ضغط طويل
    PINCH_IN,           // قرص للداخل
    PINCH_OUT           // قرص للخارج
}

/**
 * معلومات إيماءة
 *
 * @property type نوع الإيماءة
 * @property startX نقطة البداية X
 * @property startY نقطة البداية Y
 * @property endX نقطة النهاية X
 * @property endY نقطة النهاية Y
 * @property velocity السرعة
 * @property timestamp وقت الحدوث
 */
data class GestureInfo(
    val type: GestureType = GestureType.NONE,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
    val velocity: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: - Player Controller
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * المتحكم الرئيسي باللاعب
 *
 * @property stateManager مدير حالة اللاعب
 */
class PlayerController(
    private val stateManager: PlayerStateManager
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // Properties
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * حالة المدخلات الحالية
     */
    private val _inputState = MutableStateFlow(InputState())
    val inputState: StateFlow<InputState> = _inputState.asStateFlow()

    /**
     * حالة الإجراء الحالية
     */
    private val _actionState = MutableStateFlow(PlayerActionState.IDLE)
    val actionState: StateFlow<PlayerActionState> = _actionState.asStateFlow()

    /**
     * الجسم الفيزيائي للاعب
     */
    private var physicsBody: PhysicsBody = PhysicsBody(
        x = stateManager.currentState.position.x,
        y = stateManager.currentState.position.y,
        width = GameConfig.PlayerConfig.PLAYER_WIDTH,
        height = GameConfig.PlayerConfig.PLAYER_HEIGHT,
        velocityX = 0f,
        velocityY = 0f,
        mass = GameConfig.PlayerConfig.PLAYER_MASS
    )

    /**
     * الإيماءة الأخيرة المكتشفة
     */
    private var lastGesture: GestureInfo = GestureInfo()

    /**
     * عداد Combo
     */
    private var comboCount: Int = 0
    private var lastAttackTime: Long = 0L

    /**
     * هل اللاعب في حالة هجوم؟
     */
    private var isAttacking: Boolean = false
    private var attackStartTime: Long = 0L

    /**
     * هل اللاعب في حالة صد؟
     */
    private var isParrying: Boolean = false
    private var parryStartTime: Long = 0L

    /**
     * هل اللاعب في حالة استخدام قدرة؟
     */
    private var isUsingAbility: Boolean = false
    private var abilityStartTime: Long = 0L

    /**
     * هل اللاعب في حالة تفاعل؟
     */
    private var isInteracting: Boolean = false

    /**
     * هل اللاعب في حالة إصابة؟
     */
    private var isHurt: Boolean = false
    private var hurtStartTime: Long = 0L

    /**
     * Coyote Time Counter (للقفز بعد السقوط)
     */
    private var coyoteTimeRemaining: Float = 0f

    /**
     * Jump Buffer Counter (حفظ ضغطة القفز)
     */
    private var jumpBufferRemaining: Float = 0f

    /**
     * Wall Jump Direction
     */
    private var wallJumpDirection: Float = 0f

    // ═══════════════════════════════════════════════════════════════════════════
    // Input Processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * تحديث حالة المدخلات
     *
     * @param newInput الحالة الجديدة
     */
    fun updateInput(newInput: InputState) {
        _inputState.value = newInput
    }

    /**
     * تحديث محور الحركة
     *
     * @param x المحور الأفقي (-1.0 إلى 1.0)
     * @param y المحور العمودي (-1.0 إلى 1.0)
     */
    fun updateMovement(x: Float, y: Float) {
        _inputState.value = _inputState.value.copy(
            moveX = x.coerceIn(-1f, 1f),
            moveY = y.coerceIn(-1f, 1f)
        )
    }

    /**
     * ضغط زر القفز
     */
    fun pressJump() {
        _inputState.value = _inputState.value.copy(
            jumpPressed = true,
            jumpHeld = true
        )
        jumpBufferRemaining = GameConfig.PlayerConfig.JUMP_BUFFER_TIME
    }

    /**
     * رفع زر القفز
     */
    fun releaseJump() {
        _inputState.value = _inputState.value.copy(
            jumpPressed = false,
            jumpHeld = false
        )
    }

    /**
     * ضغط زر الاندفاع
     */
    fun pressDash() {
        _inputState.value = _inputState.value.copy(dashPressed = true)
    }

    /**
     * رفع زر الاندفاع
     */
    fun releaseDash() {
        _inputState.value = _inputState.value.copy(dashPressed = false)
    }

    /**
     * ضغط زر التفادي
     */
    fun pressDodge() {
        _inputState.value = _inputState.value.copy(dodgePressed = true)
    }

    /**
     * رفع زر التفادي
     */
    fun releaseDodge() {
        _inputState.value = _inputState.value.copy(dodgePressed = false)
    }

    /**
     * ضغط زر التفاعل
     */
    fun pressInteract() {
        _inputState.value = _inputState.value.copy(interactPressed = true)
    }

    /**
     * رفع زر التفاعل
     */
    fun releaseInteract() {
        _inputState.value = _inputState.value.copy(interactPressed = false)
    }

    /**
     * ضغط زر الهجوم الخفيف
     */
    fun pressLightAttack() {
        _inputState.value = _inputState.value.copy(lightAttackPressed = true)
    }

    /**
     * رفع زر الهجوم الخفيف
     */
    fun releaseLightAttack() {
        _inputState.value = _inputState.value.copy(lightAttackPressed = false)
    }

    /**
     * ضغط زر الهجوم الثقيل
     */
    fun pressHeavyAttack() {
        _inputState.value = _inputState.value.copy(heavyAttackPressed = true)
    }

    /**
     * رفع زر الهجوم الثقيل
     */
    fun releaseHeavyAttack() {
        _inputState.value = _inputState.value.copy(heavyAttackPressed = false)
    }

    /**
     * ضغط زر الصد
     */
    fun pressParry() {
        _inputState.value = _inputState.value.copy(parryPressed = true)
    }

    /**
     * رفع زر الصد
     */
    fun releaseParry() {
        _inputState.value = _inputState.value.copy(parryPressed = false)
    }

    /**
     * ضغط زر نبضة الذاكرة
     */
    fun pressMemoryPulse() {
        _inputState.value = _inputState.value.copy(memoryPulsePressed = true)
    }

    /**
     * رفع زر نبضة الذاكرة
     */
    fun releaseMemoryPulse() {
        _inputState.value = _inputState.value.copy(memoryPulsePressed = false)
    }

    /**
     * ضغط زر استدعاء الصدى
     */
    fun pressEchoRecall() {
        _inputState.value = _inputState.value.copy(echoRecallPressed = true)
    }

    /**
     * رفع زر استدعاء الصدى
     */
    fun releaseEchoRecall() {
        _inputState.value = _inputState.value.copy(echoRecallPressed = false)
    }

    /**
     * ضغط زر شظية القناع
     */
    fun pressMaskShard() {
        _inputState.value = _inputState.value.copy(maskShardPressed = true)
    }

    /**
     * رفع زر شظية القناع
     */
    fun releaseMaskShard() {
        _inputState.value = _inputState.value.copy(maskShardPressed = false)
    }

    /**
     * ضغط زر المخزون
     */
    fun pressInventory() {
        _inputState.value = _inputState.value.copy(inventoryPressed = true)
    }

    /**
     * ضغط زر الخريطة
     */
    fun pressMap() {
        _inputState.value = _inputState.value.copy(mapPressed = true)
    }

    /**
     * ضغط زر الإيقاف المؤقت
     */
    fun pressPause() {
        _inputState.value = _inputState.value.copy(pausePressed = true)
    }

    /**
     * معالجة إيماءة
     *
     * @param gesture معلومات الإيماءة
     */
    fun processGesture(gesture: GestureInfo) {
        lastGesture = gesture

        when (gesture.type) {
            GestureType.SWIPE_UP -> {
                // القفز العالي
                if (canPerformAction(PlayerActionState.JUMPING)) {
                    pressJump()
                }
            }
            GestureType.SWIPE_DOWN -> {
                // السقوط السريع أو Ground Slam
                if (physicsBody.isAirborne && stateManager.currentState.abilities.isUnlocked(AbilityType.GROUND_SLAM)) {
                    executeGroundSlam()
                }
            }
            GestureType.SWIPE_LEFT, GestureType.SWIPE_RIGHT -> {
                // الاندفاع
                if (canPerformAction(PlayerActionState.DASHING)) {
                    val direction = if (gesture.type == GestureType.SWIPE_RIGHT) 1f else -1f
                    executeDash(direction)
                }
            }
            GestureType.DOUBLE_TAP -> {
                // التفادي
                if (canPerformAction(PlayerActionState.DODGING)) {
                    executeDodge()
                }
            }
            GestureType.LONG_PRESS -> {
                // الهجوم الثقيل المشحون
                if (canPerformAction(PlayerActionState.ATTACKING_HEAVY)) {
                    pressHeavyAttack()
                }
            }
            else -> {
                // لا شيء
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Update Loop
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * التحديث الرئيسي للمتحكم
     *
     * @param deltaTime الوقت المنقضي منذ آخر إطار (ثواني)
     */
    fun update(deltaTime: Float) {
        // تحديث الحالة من PlayerStateManager
        updatePhysicsBodyFromState()

        // تحديث Timers
        updateTimers(deltaTime)

        // معالجة المدخلات وتنفيذ الإجراءات
        processInput(deltaTime)

        // تحديث الجسم الفيزيائي
        updatePhysics(deltaTime)

        // تحديث حالة اللاعب
        updatePlayerState()

        // تحديث حالة الإجراء
        updateActionState()

        // مسح المدخلات المؤقتة (Pressed)
        clearTemporaryInputs()
    }

    /**
     * تحديث الجسم الفيزيائي من حالة اللاعب
     */
    private fun updatePhysicsBodyFromState() {
        val state = stateManager.currentState
        physicsBody = physicsBody.copy(
            x = state.position.x,
            y = state.position.y,
            velocityX = state.position.velocityX,
            velocityY = state.position.velocityY,
            isGrounded = state.position.isGrounded,
            isOnWall = state.position.isOnWall
        )
    }

    /**
     * تحديث العدادات الزمنية
     */
    private fun updateTimers(deltaTime: Float) {
        // Coyote Time
        if (coyoteTimeRemaining > 0f) {
            coyoteTimeRemaining -= deltaTime
        }

        // Jump Buffer
        if (jumpBufferRemaining > 0f) {
            jumpBufferRemaining -= deltaTime
        }

        // Attack Timer
        if (isAttacking) {
            val attackDuration = if (_actionState.value == PlayerActionState.ATTACKING_LIGHT) {
                GameConfig.CombatConfig.LIGHT_ATTACK_DURATION
            } else {
                GameConfig.CombatConfig.HEAVY_ATTACK_DURATION
            }
            if (System.currentTimeMillis() - attackStartTime >= attackDuration) {
                isAttacking = false
            }
        }

        // Parry Timer
        if (isParrying) {
            if (System.currentTimeMillis() - parryStartTime >= GameConfig.CombatConfig.PARRY_WINDOW) {
                isParrying = false
            }
        }

        // Ability Timer
        if (isUsingAbility) {
            if (System.currentTimeMillis() - abilityStartTime >= GameConfig.PlayerConfig.ABILITY_USE_DURATION) {
                isUsingAbility = false
            }
        }

        // Hurt Timer
        if (isHurt) {
            if (System.currentTimeMillis() - hurtStartTime >= GameConfig.PlayerConfig.HURT_DURATION) {
                isHurt = false
            }
        }

        // Combo Timer
        if (System.currentTimeMillis() - lastAttackTime > GameConfig.CombatConfig.COMBO_RESET_TIME) {
            comboCount = 0
        }
    }

    /**
     * معالجة المدخلات
     */
    private fun processInput(deltaTime: Float) {
        val input = _inputState.value
        val state = stateManager.currentState

        // التحقق من الموت
        if (!state.stats.isAlive) {
            _actionState.value = PlayerActionState.DEAD
            return
        }

        // التحقق من الإصابة
        if (isHurt) {
            _actionState.value = PlayerActionState.HURT
            return
        }

        // التحقق من Stun
        if (state.effects.isStunned) {
            return
        }

        // معالجة الإيقاف المؤقت
        if (input.pausePressed) {
            EventBus.emit(GameEvent.UI.PauseRequested)
            return
        }

        // معالجة UI
        if (input.inventoryPressed) {
            EventBus.emit(GameEvent.UI.InventoryOpened)
            return
        }

        if (input.mapPressed) {
            EventBus.emit(GameEvent.UI.MapOpened)
            return
        }

        // معالجة التفاعل
        if (input.interactPressed && canPerformAction(PlayerActionState.INTERACTING)) {
            executeInteraction()
            return
        }

        // معالجة القدرات (أولوية عالية)
        if (state.effects.isSilenced) {
            // لا يمكن استخدام القدرات إذا كان صامتاً
        } else {
            if (input.memoryPulsePressed && canUseAbility(AbilityType.MEMORY_PULSE_SMALL)) {
                executeMemoryPulse()
                return
            }

            if (input.echoRecallPressed && canUseAbility(AbilityType.ECHO_RECALL)) {
                executeEchoRecall()
                return
            }

            if (input.maskShardPressed && canUseAbility(AbilityType.MASK_SHARD_BLAST)) {
                executeMaskShard()
                return
            }
        }

        // معالجة القتال
        if (input.parryPressed && canPerformAction(PlayerActionState.PARRYING)) {
            executeParry()
            return
        }

        if (input.lightAttackPressed && canPerformAction(PlayerActionState.ATTACKING_LIGHT)) {
            executeLightAttack()
            return
        }

        if (input.heavyAttackPressed && canPerformAction(PlayerActionState.ATTACKING_HEAVY)) {
            executeHeavyAttack()
            return
        }

        // معالجة الحركة المتقدمة
        if (input.dodgePressed && canPerformAction(PlayerActionState.DODGING)) {
            executeDodge()
            return
        }

        if (input.dashPressed && canPerformAction(PlayerActionState.DASHING)) {
            val direction = if (input.moveX != 0f) input.moveX else if (physicsBody.facingRight) 1f else -1f
            executeDash(direction)
            return
        }

        // معالجة القفز
        if (input.jumpPressed || jumpBufferRemaining > 0f) {
            if (canPerformAction(PlayerActionState.JUMPING)) {
                executeJump()
            } else if (canPerformAction(PlayerActionState.WALL_JUMPING)) {
                executeWallJump()
            }
        }

        // معالجة الحركة الأساسية
        if (input.hasHorizontalInput) {
            executeMovement(input.moveX)
        }

        // معالجة التسلق
        if (input.hasVerticalInput && physicsBody.isOnWall && state.abilities.isUnlocked(AbilityType.LEDGE_GRAB)) {
            executeClimb(input.moveY)
        }
    }

    /**
     * تحديث الفيزياء
     */
    private fun updatePhysics(deltaTime: Float) {
        val input = _inputState.value

        // إنشاء PhysicsInput
        val physicsInput = PhysicsInput(
            moveX = input.moveX,
            moveY = input.moveY,
            jump = input.jumpPressed,
            jumpHeld = input.jumpHeld,
            dash = input.dashPressed,
            dodge = input.dodgePressed
        )

        // تحديث الفيزياء
        physicsBody = PhysicsEngine.update(physicsBody, physicsInput, deltaTime)

        // تحديث Coyote Time
        if (physicsBody.wasGrounded && !physicsBody.isGrounded) {
            coyoteTimeRemaining = GameConfig.PlayerConfig.COYOTE_TIME
        }
    }

    /**
     * تحديث حالة اللاعب
     */
    private fun updatePlayerState() {
        stateManager.updatePosition(
            x = physicsBody.x,
            y = physicsBody.y,
            velocityX = physicsBody.velocityX,
            velocityY = physicsBody.velocityY,
            facingRight = physicsBody.facingRight
        )

        stateManager.setGrounded(physicsBody.isGrounded)
        stateManager.setOnWall(physicsBody.isOnWall)
        stateManager.setClimbing(_actionState.value == PlayerActionState.CLIMBING)
        stateManager.setSwinging(_actionState.value == PlayerActionState.SWINGING)
    }

    /**
     * تحديث حالة الإجراء
     */
    private fun updateActionState() {
        if (!stateManager.currentState.stats.isAlive) {
            _actionState.value = PlayerActionState.DEAD
            return
        }

        if (isHurt) {
            _actionState.value = PlayerActionState.HURT
            return
        }

        if (isUsingAbility) {
            _actionState.value = PlayerActionState.USING_ABILITY
            return
        }

        if (isParrying) {
            _actionState.value = PlayerActionState.PARRYING
            return
        }

        if (isAttacking) {
            _actionState.value = if (_inputState.value.lightAttackPressed) {
                PlayerActionState.ATTACKING_LIGHT
            } else {
                PlayerActionState.ATTACKING_HEAVY
            }
            return
        }

        if (isInteracting) {
            _actionState.value = PlayerActionState.INTERACTING
            return
        }

        if (physicsBody.isDashing) {
            _actionState.value = PlayerActionState.DASHING
            return
        }

        if (physicsBody.isDodging) {
            _actionState.value = PlayerActionState.DODGING
            return
        }

        if (physicsBody.isOnWall && !physicsBody.isGrounded) {
            _actionState.value = PlayerActionState.WALL_SLIDING
            return
        }

        if (_actionState.value == PlayerActionState.CLIMBING && _inputState.value.moveY != 0f) {
            return // لا تزال تتسلق
        }

        if (_actionState.value == PlayerActionState.SWINGING) {
            return // لا تزال تتأرجح
        }

        if (!physicsBody.isGrounded && physicsBody.velocityY > 0f) {
            _actionState.value = PlayerActionState.JUMPING
            return
        }

        if (!physicsBody.isGrounded && physicsBody.velocityY < 0f) {
            _actionState.value = PlayerActionState.FALLING
            return
        }

        if (_inputState.value.hasHorizontalInput && physicsBody.isGrounded) {
            val speed = abs(physicsBody.velocityX)
            _actionState.value = if (speed > GameConfig.PlayerConfig.RUN_THRESHOLD) {
                PlayerActionState.RUNNING
            } else {
                PlayerActionState.WALKING
            }
            return
        }

        _actionState.value = PlayerActionState.IDLE
    }

    /**
     * مسح المدخلات المؤقتة
     */
    private fun clearTemporaryInputs() {
        _inputState.value = _inputState.value.copy(
            jumpPressed = false,
            dashPressed = false,
            dodgePressed = false,
            interactPressed = false,
            lightAttackPressed = false,
            heavyAttackPressed = false,
            parryPressed = false,
            memoryPulsePressed = false,
            echoRecallPressed = false,
            maskShardPressed = false,
            inventoryPressed = false,
            mapPressed = false,
            pausePressed = false
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution - Movement
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * تنفيذ الحركة الأفقية
     */
    private fun executeMovement(direction: Float) {
        // تطبيق السرعة من المؤثرات
        val speedMultiplier = 1f + stateManager.currentState.effects.getEffectStrength(EffectType.SPEED_BOOST)
        val slowMultiplier = 1f - stateManager.currentState.effects.getEffectStrength(EffectType.SLOWED)
        val finalMultiplier = speedMultiplier * slowMultiplier

        // تحديث الاتجاه
        if (direction != 0f) {
            physicsBody = physicsBody.copy(facingRight = direction > 0f)
        }
    }

    /**
     * تنفيذ القفز
     */
    private fun executeJump() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.LEDGE_GRAB)) {
            // القفز الأساسي (متاح دائماً)
        }

        val jumpType = if (_inputState.value.jumpHeld) {
            JumpType.FULL
        } else {
            JumpType.SHORT
        }

        physicsBody = PhysicsEngine.processJump(physicsBody, jumpType)
        jumpBufferRemaining = 0f

        EventBus.emit(GameEvent.Player.Jumped(physicsBody.x, physicsBody.y))
    }

    /**
     * تنفيذ القفز الجداري
     */
    private fun executeWallJump() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.WALL_JUMP)) return

        // تحديد اتجاه القفز (عكس الجدار)
        wallJumpDirection = if (physicsBody.facingRight) -1f else 1f

        physicsBody = PhysicsEngine.processJump(physicsBody, JumpType.WALL)
        jumpBufferRemaining = 0f

        EventBus.emit(GameEvent.Player.WallJumped(physicsBody.x, physicsBody.y))
    }

    /**
     * تنفيذ الاندفاع
     */
    private fun executeDash(direction: Float) {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.DASH)) return
        if (!stateManager.currentState.abilities.isReady(AbilityType.DASH)) return
        if (!stateManager.consumeEnergy(GameConfig.PlayerConfig.DASH_ENERGY_COST)) return

        val dashDirection = when {
            direction > 0f -> DashDirection.RIGHT
            direction < 0f -> DashDirection.LEFT
            else -> if (physicsBody.facingRight) DashDirection.RIGHT else DashDirection.LEFT
        }

        physicsBody = PhysicsEngine.startDash(physicsBody, dashDirection)
        stateManager.useAbility(AbilityType.DASH)

        EventBus.emit(GameEvent.Player.Dashed(physicsBody.x, physicsBody.y, dashDirection.name))
    }

    /**
     * تنفيذ التفادي
     */
    private fun executeDodge() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.DODGE_ROLL)) return
        if (!stateManager.currentState.abilities.isReady(AbilityType.DODGE_ROLL)) return
        if (!stateManager.consumeEnergy(GameConfig.PlayerConfig.DODGE_ENERGY_COST)) return

        physicsBody = PhysicsEngine.startDodge(physicsBody)
        stateManager.useAbility(AbilityType.DODGE_ROLL)

        // إضافة تأثير مناعة مؤقتة
        val invulnerableEffect = Effect(
            type = EffectType.INVULNERABLE,
            strength = 1f,
            duration = GameConfig.PlayerConfig.DODGE_INVULNERABILITY_DURATION,
            remaining = GameConfig.PlayerConfig.DODGE_INVULNERABILITY_DURATION,
            source = "Dodge Roll"
        )
        stateManager.addEffect(invulnerableEffect)

        EventBus.emit(GameEvent.Player.Dodged(physicsBody.x, physicsBody.y))
    }

    /**
     * تنفيذ التسلق
     */
    private fun executeClimb(direction: Float) {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.LEDGE_GRAB)) return

        physicsBody = PhysicsEngine.applyClimbing(physicsBody, direction)
        _actionState.value = PlayerActionState.CLIMBING

        EventBus.emit(GameEvent.Player.Climbing(physicsBody.x, physicsBody.y))
    }

    /**
     * تنفيذ Ground Slam
     */
    private fun executeGroundSlam() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.GROUND_SLAM)) return
        if (!stateManager.currentState.abilities.isReady(AbilityType.GROUND_SLAM)) return
        if (!stateManager.consumeEnergy(GameConfig.PlayerConfig.GROUND_SLAM_ENERGY_COST)) return

        physicsBody = physicsBody.copy(
            velocityY = -GameConfig.PlayerConfig.GROUND_SLAM_VELOCITY
        )
        stateManager.useAbility(AbilityType.GROUND_SLAM)

        EventBus.emit(GameEvent.Player.GroundSlammed(physicsBody.x, physicsBody.y))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution - Combat
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * تنفيذ الهجوم الخفيف
     */
    private fun executeLightAttack() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.PRECISION_STRIKE)) return
        if (!stateManager.consumeEnergy(GameConfig.CombatConfig.LIGHT_ATTACK_ENERGY_COST)) return

        isAttacking = true
        attackStartTime = System.currentTimeMillis()

        // تحديث Combo
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAttackTime <= GameConfig.CombatConfig.COMBO_RESET_TIME) {
            comboCount = (comboCount + 1).coerceAtMost(GameConfig.CombatConfig.MAX_COMBO)
        } else {
            comboCount = 1
        }
        lastAttackTime = currentTime

        EventBus.emit(GameEvent.Combat.LightAttackStarted(
            x = physicsBody.x,
            y = physicsBody.y,
            facingRight = physicsBody.facingRight,
            comboCount = comboCount
        ))
    }

    /**
     * تنفيذ الهجوم الثقيل
     */
    private fun executeHeavyAttack() {
        if (!stateManager.consumeEnergy(GameConfig.CombatConfig.HEAVY_ATTACK_ENERGY_COST)) return

        isAttacking = true
        attackStartTime = System.currentTimeMillis()

        // إعادة تعيين Combo
        comboCount = 0

        EventBus.emit(GameEvent.Combat.HeavyAttackStarted(
            x = physicsBody.x,
            y = physicsBody.y,
            facingRight = physicsBody.facingRight
        ))
    }

    /**
     * تنفيذ الصد
     */
    private fun executeParry() {
        if (!stateManager.currentState.abilities.isUnlocked(AbilityType.PARRY_COUNTER)) return
        if (!stateManager.consumeEnergy(GameConfig.CombatConfig.PARRY_ENERGY_COST)) return

        isParrying = true
        parryStartTime = System.currentTimeMillis()

        EventBus.emit(GameEvent.Combat.ParryStarted(
            x = physicsBody.x,
            y = physicsBody.y
        ))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution - Abilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * تنفيذ نبضة الذاكرة
     */
    private fun executeMemoryPulse() {
        val abilityType = AbilityType.MEMORY_PULSE_SMALL
        if (!canUseAbility(abilityType)) return

        val mfCost = GameConfig.MemoryConfig.MEMORY_PULSE_SMALL_MF_COST
        val fmGain = GameConfig.MemoryConfig.MEMORY_PULSE_SMALL_FM_GAIN

        if (!stateManager.consumeMF(mfCost)) return

        stateManager.gainFM(fmGain)
        stateManager.useAbility(abilityType)

        isUsingAbility = true
        abilityStartTime = System.currentTimeMillis()

        EventBus.emit(GameEvent.Player.MemoryPulseUsed(
            x = physicsBody.x,
            y = physicsBody.y,
            radius = GameConfig.MemoryConfig.MEMORY_PULSE_RADIUS
        ))
    }

    /**
     * تنفيذ استدعاء الصدى
     */
    private fun executeEchoRecall() {
        val abilityType = AbilityType.ECHO_RECALL
        if (!canUseAbility(abilityType)) return

        val mfCost = GameConfig.MemoryConfig.ECHO_RECALL_MF_COST
        val fmGain = GameConfig.MemoryConfig.ECHO_RECALL_FM_GAIN

        if (!stateManager.consumeMF(mfCost)) return

        stateManager.gainFM(fmGain)
        stateManager.useAbility(abilityType)

        isUsingAbility = true
        abilityStartTime = System.currentTimeMillis()

        EventBus.emit(GameEvent.Player.EchoRecallUsed(
            x = physicsBody.x,
            y = physicsBody.y
        ))
    }

    /**
     * تنفيذ انفجار شظية القناع
     */
    private fun executeMaskShard() {
        val abilityType = AbilityType.MASK_SHARD_BLAST
        if (!canUseAbility(abilityType)) return

        val mfCost = GameConfig.MemoryConfig.MASK_SHARD_BLAST_MF_COST
        val fmGain = GameConfig.MemoryConfig.MASK_SHARD_BLAST_FM_GAIN

        if (!stateManager.consumeMF(mfCost)) return

        stateManager.gainFM(fmGain)
        stateManager.useAbility(abilityType)

        isUsingAbility = true
        abilityStartTime = System.currentTimeMillis()

        EventBus.emit(GameEvent.Player.MaskShardBlasted(
            x = physicsBody.x,
            y = physicsBody.y,
            facingRight = physicsBody.facingRight
        ))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution - Interaction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * تنفيذ التفاعل
     */
    private fun executeInteraction() {
        isInteracting = true

        EventBus.emit(GameEvent.Player.InteractionAttempted(
            x = physicsBody.x,
            y = physicsBody.y,
            facingRight = physicsBody.facingRight
        ))

        // سيتم إيقاف isInteracting بواسطة نظام آخر بعد التفاعل
    }

    /**
     * إنهاء التفاعل (يتم استدعاؤه من نظام خارجي)
     */
    fun endInteraction() {
        isInteracting = false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Functions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * هل يمكن تنفيذ إجراء معين؟
     *
     * @param action الإجراء المراد التحقق منه
     * @return true إذا كان الإجراء ممكناً
     */
    private fun canPerformAction(action: PlayerActionState): Boolean {
        val state = stateManager.currentState

        // التحقق من الموت
        if (!state.stats.isAlive) return false

        // التحقق من الإصابة
        if (isHurt) return false

        // التحقق من Stun
        if (state.effects.isStunned) return false

        // التحقق من إجراءات حصرية
        if (isAttacking && action !in listOf(PlayerActionState.ATTACKING_LIGHT, PlayerActionState.ATTACKING_HEAVY)) {
            return false
        }

        if (isParrying && action != PlayerActionState.PARRYING) {
            return false
        }

        if (isUsingAbility && action != PlayerActionState.USING_ABILITY) {
            return false
        }

        if (isInteracting && action != PlayerActionState.INTERACTING) {
            return false
        }

        // التحقق حسب نوع الإجراء
        return when (action) {
            PlayerActionState.JUMPING -> {
                physicsBody.isGrounded || coyoteTimeRemaining > 0f
            }
            PlayerActionState.WALL_JUMPING -> {
                physicsBody.isOnWall && !physicsBody.isGrounded && 
                state.abilities.isUnlocked(AbilityType.WALL_JUMP)
            }
            PlayerActionState.DASHING -> {
                state.abilities.isUnlocked(AbilityType.DASH) &&
                state.abilities.isReady(AbilityType.DASH) &&
                state.stats.hasEnergy(GameConfig.PlayerConfig.DASH_ENERGY_COST)
            }
            PlayerActionState.DODGING -> {
                state.abilities.isUnlocked(AbilityType.DODGE_ROLL) &&
                state.abilities.isReady(AbilityType.DODGE_ROLL) &&
                state.stats.hasEnergy(GameConfig.PlayerConfig.DODGE_ENERGY_COST)
            }
            PlayerActionState.ATTACKING_LIGHT -> {
                state.abilities.isUnlocked(AbilityType.PRECISION_STRIKE) &&
                state.stats.hasEnergy(GameConfig.CombatConfig.LIGHT_ATTACK_ENERGY_COST)
            }
            PlayerActionState.ATTACKING_HEAVY -> {
                state.stats.hasEnergy(GameConfig.CombatConfig.HEAVY_ATTACK_ENERGY_COST)
            }
            PlayerActionState.PARRYING -> {
                state.abilities.isUnlocked(AbilityType.PARRY_COUNTER) &&
                state.stats.hasEnergy(GameConfig.CombatConfig.PARRY_ENERGY_COST)
            }
            PlayerActionState.INTERACTING -> {
                true // دائماً ممكن
            }
            else -> true
        }
    }

    /**
     * هل يمكن استخدام قدرة معينة؟
     *
     * @param abilityType نوع القدرة
     * @return true إذا كان الاستخدام ممكناً
     */
    private fun canUseAbility(abilityType: AbilityType): Boolean {
        val state = stateManager.currentState

        // التحقق من الصمت (Silence)
        if (state.effects.isSilenced) return false

        // التحقق من فتح القدرة
        if (!state.abilities.isUnlocked(abilityType)) return false

        // التحقق من جاهزية القدرة
        if (!state.abilities.isReady(abilityType)) return false

        // التحقق من المتطلبات حسب نوع القدرة
        return when (abilityType) {
            AbilityType.MEMORY_PULSE_SMALL -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.MEMORY_PULSE_SMALL_MF_COST)
            }
            AbilityType.MEMORY_PULSE_LARGE -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.MEMORY_PULSE_LARGE_MF_COST)
            }
            AbilityType.ECHO_RECALL -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.ECHO_RECALL_MF_COST)
            }
            AbilityType.MASK_SHARD_BLAST -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.MASK_SHARD_BLAST_MF_COST)
            }
            AbilityType.BORROWED_NAMES -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.BORROWED_NAMES_MF_COST)
            }
            AbilityType.MEMORY_RESTORATION -> {
                state.stats.hasMemoryFragments(GameConfig.MemoryConfig.MEMORY_RESTORATION_MF_COST)
            }
            AbilityType.GROUND_SLAM -> {
                physicsBody.isAirborne &&
                state.stats.hasEnergy(GameConfig.PlayerConfig.GROUND_SLAM_ENERGY_COST)
            }
            else -> true
        }
    }

    /**
     * تطبيق ضرر على اللاعب
     *
     * @param damage مقدار الضرر
     * @param source مصدر الضرر
     * @param knockbackX قوة الارتداد الأفقية
     * @param knockbackY قوة الارتداد العمودية
     */
    fun takeDamage(damage: Int, source: String = "Unknown", knockbackX: Float = 0f, knockbackY: Float = 0f) {
        val actualDamage = stateManager.takeDamage(damage, source)

        if (actualDamage > 0) {
            // تطبيق الارتداد
            if (knockbackX != 0f || knockbackY != 0f) {
                physicsBody = PhysicsEngine.applyKnockback(physicsBody, knockbackX, knockbackY)
            }

            // تفعيل حالة الإصابة
            isHurt = true
            hurtStartTime = System.currentTimeMillis()

            EventBus.emit(GameEvent.Player.Damaged(
                damage = actualDamage,
                source = source,
                currentHp = stateManager.currentState.stats.hp,
                maxHp = stateManager.currentState.stats.maxHp
            ))

            // التحقق من الموت
            if (!stateManager.currentState.stats.isAlive) {
                onDeath()
            }
        }
    }

    /**
     * معالجة الموت
     */
    private fun onDeath() {
        _actionState.value = PlayerActionState.DEAD
        stateManager.registerDeath()

        EventBus.emit(GameEvent.Player.Died(
            x = physicsBody.x,
            y = physicsBody.y,
            deaths = stateManager.currentState.stats.deaths
        ))
    }

    /**
     * إحياء اللاعب (من Sanctuary أو Respawn)
     *
     * @param spawnX موقع الإحياء X
     * @param spawnY موقع الإحياء Y
     * @param fullRestore هل نستعيد كامل HP/Energy؟
     */
    fun respawn(spawnX: Float, spawnY: Float, fullRestore: Boolean = true) {
        // استعادة الصحة والطاقة
        if (fullRestore) {
            stateManager.heal(stateManager.currentState.stats.maxHp)
            stateManager.restoreEnergy(stateManager.currentState.stats.maxEnergy)
        } else {
            stateManager.heal(stateManager.currentState.stats.maxHp / 2)
            stateManager.restoreEnergy(stateManager.currentState.stats.maxEnergy / 2)
        }

        // إعادة تعيين الموقع
        physicsBody = physicsBody.copy(
            x = spawnX,
            y = spawnY,
            velocityX = 0f,
            velocityY = 0f,
            isGrounded = false
        )

        // إعادة تعيين الحالات
        isHurt = false
        isAttacking = false
        isParrying = false
        isUsingAbility = false
        isInteracting = false
        _actionState.value = PlayerActionState.IDLE

        // مسح التأثيرات السلبية
        stateManager.clearEffects(negativeOnly = true)

        EventBus.emit(GameEvent.Player.Respawned(spawnX, spawnY))
    }

    /**
     * الحصول على الجسم الفيزيائي الحالي
     */
    fun getPhysicsBody(): PhysicsBody = physicsBody

    /**
     * الحصول على حالة الإجراء الحالية
     */
    fun getActionState(): PlayerActionState = _actionState.value

    /**
     * الحصول على عدد Combo الحالي
     */
    fun getComboCount(): Int = comboCount

    /**
     * هل اللاعب يهاجم حالياً؟
     */
    fun isAttacking(): Boolean = isAttacking

    /**
     * هل اللاعب يصد حالياً؟
     */
    fun isParrying(): Boolean = isParrying

    /**
     * إعادة تعيين المتحكم
     */
    fun reset() {
        _inputState.value = InputState()
        _actionState.value = PlayerActionState.IDLE
        physicsBody = PhysicsBody(
            x = stateManager.currentState.position.x,
            y = stateManager.currentState.position.y,
            width = GameConfig.PlayerConfig.PLAYER_WIDTH,
            height = GameConfig.PlayerConfig.PLAYER_HEIGHT
        )
        isAttacking = false
        isParrying = false
        isUsingAbility = false
        isInteracting = false
        isHurt = false
        comboCount = 0
        coyoteTimeRemaining = 0f
        jumpBufferRemaining = 0f
    }
}