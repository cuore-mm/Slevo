package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel

internal fun ReplyInfo.toThreadPostUiModel(): ThreadPostUiModel {
    return ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = name,
            email = email,
            date = date,
            id = id,
            beLoginId = beLoginId,
            beRank = beRank,
            beIconUrl = beIconUrl,
        ),
        body = ThreadPostUiModel.Body(
            content = content,
        ),
        meta = ThreadPostUiModel.Meta(
            momentum = momentum,
            urlFlags = urlFlags,
        )
    )
}

