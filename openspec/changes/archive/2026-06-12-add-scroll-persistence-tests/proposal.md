## Why

スレッド/板共通のスクロール位置保存は `BbsRouteScaffold` 内の Compose 副作用に埋め込まれており、定期保存、非アクティブ化時保存、破棄時保存の挙動を直接検証しにくい。スクロール復元の回帰を防ぐため、保存ロジックを小さく分離し、純粋ロジック・Flow・Compose 副作用をそれぞれテスト可能にする。

## What Changes

- スクロール位置保存の副作用を `BbsRouteScaffold` から専用の小さな Composable へ分離する。
- 保存済み位置の重複抑制を、JVM unit test で検証できる純粋ロジックとして切り出す。
- `distinctUntilChanged` と周期保存の Flow 変換を、仮想時間で検証できる関数として切り出す。
- 非アクティブ化時保存と Composable 破棄時保存を、対象 Composable 単体の Compose test で検証する。
- `BbsRouteScaffold` はスクロール保存 Composable を呼び出す構成にし、画面レイアウトと保存副作用の責務を分離する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-state-sync`: タブ固有スクロール位置保存を、定期保存・非アクティブ化時保存・破棄時保存ごとに検証可能な構成へ変更する。

## Impact

- 影響範囲:
  - `BbsRouteScaffold` のスクロール位置保存呼び出し
  - スクロール位置保存専用 Composable / helper の追加
  - JVM unit test と Compose test の追加
- DB スキーマ、外部 API、保存データ形式は変更しない。
- 既存のスクロール復元仕様、タブ切り替え仕様、自動スクロール仕様は維持する。
