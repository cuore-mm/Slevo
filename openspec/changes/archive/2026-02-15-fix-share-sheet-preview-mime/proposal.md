## Why

共有起動遅延改善で導入した表示キャッシュ再利用経路により、共有機能自体は動作する一方で、共有シート上の画像プレビューが表示されない回帰が発生した。現行実装は再利用元キャッシュファイルの拡張子に依存して共有ファイル名を生成しており、画像MIME判定が不安定になっている。

共有シートのプレビュー表示はユーザーが共有対象を確認する重要な導線であり、共有データの型情報（拡張子/MIME）を再利用メタデータとして明示管理する必要がある。

## What Changes

- 画像表示成功時に保存する再利用メタデータへ、`diskCacheKey` に加えて拡張子とMIME情報を追加する。
- 共有/外部アプリ起動の再利用経路では、キャッシュ実ファイル名ではなく再利用メタデータの型情報を使って共有URI生成を行う。
- 型情報が不正または欠損している場合は安全な既定値（`jpg` / `image/jpeg`）へフォールバックする。
- 既存の再取得フォールバック契約は維持し、再利用失敗時も共有機能が継続することを保証する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-image-menu`: 共有系アクションで再利用メタデータの型情報を用いた共有URI生成契約を追加する。
- `image-viewer`: ビューア起点でも同一の再利用メタデータ契約（拡張子/MIME付き）を適用する。

## Impact

- Affected specs:
  - `openspec/specs/thread-image-menu/spec.md`
  - `openspec/specs/image-viewer/spec.md`
- Affected code:
  - `ImageActionReuseRegistry`（再利用メタデータ構造）
  - `ImageCopyUtil`（再利用経路の共有URI生成）
  - 画像表示成功時登録箇所（スレッド/ビューア）
- Expected outcome:
  - 共有シートで画像プレビューが安定表示される
  - 共有機能の成功率と既存フォールバック互換を維持する
