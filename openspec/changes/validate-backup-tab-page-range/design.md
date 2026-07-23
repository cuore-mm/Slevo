## Context

現在の `app` module では、タブ一覧 page は raw `Int` として扱われている。`ui/tabs/screen/TabScreenContent.kt` の `rememberPagerState` は `pageCount = { 2 }` を直接指定し、`ui/tabs/screen/TabsPagerContent.kt`、`ui/tabs/component/TabListBottomControls.kt`、`TabScreenContent.kt` 内の検索結果スクロール分岐は `0` を board、その他を thread として扱う。一方、`data/backup/restore/BackupReader.kt` の `parseTabs()` は `BackupTabsJson.lastSelectedTabsPage < 0` だけを拒否する。

そのため 2 以上の値は preview に入り、pending restore を経て `PendingRestoreDataStoreWriter.writeTabs()` から `last_selected_page` に保存され、`TabsLocalDataSourceImpl`、`TabsRepository`、`TabSessionStore`、`TabsScaffold` を通って `TabScreenContent(initialPage = ...)` に到達できる。`BackupReaderTest.preview_containsSettingsAndTabsJson()` は現に値 3 を成功例として期待している。

この変更には次の制約がある。

- 現在の page は index 0 の board と index 1 の thread の 2 件のままとする。
- JSON の `lastSelectedTabsPage: Int`、`datastore/tabs.json`、DataStore の `last_selected_page`、backup format version 1 は変更しない。
- 範囲外値は `parseTabs()` の `null` を通じ、既存の `BackupRestoreResult.Invalid("invalid tabs JSON")` 経路で拒否する。
- UI layout、表示文言、操作、navigation、accessibility は変更しない。
- text-setting の range validation と stable ID 形式への migration は扱わない。

## Goals / Non-Goals

**Goals:**

- pager の page 数、page index の意味、バックアップ復元時の有効範囲を 1 個の canonical 定義から導出する。
- 将来 page を末尾追加するとき、canonical 定義と UI content を追加すればバックアップ validator の境界編集を不要にする。
- 負数および現在の page count 以上のバックアップ値を pending restore 作成前に拒否する。
- unit test で現在の page 順序、境界、validator 連携を固定し、定義の drift を検出する。

**Non-Goals:**

- page の追加、削除、並べ替え、名称変更。
- 既存 DataStore 値の sanitize、clamp、migration。
- backup JSON を enum 名や stable ID に変更すること。
- 復元エラー UI または文言の変更。
- `BookmarkListScreen.kt` など、タブ一覧とは別の pager の共通化。
- text-setting の range validation。
- 実装後 audit。

## Decisions

### 1. `data/model/TabPage.kt` の enum を canonical owner とする

新規 `data/model/TabPage.kt` に `enum class TabPage` を置き、宣言順を serialized index とする。現在の entries は `BOARD`、`THREAD` の順にする。companion API は少なくとも次を提供する。

- `count`: `entries.size` から導出する page 数。
- `fromIndex(index: Int): TabPage?`: `entries.getOrNull(index)` 相当で変換する。
- `isValidIndex(index: Int): Boolean`: `fromIndex(index) != null` から導出する。
- 各 entry の `index`: `ordinal` を返し、UI の pager index と既存 serialized integer を対応させる。

`data/model` は既に `ThemeMode` など data と UI の双方が使う application model を所有しており、単一 `app` module 内で `data/backup/restore` と `ui/tabs` の双方から参照できる。定義を `ui/tabs` 配下へ置いて data-layer の `BackupReader` から UI package を import する案は依存方向を悪化させるため採用しない。`BackupTabsJson` companion に count だけを置く案も、UI concept を backup format model が所有し、page semantics が raw literal のまま残るため採用しない。

stable ID を新設する案は、現在の persisted/serialized contract が integer index であり、今回必要なのは現在の範囲検証と定義共有だけなので採用しない。enum の宣言順は persisted contract であり、既存 entry を並べ替えず、新 page は末尾へ追加する invariant を KDoc と test で固定する。

### 2. pager と page-dependent UI を `TabPage` から導出する

`TabScreenContent.kt` の `rememberPagerState` は `pageCount = { TabPage.count }` を使う。検索結果 scroll 対象の分岐は `TabPage.fromIndex(request.page)` を使い、`BOARD` と `THREAD` を明示する。

`TabsPagerContent.kt` は pager から渡された index を `TabPage.fromIndex(page)` で解釈し、`BOARD` は `OpenBoardsList`、`THREAD` は `OpenThreadsList` を描画する。pager が canonical count を使うため通常は変換に成功するが、nullable 変換の失敗時は何も描画しないか、到達不能を明示する既存 UI 非変更の防御的処理とし、未知 index を thread とみなしてはならない。

`TabListBottomControls.kt` は board 判定に `TabPage.BOARD.index` を使い、同ファイル内 3 個の Preview 用 `rememberPagerState` も `TabPage.count` を使う。board/thread の label 順と既存操作は維持する。

これにより production pager、関連 UI semantics、Preview、backup validator が同一 enum に compile-time で依存する。`BookmarkListScreen.kt` の別 pager はこの tab page domain ではないため変更しない。

### 3. `BackupReader.parseTabs()` は canonical index membership だけを検証する

`BackupReader.kt` の `parseTabs()` は Moshi parse 後、`TabPage.isValidIndex(json.lastSelectedTabsPage)` が false なら `null` を返す。caller は既存どおり `cleanupAndError(..., BackupRestoreResult.Invalid("invalid tabs JSON"))` を返すため、エラー型、detail、cleanup、preview/commit の再検証経路を変更しない。

上限を `0..1`、`< 2`、`TabPage.count - 1` として `BackupReader` 内へ再定義してはならない。将来 `TabPage` に entry を末尾追加した場合、validator は無編集で新しい index を受け付ける。

### 4. 互換性は integer index の維持と append-only 順序で確保する

既存 backup/DataStore の 0 と 1 は同じ page を表すため migration は不要である。新しい validator は従来誤って受け入れていた 2 以上を invalid にする意図的な厳格化であり、serialized schema 自体は変わらない。

将来 page を追加するときは enum entry を末尾追加し、その content/label 分岐を実装する。既存 entry の削除・並べ替え・途中挿入は古い integer の意味を変えるので、この変更の invariant に反し、別途 compatibility 設計を必要とする。

## Data Flow

```text
TabPage.entries
  ├─ count ───────────────> TabScreenContent pager pageCount
  ├─ index/fromIndex ─────> TabsPagerContent / TabListBottomControls / search branch
  └─ isValidIndex ────────> BackupReader.parseTabs
                                  │ invalid
                                  v
                         BackupRestoreResult.Invalid

valid backup Int -> BackupPreview -> pending tabs JSON
                 -> PendingRestoreDataStoreWriter -> last_selected_page
                 -> TabsScaffold initialPage -> canonical pager
```

## Error Cases

- `lastSelectedTabsPage < 0`: 従来どおり invalid tabs JSON として拒否する。
- `lastSelectedTabsPage == TabPage.count`: 最初の上限外値として拒否する。
- `lastSelectedTabsPage > TabPage.count`: 拒否する。
- `lastSelectedTabsPage == TabPage.count - 1`: 最大有効値として preview に保持する。
- malformed/missing tabs JSON: 既存挙動を維持する。
- UI 内で未知 index を受け取った場合: thread page へ暗黙 fallback せず、防御的に content を生成しない。backup restore の範囲外値はそれ以前に拒否される。

## Testing Strategy

- 新規 `app/src/test/java/com/websarva/wings/android/slevo/data/model/TabPageTest.kt` で `BOARD.index == 0`、`THREAD.index == 1`、`count == entries.size`、`fromIndex(-1/count) == null`、全 entry index が連続し重複しないことを確認する。これは persisted index の順序と append-only contract の drift detector とする。
- `BackupReaderTest.kt` に最大有効値 `TabPage.count - 1` の成功、`TabPage.count` とそれより大きい値の `BackupRestoreResult.Invalid` を追加し、既存負数 test を維持する。
- `preview_containsSettingsAndTabsJson()` の値 3 は `TabPage.THREAD.index` など現在の有効値へ変更し、preview が有効値を保持する責務だけを検証する。
- UI は `pageCount = { TabPage.count }` を直接参照し、page semantics も enum 分岐へ置換するため、今回の UI 非変更を目的とする新規 instrumented test は追加しない。既存 unit test と Android CI の build/unit test により import、exhaustive branch、source sharing を検証する。
- 実装後は repository の CI workflow で build と unit test を実行する。ユーザー指定により post-implementation audit は行わない。

## Migration Plan

1. canonical `TabPage` と unit test を追加する。
2. UI の pager count と page semantics を canonical 定義へ置換する。
3. `BackupReader.parseTabs()` の範囲検証と `BackupReaderTest` を更新する。
4. CI の build/unit test を通す。

データ migration、backup format version bump、段階 rollout は不要である。rollback はコード変更を戻すだけで可能だが、rollback 後は範囲外 backup を再び受け付けるため、問題発生時も canonical 定義を維持した修正を優先する。

## Implementation Contract

- application code の canonical owner は `app/src/main/java/com/websarva/wings/android/slevo/data/model/TabPage.kt` だけとし、page count または有効範囲を他ファイルへ再定義しない。
- `TabPage` は `BOARD`, `THREAD` の順序を維持し、`count` と validation は `entries` から導出する。既存 entry を並べ替えず、将来 entry は末尾へ追加する旨を type KDoc に記載する。
- `TabScreenContent.kt`、`TabsPagerContent.kt`、`TabListBottomControls.kt` のタブ一覧 page count/semantics を `TabPage` 参照へ置換する。別 domain の pager は変更しない。
- `BackupReader.parseTabs()` は `TabPage.isValidIndex()` を使用し、独自の `0..1` または `2` literal を持たない。invalid result の detail と cleanup path は変更しない。
- `BackupTabsJson`、DataStore key、pending restore writer、mapper、backup format version、UI resource は変更しない。
- repository の KDoc/comment rules に従い、新規 enum と非 trivial helper に KDoc を付ける。Preview function には KDoc を追加しない。
- text-setting range、UI 文言/layout/navigation/accessibility、stable ID migration、post-implementation audit を scope に入れない。

## Open Questions

なし。

## Risks / Trade-offs

- [enum の並べ替えで persisted integer の意味が変わる] → append-only invariant を KDoc、明示的順序 test、`BOARD == 0` / `THREAD == 1` test で固定する。
- [UI の一部に raw page literal が残り、semantic drift が起きる] → タブ一覧の production pager、content 分岐、board 判定、検索分岐、関連 Preview を対象 task で列挙し、実装時に `ui/tabs` の page literal を検索確認する。
- [既に端末 DataStore に範囲外値がある場合は直らない] → 本 finding は backup ingestion の拒否だけを対象とし、既存 persisted corruption の recovery は別 change とする。
- [将来 page を削除・並べ替える変更には enum count 共有だけでは対応できない] → この設計は末尾追加を future-proof 対象とし、意味変更は compatibility 設計が必要な別変更として扱う。
