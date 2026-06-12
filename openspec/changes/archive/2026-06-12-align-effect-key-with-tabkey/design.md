## Context

`BbsRouteScaffold.kt` では、各タブページ内で `HorizontalPager` の `key`、`remember(tabKey)`、`DisposableEffect(tabKey)` がすべて `tabKey = getKey(tab)` を基準にタブの同一性を判定している。しかし、タブ初期化用の `LaunchedEffect` だけが `tab` オブジェクトを直接キーにしており、以下のような不一致が生じている。

```
Pager key          → getKey(tab)
remember           → tabKey
LaunchedEffect     → tab      ← 不一致
DisposableEffect   → tabKey
```

`tab` は data class（`ThreadTabInfo` / `BoardTabInfo`）であり、タイトル更新や未読状態変化などで再生成される可能性がある。これにより `LaunchedEffect` が不要にリセットされ、Pager や他の Effect・remember のライフサイクルとずれる。

## Goals / Non-Goals

**Goals:**
- `BbsRouteScaffold` 内のすべての Effect・remember・Pager key を `tabKey = getKey(tab)` 基準に統一する
- タブオブジェクトの再生成による `LaunchedEffect` の不要リセットを防止する

**Non-Goals:**
- スクロール位置保存ロジックの変更
- タブ初期化ロジック（`initializeViewModel` の呼び出し条件やタイミング）の変更
- 新しい機能の追加

## Decisions

### `LaunchedEffect` のキーを `tab` から `tabKey` に変更

**選択**: `LaunchedEffect(isActive, tab)` → `LaunchedEffect(isActive, tabKey)`

**理由**:
- `tabKey` は `getKey(tab)` の結果であり、タブの「論理的同一性」を表す値（通常は String/Int）である。タブオブジェクトが再生成されても `tabKey` が同じなら同じタブとみなすべきである。
- Pager 側は `key = { page -> getKey(tabs[page]) }` でページの同一性を判断しており、ページが再利用されていても `tab` オブジェクトが変わると `LaunchedEffect` がリセットされるのは不自然である。
- 同ファイル内ですでに `val tabKey = getKey(tab)` が抽出されており、一貫性を保つための最小変更である。

**検討した代替案**:
- `LaunchedEffect(tabKey, hasInitialized)` などにする案も考えられたが、`isActive` はページのアクティブ状態を表すトリガーとして必要なので、`isActive` は維持する。
- `hasInitialized` を `remember(tabKey)` で保持しているため、`tabKey` が同じなら初期化フラグも維持される。これは意図通りの動作である。

## Risks / Trade-offs

- **リスク**: `tab` オブジェクトが再生成されるケースで `LaunchedEffect` がリセットされなくなる。しかし `hasInitialized` も `remember(tabKey)` で保持されるため、リセットされても初期化は走らない。リセットを防ぐことで逆に意図しない副作用が減る。
- **トレードオフ**: なし。1行変更で、他のキーと同じ基準に揃えるのみ。
