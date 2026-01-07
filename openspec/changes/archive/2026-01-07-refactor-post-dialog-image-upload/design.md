## Context
- Board/Threadで画像アップロードがそれぞれ実装されており、本文へのURL追記ロジックが重複している。
- PostDialogControllerは投稿フォーム更新の中心であり、URL挿入を集約しやすい。

## Goals / Non-Goals
- Goals:
  - 画像アップロード処理を共通化し、本文更新をControllerに集約する。
  - 既存の挙動（本文末尾に改行+URL追記）を維持する。
- Non-Goals:
  - 画像アップロードAPIやUIの仕様変更。
  - 画像挿入の位置やフォーマット変更。

## Decisions
- Decision: Uploaderを共通化し、成功時にControllerのappendメソッドを呼ぶ。
- Decision: ControllerはURL文字列のみ扱い、Context/UriはUI側に残す。

## Alternatives considered
- Board/Threadの実装を残したままUI側で更新する。
  - 重複が残るため却下。
- 画像アップロード処理をControllerへ完全移管する。
  - UI依存が増えるため今回は採用しない。

## Risks / Trade-offs
- Controllerに新たな更新APIが追加される。
  - URL挿入のみの単機能に限定し、責務の膨張を抑える。

## Migration Plan
1. 共通Uploaderを用意し、Board/Threadから利用する。
2. PostDialogControllerにURL挿入APIを追加する。
3. 既存の本文更新処理をController呼び出しに置換する。

## Open Questions
- なし（現行の改行+URL追記を維持する）。
