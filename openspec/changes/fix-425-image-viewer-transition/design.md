## Context

issue #425 では、画像ビューア画面でトップバーとサムネイルバーを表示したまま戻ると、共有トランジション開始直前に画像本体がバーUIより前面に出る不具合が常時再現している。現状の shared element 設定は遷移元と遷移先で描画ポリシーが揃っておらず、戻り遷移時のレイヤ順が不安定になる。

## Goals / Non-Goals

**Goals:**
- 戻り遷移中の画像本体とバーUIの重なり順を安定化し、画像本体がバーUIの背面に維持されることを保証する。
- 遷移元/遷移先の shared element 設定を統一し、pop 時の一時的な前面化を防止する。
- shared transition 有効化フラグの適用漏れを解消し、UI状態と遷移設定の整合を取る。

**Non-Goals:**
- 画像ビューアのページング、ズーム、保存機能の仕様変更。
- ナビゲーション遷移時間やイージングなどの演出チューニング。
- 新しいアニメーションAPIや依存ライブラリの導入。

## Decisions

- Decision 1: 共有トランジション要素の描画を overlay に持ち上げない方針を遷移元・遷移先の双方で統一する。
  - Rationale: issue の症状は「バーUIより前に一瞬出る」ため、overlay 経由の描画差が最も直接的な原因候補であり、両端の明示設定で挙動を固定できる。
  - Alternative: ImageViewer 側のみ修正して対処する案。
  - Why not: 片側だけでは再発防止の契約が弱く、将来の変更で再び設定差分が発生しやすい。

- Decision 2: サムネイル側の shared element 修飾子は `enableSharedElement` の条件分岐で実際に使用される構造に整理する。
  - Rationale: 現状は条件付きで作った修飾子が適用されない箇所があり、仕様上の有効/無効切替と実装が一致していない。
  - Alternative: 常時 shared element を適用して分岐を廃止する案。
  - Why not: ポップアップ表示時など共有遷移を抑止する既存制御と衝突する。

## Risks / Trade-offs

- [Risk] shared element の描画方法変更により、遷移の見え方が既存と微妙に変化する可能性。 → Mitigation: issue 再現手順で forward/pop を目視確認し、バー表示中のレイヤ順を優先して検証する。
- [Risk] `enableSharedElement` 分岐整理時に別経路の遷移が無効化される可能性。 → Mitigation: スレッド通常遷移とポップアップ表示時の双方で shared transition の有効/無効を確認する。
- [Trade-off] overlay 非使用を優先すると、将来的に別画面で overlay 特有の演出を使いたい場合は個別設計が必要になる。 → Mitigation: 本変更では image-viewer capability に限定して契約化する。
