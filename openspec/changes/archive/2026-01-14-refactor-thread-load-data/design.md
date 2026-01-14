## Context
ThreadViewModel#loadData は取得・派生計算・UI反映・履歴処理が1関数に集約されており、変更時の影響範囲が把握しづらい。分割により責務境界を明確にする。

## Goals / Non-Goals
- Goals:
  - 取得/派生計算/UI更新/履歴処理を小さな関数に分離する
  - 挙動を変更せず、既存のUI更新順と副作用を維持する
- Non-Goals:
  - 表示仕様やデータ内容の変更
  - Repository/DB 層の設計変更
  - パフォーマンス最適化の追加

## Decisions
- Decision: 派生計算の出力をデータ構造にまとめ、UI反映関数へ渡す
- Decision: 進捗更新はコールバック内の UIState 更新として継続する
- Alternatives considered: ロジックを Repository へ移動する案は、UIState 更新責務が混在するため採用しない

## Risks / Trade-offs
- リスク: UIState 更新順の差分による表示変化
  - Mitigation: 既存の更新順を維持し、仕様に明文化する

## Migration Plan
- 既存ロジックを分割して置換し、追加の移行作業は行わない

## Open Questions
- なし
