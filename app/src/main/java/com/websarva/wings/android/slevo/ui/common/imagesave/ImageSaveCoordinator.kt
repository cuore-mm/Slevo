package com.websarva.wings.android.slevo.ui.common.imagesave

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.util.ImageCopyUtil
import com.websarva.wings.android.slevo.ui.util.distinctImageUrls

/**
 * 画像保存フローの判定と実行を共通化するコーディネータ。
 *
 * URL正規化、権限判定、保留URL管理、保存件数の集計、結果文言生成をまとめて扱う。
 */
class ImageSaveCoordinator {
    private var pendingImageSaveUrls: List<String> = emptyList()

    /**
     * 画像保存要求の前処理を行い、次のアクションを返す。
     */
    fun prepareSave(context: Context, urls: List<String>): ImageSavePreparation {
        val targetUrls = normalizeImageSaveUrls(urls)
        if (targetUrls.isEmpty()) {
            // Guard: 空URLのみの場合は保存処理を開始しない。
            return ImageSavePreparation.Ignore
        }
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val hasPermission = !needsPermission || ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            pendingImageSaveUrls = targetUrls
            return ImageSavePreparation.RequestPermission(permission)
        }
        return ImageSavePreparation.ReadyToSave(targetUrls)
    }

    /**
     * 権限許可後に再開する保存対象URLを取り出し、保持状態をクリアする。
     */
    fun consumePendingUrls(): List<String> {
        val urls = pendingImageSaveUrls
        pendingImageSaveUrls = emptyList()
        return urls
    }

    /**
     * 保存待ちURLの保持状態をクリアする。
     */
    fun clearPendingUrls() {
        pendingImageSaveUrls = emptyList()
    }

    /**
     * 画像URL一覧を順次保存し、成功/失敗件数を集計する。
     */
    suspend fun saveImageUrls(context: Context, urls: List<String>): ImageSaveSummary {
        if (urls.isEmpty()) {
            // Guard: 空リストは保存処理を実行しない。
            return ImageSaveSummary(successCount = 0, failureCount = 0)
        }
        var successCount = 0
        var failureCount = 0
        for (url in urls) {
            val result = ImageCopyUtil.saveImageToMediaStore(context, url)
            if (result.isSuccess) {
                successCount += 1
            } else {
                failureCount += 1
            }
        }
        return ImageSaveSummary(successCount = successCount, failureCount = failureCount)
    }

    /**
     * 保存結果の件数に応じた通知文言を返す。
     */
    fun buildResultMessage(
        context: Context,
        requestCount: Int,
        summary: ImageSaveSummary,
    ): String {
        return when {
            requestCount == 1 && summary.failureCount == 0 -> {
                context.getString(R.string.image_save_result_single_success)
            }

            requestCount == 1 && summary.successCount == 0 -> {
                context.getString(R.string.image_save_result_single_failed)
            }

            summary.failureCount == 0 -> {
                context.getString(
                    R.string.image_save_result_success,
                    summary.successCount,
                )
            }

            summary.successCount == 0 -> {
                context.getString(
                    R.string.image_save_result_all_failed,
                    summary.failureCount,
                )
            }

            else -> {
                context.getString(
                    R.string.image_save_result_partial,
                    summary.successCount,
                    summary.failureCount,
                )
            }
        }
    }

    /**
     * 保存開始時に表示する文言を返す。
     */
    fun buildInProgressMessage(context: Context): String {
        return context.getString(R.string.image_save_in_progress)
    }

    /**
     * 権限拒否時に表示する文言を返す。
     */
    fun buildPermissionDeniedMessage(context: Context): String {
        return context.getString(R.string.image_save_permission_denied)
    }

    /**
     * 保存対象の画像URL一覧を正規化する。
     *
     * 空URLを除外し、重複を除いた順序で返す。
     */
    private fun normalizeImageSaveUrls(urls: List<String>): List<String> {
        return distinctImageUrls(urls)
            .filter { it.isNotBlank() }
    }
}

/**
 * 画像保存要求の判定結果。
 */
sealed interface ImageSavePreparation {
    /**
     * 保存対象がないため処理不要。
     */
    data object Ignore : ImageSavePreparation

    /**
     * 権限要求が必要。
     */
    data class RequestPermission(
        val permission: String,
    ) : ImageSavePreparation

    /**
     * すぐに保存実行可能。
     */
    data class ReadyToSave(
        val urls: List<String>,
    ) : ImageSavePreparation
}

/**
 * 画像保存の成功/失敗件数を表す結果。
 */
data class ImageSaveSummary(
    val successCount: Int,
    val failureCount: Int,
)

/**
 * 画像保存フローで UI に通知するイベント。
 */
sealed interface ImageSaveUiEvent {
    /**
     * ランタイム権限要求を UI に依頼する。
     */
    data class RequestPermission(
        val permission: String,
    ) : ImageSaveUiEvent

    /**
     * トースト表示メッセージを UI に通知する。
     */
    data class ShowToast(
        val message: String,
    ) : ImageSaveUiEvent
}
