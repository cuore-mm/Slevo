package com.websarva.wings.android.slevo.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import com.websarva.wings.android.slevo.ui.util.CustomTabsUtil
import com.websarva.wings.android.slevo.ui.util.ImageCopyUtil
import com.websarva.wings.android.slevo.ui.util.buildLensSearchUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 画像メニューアクション実行に必要な依存をまとめた値オブジェクト。
 *
 * 画面ごとの差分はコールバックで受け取り、共通ランナーから実行する。
 */
data class ImageMenuActionRunnerParams(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val currentImageUrl: String,
    val imageUrls: List<String>,
    val onOpenNgDialog: (String) -> Unit,
    val onRequestSaveSingle: (String) -> Unit,
    val onRequestSaveAll: (List<String>) -> Unit,
    val onActionHandled: () -> Unit,
    val onSetClipboardText: suspend (String) -> Unit,
    val onSetClipboardImageUri: suspend (Uri) -> Unit,
)

/**
 * スレッド画面と画像ビューアで共通利用する画像メニューアクション実行器。
 *
 * 各アクションの成功/失敗時処理を一元化し、画面固有の責務はコールバックへ委譲する。
 */
object ImageMenuActionRunner {
    /**
     * 画像メニューで選択されたアクションを共通契約で実行する。
     */
    fun run(
        action: ImageMenuAction,
        params: ImageMenuActionRunnerParams,
    ) {
        // --- Dispatch ---
        when (action) {
            ImageMenuAction.ADD_NG -> handleAddNg(params)
            ImageMenuAction.COPY_IMAGE_URL -> handleCopyImageUrl(params)
            ImageMenuAction.COPY_IMAGE -> handleCopyImage(params)
            ImageMenuAction.OPEN_IN_OTHER_APP -> handleOpenInOtherApp(params)
            ImageMenuAction.SAVE_IMAGE -> handleSaveImage(params)
            ImageMenuAction.SAVE_ALL_IMAGES -> handleSaveAllImages(params)
            ImageMenuAction.SEARCH_WEB -> handleSearchWeb(params)
            ImageMenuAction.SHARE_IMAGE -> handleShareImage(params)
        }
        params.onActionHandled()
    }

    /**
     * NG登録アクションを実行する。
     */
    private fun handleAddNg(params: ImageMenuActionRunnerParams) {
        val targetUrl = params.currentImageUrl
        if (targetUrl.isBlank()) {
            // Guard: 空URLはNG登録対象にしない。
            return
        }
        params.onOpenNgDialog(targetUrl)
    }

    /**
     * 画像URLコピーアクションを実行する。
     */
    private fun handleCopyImageUrl(params: ImageMenuActionRunnerParams) {
        val targetUrl = params.currentImageUrl
        if (targetUrl.isBlank()) {
            // Guard: 空URLはコピー対象にしない。
            return
        }
        params.coroutineScope.launch {
            params.onSetClipboardText(targetUrl)
        }
    }

    /**
     * 画像コピーアクションを実行する。
     */
    private fun handleCopyImage(params: ImageMenuActionRunnerParams) {
        fetchImageUriAndRun(
            params = params,
            onFetchFailedMessageRes = R.string.image_copy_failed,
            onUriReady = { uri ->
                params.onSetClipboardImageUri(uri)
            },
        )
    }

    /**
     * 他アプリ起動アクションを実行する。
     */
    private fun handleOpenInOtherApp(params: ImageMenuActionRunnerParams) {
        fetchImageUriAndRun(
            params = params,
            onFetchFailedMessageRes = R.string.image_open_failed,
            onUriReady = { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (intent.resolveActivity(params.context.packageManager) != null) {
                    params.context.startActivity(Intent.createChooser(intent, null))
                } else {
                    Toast.makeText(
                        params.context,
                        R.string.no_app_to_open_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    /**
     * 単体保存アクションを実行する。
     */
    private fun handleSaveImage(params: ImageMenuActionRunnerParams) {
        val targetUrl = params.currentImageUrl
        if (targetUrl.isBlank()) {
            // Guard: 空URLは保存対象にしない。
            return
        }
        params.onRequestSaveSingle(targetUrl)
    }

    /**
     * 一括保存アクションを実行する。
     */
    private fun handleSaveAllImages(params: ImageMenuActionRunnerParams) {
        params.onRequestSaveAll(params.imageUrls)
    }

    /**
     * Web検索アクションを実行する。
     */
    private fun handleSearchWeb(params: ImageMenuActionRunnerParams) {
        val targetUrl = params.currentImageUrl
        if (targetUrl.isBlank()) {
            // Guard: 空URLは検索対象にしない。
            return
        }
        val searchUrl = buildLensSearchUrl(targetUrl)
        val opened = CustomTabsUtil.openCustomTab(params.context, searchUrl)
        if (!opened) {
            Toast.makeText(
                params.context,
                R.string.image_search_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * 共有アクションを実行する。
     */
    private fun handleShareImage(params: ImageMenuActionRunnerParams) {
        fetchImageUriAndRun(
            params = params,
            onFetchFailedMessageRes = R.string.image_share_failed,
            onUriReady = { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(params.context.contentResolver, "", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (intent.resolveActivity(params.context.packageManager) != null) {
                    params.context.startActivity(Intent.createChooser(intent, null))
                } else {
                    Toast.makeText(
                        params.context,
                        R.string.no_app_to_share_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    /**
     * URLから画像URIを取得し、成功/失敗で共通処理を実行する。
     */
    private fun fetchImageUriAndRun(
        params: ImageMenuActionRunnerParams,
        onFetchFailedMessageRes: Int,
        onUriReady: suspend (Uri) -> Unit,
    ) {
        val targetUrl = params.currentImageUrl
        if (targetUrl.isBlank()) {
            // Guard: 空URLは処理対象にしない。
            return
        }
        params.coroutineScope.launch {
            val result = ImageCopyUtil.fetchImageUri(params.context, targetUrl)
            if (result.isSuccess) {
                onUriReady(result.getOrThrow())
            } else {
                Toast.makeText(
                    params.context,
                    onFetchFailedMessageRes,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
