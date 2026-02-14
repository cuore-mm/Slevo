## Why

スレッド画面のレスポップアップで、同じ表示内容のポップアップを連続して積み上げられるため、誤操作時にスタックが無限に増加しやすく、閲覧性と操作性を損なっている。issue #432 で常時再現しており、連続同一表示のみを抑止する仕様を明確化する必要がある。

## What Changes

- ポップアップ追加時に「現在の最上位ポップアップと同一内容か」を判定し、同一であれば新規追加しない挙動を導入する。
- 同一判定は連続表示時のみ適用し、異なるポップアップを挟んだ再表示（A→B→A）は許可する。
- IDタップ・返信番号タップ・返信元タップ・ツリーポップアップのいずれの経路でも同一の抑止ルールを適用する。
- 連続同一抑止の受け入れ条件を仕様へ追加し、ViewModel単位で回帰を防ぐテスト観点を明文化する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `thread-tree-popup`: レスポップアップの連続同一表示を抑止し、異なる表示を挟んだ再表示は許可する要件を追加する。

## Impact

- 影響コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt` のポップアップ追加経路。
- 影響仕様: `openspec/specs/thread-tree-popup/spec.md` にポップアップ連続同一抑止の要求とシナリオを追加。
- テスト影響: `ThreadViewModel` のポップアップスタック更新に対するユニットテスト追加・更新。
