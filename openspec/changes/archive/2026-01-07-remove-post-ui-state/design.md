## Context
- 投稿ダイアログの状態がPostUiStateとPostDialogStateに分割されている。
- BoardはBoardUiState内、Threadは別StateFlowで保持しており、構造の差分が大きい。

## Goals / Non-Goals
- Goals:
  - 投稿状態の単一モデル化（PostDialogState）と可読性の向上。
  - Board/Threadの状態構造を揃え、PostDialogStateAdapterの責務を単純化する。
- Non-Goals:
  - 投稿フローの仕様変更（成功/失敗条件の変更など）。
  - UIデザインや文言の変更。

## Decisions
- Decision: BoardUiState/ThreadUiStateにPostDialogStateを内包し、PostUiStateを廃止する。
- Decision: namePlaceholderをPostDialogStateに追加し、入力フォームの情報を集約する。
- Decision: PostFormStateは共通領域へ移動し、PostDialogStateの依存関係を整理する。
- Decision: PostDialogStateAdapterはUiState内のpostDialogStateに対して読み書きする。

## Alternatives considered
- PostUiStateを残したままPostDialogStateと並列運用する。
  - 画面側の差異は減るが、状態の重複と同期コストが残るため却下。
- BoardのみPostDialogState化し、Threadは既存構造を維持する。
  - 実装は軽いが共通化の目的を満たせないため却下。

## Risks / Trade-offs
- UiStateがさらに肥大化する可能性がある。
  - 対策としてPostDialogStateを1プロパティにまとめ、関連ロジックはPostDialogControllerに集約する。

## Migration Plan
1. PostDialogStateへnamePlaceholderと共通フォーム状態を追加。
2. BoardUiState/ThreadUiStateにpostDialogStateを追加し、既存フィールドを移行。
3. PostUiStateを削除し、PostDialog/各ViewModel/Adapterの参照を置換。

## Open Questions
- なし（Option A/Bは確認済み）。
