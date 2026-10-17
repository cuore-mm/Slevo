package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.util.PostNgMatcher
import com.websarva.wings.android.slevo.data.util.PostNgTarget
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationCandidate
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationDetector
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublishResult
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublisher
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.ReplyNotificationRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * スレッド取得後の共通処理を実行するオーケストレーター。
 *
 * スレッド画面とタブ画面から同じ入口を呼び出し、取得前stateを境界にした返信検出と
 * thread state更新を一つのパイプラインへ集約する。
 */
class ThreadRefreshUseCase @Inject constructor(
    private val datRepository: DatRepository,
    private val threadStateRepository: ThreadStateRepository,
    private val threadHistoryRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val ownPostReconciliationUseCase: OwnPostReconciliationUseCase,
    private val settingsRepository: SettingsRepository,
    private val ngRepository: NgRepository,
    private val replyNotificationRepository: ReplyNotificationRepository,
    private val replyNotificationPublisher: ReplyNotificationPublisher,
    private val logger: AppLogger,
) {
    /** 通信から通知投稿までの共通取得処理を実行する。 */
    suspend fun refresh(request: ThreadRefreshRequest): ThreadRefreshResult? {
        // --- Previous state and fetch ---
        val previousState = threadStateRepository.getThreadState(request.threadId)
        val threadData = datRepository.getThread(
            boardUrl = request.boardUrl,
            threadKey = request.threadKey,
            onProgress = request.onProgress,
        ) ?: return null
        val (posts, title) = threadData

        // --- Own-post reconciliation ---
        reconcileOwnPosts(request, posts)
        val ownPostNumbers = loadOwnPostNumbers(request.threadId)

        // --- Reply detection and persistence ---
        val notificationsEnabled = settingsRepository.getIsReplyNotificationEnabled()
        val detectionNgRules = if (notificationsEnabled) loadNgRules() else null
        if (notificationsEnabled) {
            if (detectionNgRules != null) {
                val ignoredReplyResNumbers = deriveIgnoredReplyNumbers(posts, request.boardId, detectionNgRules)
                val candidates = ReplyNotificationDetector.detect(
                    posts = posts,
                    previousResCount = previousState?.latestResCount,
                    ownPostNumbers = ownPostNumbers,
                    ignoredReplyResNumbers = ignoredReplyResNumbers,
                )
                persistCandidates(request, title, candidates)
            }
        }

        // --- Objective state update ---
        threadStateRepository.saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = request.threadId,
                boardId = request.boardId,
                boardUrl = request.boardUrl,
                boardName = request.boardName,
                title = title ?: request.threadTitle,
                latestResCount = posts.size,
            )
        )

        // --- Notification delivery ---
        val deliveryNgRules = if (notificationsEnabled) loadNgRules() else null
        processDetectedNotifications(request, notificationsEnabled, posts, deliveryNgRules)

        return ThreadRefreshResult(
            posts = posts,
            title = title,
            previousResCount = previousState?.latestResCount,
        )
    }

    /** 現在のNGルールを取得し、失敗時は通知処理だけを保留する。 */
    private suspend fun loadNgRules() = try {
        ngRepository.observeNgs().first()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        logger.e(message = "Failed to load NG rules for reply notifications", throwable = error)
        null
    }

    /** 取得結果からNG判定対象となる絶対レス番号を作る。 */
    private fun deriveIgnoredReplyNumbers(
        posts: List<ReplyInfo>,
        boardId: Long,
        ngRules: List<NgEntity>,
    ): Set<Int> {
        val compiledRules = PostNgMatcher.compile(ngRules)
        return PostNgMatcher.ignoredPostNumbers(
            posts = posts.map { post -> PostNgTarget(id = post.id, name = post.name, content = post.content) },
            boardId = boardId,
            rules = compiledRules,
        )
    }

    /** 取得済みレスを表示モデルへ変換し、対象スレッドのpending投稿だけ照合する。 */
    private suspend fun reconcileOwnPosts(request: ThreadRefreshRequest, posts: List<ReplyInfo>) {
        val history = threadHistoryRepository.getHistory(request.threadId) ?: return
        val scope = OwnPostThreadScope.from(request.boardUrl, request.threadKey) ?: return
        try {
            ownPostReconciliationUseCase.reconcile(
                scope = scope,
                posts = posts.map(ReplyInfo::toThreadPostUiModel),
                historyId = history.id,
                boardId = history.boardId,
                nowMillis = System.currentTimeMillis(),
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logger.e(
                message = "Failed to reconcile own posts after common thread refresh",
                throwable = error,
            )
        }
    }

    /** 自レス照合後の確定済み自レス番号を一度だけ再読み込みする。 */
    private suspend fun loadOwnPostNumbers(threadId: ThreadId): Set<Int> {
        val history = threadHistoryRepository.getHistory(threadId) ?: return emptySet()
        return try {
            postHistoryRepository.getMyPostNumbers(history.id)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logger.e(
                message = "Failed to load own post numbers for reply detection",
                throwable = error,
            )
            emptySet()
        }
    }

    /** 返信候補をEntityへ変換して一意登録する。 */
    private suspend fun persistCandidates(
        request: ThreadRefreshRequest,
        title: String?,
        candidates: List<ReplyNotificationCandidate>,
    ) {
        if (candidates.isEmpty()) return
        try {
            replyNotificationRepository.insertNew(
                candidates.map { candidate ->
                    ReplyNotificationEntity(
                        threadId = request.threadId,
                        replyResNo = candidate.replyResNo,
                        targetOwnResNumbers = candidate.targetOwnResNumbers.joinToString(","),
                        boardUrl = request.boardUrl,
                        threadKey = request.threadKey,
                        threadTitle = title ?: request.threadTitle,
                        messagePreview = candidate.messagePreview,
                        detectedAt = System.currentTimeMillis(),
                    )
                },
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logger.e(message = "Failed to persist reply notifications", throwable = error)
        }
    }

    /** 未配信通知を設定とPublisherの結果に応じて終端させる。 */
    private suspend fun processDetectedNotifications(
        request: ThreadRefreshRequest,
        enabled: Boolean,
        posts: List<ReplyInfo>,
        ngRules: List<NgEntity>?,
    ) {
        if (enabled && ngRules == null) return
        val threadId = request.threadId
        // --- Pending candidates ---
        val detected = try {
            replyNotificationRepository.findDetected(threadId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logger.e(message = "Failed to load detected reply notifications", throwable = error)
            return
        }

        val compiledNgRules = PostNgMatcher.compile(ngRules.orEmpty())
        // --- Current source resolution and NG recheck ---
        detected.forEach { notification ->
            if (!enabled) {
                updateNotificationStatus(notification, ReplyNotificationStatus.SUPPRESSED)
                return@forEach
            }
            val sourcePost = posts.getOrNull(notification.replyResNo - 1)
            val isNg = sourcePost == null || PostNgMatcher.isIgnored(
                post = PostNgTarget(
                    id = sourcePost.id,
                    name = sourcePost.name,
                    content = sourcePost.content,
                ),
                boardId = request.boardId,
                rules = compiledNgRules,
            )
            if (isNg) {
                updateNotificationStatus(notification, ReplyNotificationStatus.SUPPRESSED)
                return@forEach
            }
            // --- Publish and terminal status update ---
            val result = try {
                replyNotificationPublisher.publish(notification)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                logger.e(message = "Failed to publish reply notification", throwable = error)
                ReplyNotificationPublishResult.RETRY
            }
            when (result) {
                ReplyNotificationPublishResult.DELIVERED ->
                    updateNotificationStatus(notification, ReplyNotificationStatus.DELIVERED)

                ReplyNotificationPublishResult.SUPPRESSED ->
                    updateNotificationStatus(notification, ReplyNotificationStatus.SUPPRESSED)

                ReplyNotificationPublishResult.RETRY -> Unit
            }
        }
    }

    /** Publisher結果に対応するstatus遷移を条件付きで保存する。 */
    private suspend fun updateNotificationStatus(
        notification: ReplyNotificationEntity,
        nextStatus: ReplyNotificationStatus,
    ) {
        try {
            replyNotificationRepository.updateStatus(
                threadId = notification.threadId,
                replyResNo = notification.replyResNo,
                currentStatus = ReplyNotificationStatus.DETECTED,
                nextStatus = nextStatus,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logger.e(message = "Failed to update reply notification status", throwable = error)
        }
    }
}

/**
 * 共通スレッド取得へ渡す画面非依存の入力。
 *
 * board情報と既知タイトルを保持し、スレッド画面とタブ画面で同じ判定条件を作る。
 */
data class ThreadRefreshRequest(
    val threadId: ThreadId,
    val boardUrl: String,
    val boardId: Long,
    val boardName: String,
    val threadKey: String,
    val threadTitle: String,
    val onProgress: (Float) -> Unit = {},
)

/**
 * 共通取得が成功した結果。
 *
 * `previousResCount` は取得開始前の永続stateであり、UIの既読位置とは異なる新着境界を表す。
 */
data class ThreadRefreshResult(
    val posts: List<ReplyInfo>,
    val title: String?,
    val previousResCount: Int?,
)
