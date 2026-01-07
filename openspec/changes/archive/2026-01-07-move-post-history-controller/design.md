## Context
- 投稿履歴の監視・候補更新はBaseViewModelに実装されている。
- PostDialogControllerが投稿状態と投稿処理を集約しており、履歴処理だけが別境界に残っている。

## Goals / Non-Goals
- Goals:
  - 投稿履歴処理の責務をPostDialogControllerへ統合する。
  - Board/Thread ViewModelから履歴処理の委譲コードを削除する。
- Non-Goals:
  - 履歴の取得仕様やUI表示の変更。
  - 投稿フローの成功/失敗条件の変更。

## Decisions
- Decision: BaseViewModelの履歴処理APIを削除し、PostDialogControllerに移管する。
- Decision: PostDialogControllerがCoroutineScopeを使って履歴監視を管理する。

## Alternatives considered
- BaseViewModelに履歴処理を残し、Controllerから委譲する。
  - 共通化は維持できるが責務の分散が残るため却下。
- 履歴処理を別クラスに切り出してControllerが保持する。
  - 可能だが初回はシンプルに直接実装し、肥大化したら分割する方針。

## Risks / Trade-offs
- Controllerの責務が増える可能性がある。
  - 監視Jobの管理をController内で明示し、肥大化時は内部ヘルパー化する。

## Migration Plan
1. BaseViewModelの履歴処理APIを削除。
2. PostDialogControllerへ履歴監視/候補更新/削除ロジックを移植。
3. Board/Thread ViewModelの履歴委譲コードを削除。

## Open Questions
- なし（完全移管で進める）。
