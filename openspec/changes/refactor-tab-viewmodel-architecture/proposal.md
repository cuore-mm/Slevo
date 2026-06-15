## Why

現在の板画面・スレッド画面は、開いているタブごとに `BoardViewModel` / `ThreadViewModel` を独自 registry で保持しており、Android 標準の ViewModel ライフサイクルから外れた管理になっている。特に `ThreadViewModel` はデータ取得、表示変換、検索、NG、ポップアップ、投稿、画像保存、自動更新などを抱えて重く、タブ数に比例して状態重複・メモリ使用量・同期不整合のリスクが増えるため、画面単位 ViewModel とタブセッション状態の境界を見直す。

## What Changes

- 板・スレッドの ViewModel 所有単位を「タブごと」から「画面 route ごと」へ移行する設計に変更する。
- タブごとの差分状態は `TabSessionStore` 配下のセッション状態として扱い、ViewModel は選択中タブと Repository / UseCase / Settings 等を合成して `UiState` を生成する役割へ寄せる。
- 板一覧・スレ本文・既読・ブックマーク・NG などのデータ正本は Repository / DB / UseCase 側に置き、ViewModel が長期保持する表示データを最小化する。
- `TabViewModelRegistry` による per-tab ViewModel キャッシュを廃止または互換層へ縮小する移行方針を定義する。
- 既存のタブ切替、スクロール位置復元、新着表示、検索、ポップアップ、投稿ダイアログ、自動更新のユーザー体験は維持する。
- **BREAKING**: 内部設計として、タブごとの `BoardViewModel` / `ThreadViewModel` インスタンスを前提にした初期化・解放・同期フローを変更する。

## Capabilities

### New Capabilities
- `tab-viewmodel-architecture`: 板・スレッド画面における ViewModel 所有単位、タブセッション状態、画面 `UiState` 合成、ライフサイクル管理の境界を定義する。

### Modified Capabilities
- `board-thread-init`: Board/Thread ViewModel の初期化単位をタブ単位から画面 route 単位へ変更し、選択中タブ変更時の再合成・更新フローを定義する。
- `thread-state-sync`: スレッドの客観状態・既読状態・タブ固有状態の分離に加え、ViewModel がそれらの正本を重複保持しないことを明確化する。

## Impact

- 影響範囲:
  - `ui/thread/viewmodel/ThreadViewModel.kt`
  - `ui/board/viewmodel/BoardViewModel.kt`
  - `ui/bbsroute/BaseViewModel.kt`
  - `ui/bbsroute/BbsRouteScaffold.kt`
  - `ui/tabs/store/TabSessionStore.kt`
  - `ui/tabs/registry/TabViewModelRegistry.kt`
  - `ui/tabs/coordinator/*`
  - `ui/thread/state/ThreadUiState.kt`
  - `ui/board/state/BoardUiState.kt`
  - 関連する Repository / UseCase / テスト
- 外部 API やデータベーススキーマの変更は必須ではないが、タブ固有状態の永続化項目を整理する場合は移行が必要になる可能性がある。
- 実装は段階移行とし、まず責務切り出しと正本の明確化を行い、その後 per-tab ViewModel registry 依存を削減する。
