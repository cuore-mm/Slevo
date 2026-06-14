## Context

現在のスレッド一覧は、`DisplayPost` を `LazyColumn` の item として直接渡し、`num`、`dimmed`、`isAfter`、`depth` から key を生成している。TREE 表示では新着グループの先頭に既存親レスを dimmed 行として挿入するため、同じレス番号が複数の表示文脈で一覧内に再登場する。

この構造では、同じレスが複数グループで dimmed 親として出現した場合に key が衝突する。Compose の `LazyColumn` は同一リスト内の key 重複を許可しないため、スクロール中や更新後の再計測時にクラッシュする。

## Goals / Non-Goals

**Goals:**

- スレッド一覧の各表示行に、同一リスト内で一意な stable key を割り当てる。
- 投稿表示情報と LazyColumn item identity の責務を分離する。
- TREE 表示、NUMBER 表示、新着グループ、dimmed 親行の既存表示挙動を維持する。
- key 重複をユニットテストで検出できる構造にする。
- 応急的な index 依存 key ではなく、表示文脈を含む安定した key を採用する。

**Non-Goals:**

- スレッドの並び順アルゴリズム自体は変更しない。
- DB スキーマや永続化形式は変更しない。
- タブ経路、Navigation、板画面の設計は変更しない。
- `LazyColumn` のスクロール位置保存仕様は変更しない。

## Decisions

### 1. `ThreadListItem` を最終表示行モデルとして導入する

`DisplayPost` は投稿をどのように表示するかを表す中間モデルに留め、`LazyColumn` へ渡す最終単位として `ThreadListItem` を導入する。

想定する構成:

```text
ThreadListItem
  ├─ HeaderDivider
  ├─ NewArrivalDivider
  └─ PostRow
       ├─ displayPost
       ├─ groupIndex
       ├─ role
       ├─ occurrenceIndex
       └─ stableKey
```

これにより、投稿番号や見た目の属性ではなく、一覧上の表示行そのものを item identity として扱う。

代替案として `DisplayPost` に `stableKey` を直接持たせる方法もあるが、`DisplayPost` が表示属性と LazyColumn identity の両方を担うため責務が曖昧になる。今後 divider や広告、エラー行など投稿以外の行が増える可能性も考慮し、最終表示行モデルを分離する。

### 2. key は表示文脈から生成する

投稿行の key は、少なくとも以下を含む表示文脈から生成する。

- レス番号
- 表示ロール
  - 通常投稿
  - dimmed 親投稿
  - 新着範囲内投稿
- 更新グループ index
- 同一グループ・同一ロール内での出現 index

例:

```text
post_729_dimmed_parent_group_1_occ_0
post_729_dimmed_parent_group_2_occ_0
post_729_normal_group_0_occ_0
```

これにより、同じレス番号が複数回表示されても、表示文脈が異なれば別 item として扱える。

代替案として LazyColumn の `itemsIndexed` の index を末尾に付与する方法もある。しかしリストの前方に item が追加・削除されると key が変動し、スクロール位置保持や再利用の安定性が落ちるため、恒久対応には採用しない。

### 3. `DisplayPost` 生成と `ThreadListItem` 生成を分ける

`buildOrderedPosts` / `buildGroupDisplayPosts` は、引き続き投稿の表示属性と順序を生成する。`buildGroupedDisplayPosts` 相当の後段で `ThreadListItem` を生成し、groupIndex、role、occurrenceIndex、stableKey を確定する。

この分割により、既存の表示順生成ロジックを大きく変更せず、LazyColumn key 一意性だけを明確な境界で保証できる。

### 4. 新着バーと header は投稿行とは別 item として扱う

現在の header divider や新着バー表示は、投稿行とは異なる item identity を持つ。`ThreadListItem` に divider 系 item を含めることで、一覧全体の key 生成を一箇所に集約する。

ただし、初期実装で既存 UI 構造を大きく変えるリスクが高い場合は、まず投稿行だけを `ThreadListItem.PostRow` 化し、divider は既存 key を維持してもよい。その場合も最終的な設計責務は `ThreadListItem` に寄せる。

## Risks / Trade-offs

- [Risk] 表示行モデルの導入により ViewModel と Composable の受け渡し型が変わる。
  - Mitigation: `DisplayPost` を即時廃止せず、`PostRow.displayPost` として内包して段階的に移行する。
- [Risk] key に groupIndex を含めるため、グループ再構築時に一部 item key が変わる可能性がある。
  - Mitigation: 既存仕様どおり新着グループはタブ内 ViewModel 状態として保持し、グループの順序が安定する前提をテストする。
- [Risk] role 判定が `dimmed` / `isAfter` の組み合わせに依存すると、将来の表示属性追加時に key 設計が漏れる。
  - Mitigation: `PostDisplayRole` のような明示的な enum/sealed type を用意し、key 生成時に role を直接参照する。
- [Risk] occurrenceIndex の付与ルールが不明確だと、重複回避はできても意図しない同一 item 扱いが起きる。
  - Mitigation: groupIndex、role、レス番号ごとにカウントし、同じ文脈内で複数回出る場合のみ増分する。

## Migration Plan

1. `ThreadListItem` と投稿表示ロールを追加する。
2. 既存の `DisplayPost` リストから `ThreadListItem.PostRow` リストを生成する変換処理を追加する。
3. `ThreadUiState` または描画直前の入力を、`visiblePosts` から `visibleItems` へ段階的に移行する。
4. `LazyColumn` の key を `ThreadListItem.stableKey` に変更する。
5. TREE 表示 + 複数新着グループで key が重複しないテストを追加する。
6. NUMBER 表示と既存表示挙動の回帰テストを確認する。

Rollback は、変更前の `visiblePosts` 直接描画へ戻すことで可能。ただし key 重複クラッシュが再発するため、rollback は緊急時のみとする。

## Open Questions

- divider 系 item を初回実装で完全に `ThreadListItem` 化するか、投稿行のみ先行移行するか。
- 新着バーを「最新グループ先頭の投稿行に付随する表示」として残すか、独立した `NewArrivalDivider` item として扱うか。
