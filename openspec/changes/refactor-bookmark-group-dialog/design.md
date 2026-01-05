## Context
板/スレッド向けのブックマーク ViewModel は BookmarkGroupEditor を使ってダイアログとグループ編集を管理している。一方、BookmarkViewModel は同様の制御を独自実装しており、共通化できていない。

## Goals / Non-Goals
- Goals: グループ編集の状態と操作を共通化し、Board/Thread/BookmarkList の全画面で同じ制御を再利用する。
- Goals: BookmarkViewModel を一覧・選択・一括操作に集中させる。
- Non-Goals: UI 表示やユーザー操作の挙動を変更しない。

## Decisions
- Decision: GroupDialogState を新設し、ダイアログ/編集に必要な状態を一元化する。
- Decision: GroupDialogController を新設し、グループ追加/更新/削除とダイアログ制御を担う。
- Decision: SingleBookmarkState と BookmarkUiState に GroupDialogState を内包させる。
- Decision: BoardBookmarkViewModel / ThreadBookmarkViewModel / BookmarkViewModel は GroupDialogController を合成して利用する。
- Decision: BookmarkGroupEditor は GroupDialogController へ置き換え、重複ロジックを解消する。

## Risks / Trade-offs
- Risk: 状態構造の変更で UI 参照が漏れる。対策: 既存 UI の参照箇所を洗い出して置換する。
- Risk: 一覧用と単一用で状態要求が異なる。対策: GroupDialogState は最小共通に絞り、必要なら各 UIState 側で補完する。

## Migration Plan
- GroupDialogState と GroupDialogController を追加する。
- BoardBookmarkViewModel / ThreadBookmarkViewModel を GroupDialogController に切り替える。
- BookmarkViewModel のグループ編集/ダイアログ処理を GroupDialogController に置換する。
- BookmarkGroupEditor を削除または退役する。
- UI とテストを更新し、CI を実行する。

## Open Questions
- なし。
