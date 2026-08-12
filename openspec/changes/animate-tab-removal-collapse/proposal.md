## Why

タブ削除時、削除前から表示されていたカードの配置アニメーションと、画面上端外から新たに表示される別カードの配置が一時的に交差し、異なる2枚のカードが重なって見えることがある。削除行自身の高さを縮めて周囲を通常レイアウトで追従させ、カード同士が独立した移動軌道を通らない削除表現へ変更する。

## What Changes

- 単体削除および「全てのタブを閉じる」で、対象keyを `TabListUiState` の削除中状態へ登録する。
- 削除中カードを `AnimatedVisibility` のフェードアウトと垂直縮小で退出させ、固定の既存削除時間後に既存の単体またはbulk削除APIを1回呼ぶ。
- 削除時の `animateItem` placement/disappearanceを無効化し、周囲の移動を削除行の高さ縮小による通常レイアウトへ一本化する。
- カード間余白を縮小対象へ含め、退出完了時に余白だけが残って跳ねる状態を防ぐ。
- 既存のスワイプ飛び出し、削除中の二重操作防止、bulk command・projection・永続化・選択補正を維持する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tablist-ui`: 単体・一括クローズの対象カードをUiState駆動で縮小・フェードアウトし、退出後に既存削除経路へ渡す要件へ変更する。

## Impact

- UI state/event: `TabListUiState.kt`、`TabListViewModel.kt`
- Compose list: `TabScreenContent.kt`、`TabsPagerContent.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt`、`RemovableTabList.kt`
- Tests: `TabListViewModelTest.kt` とタブ一覧Compose test
- `TabSessionStore`、Board/Thread Coordinator、Repository、DAO、Room schema、文字列リソース、単体close API、bulk close APIは変更しない。
- `add-bulk-delete-tabs` と `optimize-bulk-tab-close` の完了実装に依存する後続UI変更として扱う。
