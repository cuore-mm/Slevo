## 1. Canonical tab page 定義

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/model/TabPage.kt` に `BOARD`、`THREAD` をこの順で持つ `TabPage` enum を追加し、`index`、`count`、`fromIndex(Int)`、`isValidIndex(Int)` を `ordinal` / `entries` から導出する。完了条件: page count や有効範囲に `2` または `0..1` を再定義せず、type KDoc に既存 entry の順序固定と新規 entry の末尾追加 invariant が記載されている。
- [x] 1.2 `app/src/test/java/com/websarva/wings/android/slevo/data/model/TabPageTest.kt` を追加し、`BOARD.index == 0`、`THREAD.index == 1`、`count == entries.size`、全 index の連続性/一意性、`fromIndex(-1)` と `fromIndex(count)` が null、各有効 index が元 entry へ戻ることを検証する。完了条件: persisted integer の順序と canonical 境界の drift を unit test が検出できる。

## 2. UI の canonical 定義参照

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenContent.kt` の production `rememberPagerState` を `pageCount = { TabPage.count }` へ変更し、検索結果 scroll の page 分岐を `TabPage.fromIndex(request.page)` による `BOARD` / `THREAD` の明示分岐へ変更する。完了条件: 現在の board/thread の表示・scroll 挙動を維持し、未知 index を thread として暗黙処理しない。
- [x] 2.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabsPagerContent.kt` の content 分岐を `TabPage.fromIndex(page)` へ変更する。完了条件: index 0 は `OpenBoardsList`、index 1 は `OpenThreadsList` のままで、未知 index は thread content を描画しない。
- [x] 2.3 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/component/TabListBottomControls.kt` の board 判定を `TabPage.BOARD.index` に変更し、同ファイルの 3 個の Preview 用 pager も `TabPage.count` を参照する。完了条件: label、layout、interaction、accessibility と Preview の page 数が現状のままである。
- [x] 2.4 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/**` で tab page の `0` / `1` / `pageCount = { 2 }` を検索し、page identity/count を表す残存 literal があれば `TabPage` 参照へ置換する。ただし別 domain の `BookmarkListScreen.kt` 等は変更しない。完了条件: production pager、content、controls、検索分岐、関連 Preview の page 定義が canonical owner に集約されている。

## 3. Backup validation

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupReader.kt` の `parseTabs()` を `TabPage.isValidIndex(json.lastSelectedTabsPage)` で検証するよう変更する。完了条件: 負数と `TabPage.count` 以上が既存の `Invalid("invalid tabs JSON")` / cleanup 経路に入り、`BackupReader` 内に独自の `0..1` または上限 `2` を持たない。
- [x] 3.2 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/restore/BackupReaderTest.kt` に `TabPage.count - 1` の成功、`TabPage.count` およびそれより大きい値の `BackupRestoreResult.Invalid` test を追加し、既存の負数 test を維持する。完了条件: 最小/最大有効境界と上下の無効境界を canonical count 経由で検証する。
- [x] 3.3 `BackupReaderTest.preview_containsSettingsAndTabsJson()` の範囲外成功 fixture `3` を `TabPage.THREAD.index` に変更する。完了条件: test は有効な tabs 値が preview に保持される責務だけを検証し、範囲外値を成功として期待しない。

## 4. Compatibility と検証

- [x] 4.1 diff を確認し、`BackupTabsJson.lastSelectedTabsPage: Int`、`datastore/tabs.json`、`last_selected_page`、backup format version、pending restore writer/mapper、resource、UI text/layout/navigation/accessibility、text-setting range が変更されていないことを確認する。完了条件: stable ID または data migration を含まず、この change の対象ファイルだけが変更されている。
- [x] 4.2 repository の `ci-build` 手順に従って GitHub Actions の Android CI（build と unit test）を exact implementation HEAD で実行する。完了条件: workflow run が成功し、run ID をこの task に記録する。CI run `29673135027` は implementation HEAD `38b2d5886a8085f09fa17529bc263563949e4db9` で成功した。ユーザー指定により post-implementation audit は実行しない。
