# スレッドのレス一覧表示処理まとめ（開発者向け）

このドキュメントは、スレッド画面のレス一覧をどのように整形しているかと、
新着バーの表示位置を整理したものです。
ここに記載した挙動は「2026/01/12 時点の実装」を示します。

## 1. Dat読み込み〜ReplyInfo生成（本文の整形）

エントリポイント:
- `parseDat`（`app/src/main/java/com/websarva/wings/android/slevo/data/util/DatParser.kt`）

処理概要:
- `\n` で分割した行を `<>` でパースし、`ReplyInfo` を生成する。
- `name` と `content` は `HtmlCompat.fromHtml` でデコードし、`<br>` を改行に変換する。
- 本文内の `sssp://`（BEアイコン）はURLを抽出して除去する。
- 日付文字列から `ID:` と `BE:` の表記を除去し、`id` と `beLoginId`/`beRank` を抽出する。
- URL判定（画像/スレ/その他）は `calcUrlFlags` でフラグ化する。
- 先頭行の `parts[4]` からスレタイトルを抽出する。

## 2. 表示用モデルへの変換

エントリポイント:
- `ReplyInfo.toThreadPostUiModel`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadPostUiModelMapper.kt`）

処理概要:
- `ReplyInfo` を `ThreadPostUiModel` の `header/body/meta` に変換する。

## 3. 返信関係とツリー構造の派生

エントリポイント:
- `deriveReplyMaps` / `deriveTreeOrder`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadDisplayTransformers.kt`）

派生内容:
- `idCountMap`: 同一IDの総数。
- `idIndexList`: 同一ID内の通番（表示時の `ID(1/3)` などに利用）。
- `replySourceMap`: `>>n` を本文から抽出し、返信元番号→返信先一覧を作る。
- `treeOrder` / `treeDepthMap`: 本文先頭の `>>n` を親としてツリー順と深さを生成。

## 4. 並び順・フィルタ・表示用リストの生成

エントリポイント:
- `updateDisplayPosts`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadViewModel.kt`）
- `buildOrderedPosts`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadDisplayTransformers.kt`）

処理概要:
- 並び順は `ThreadSortType.NUMBER` なら番号順、`TREE` なら `treeOrder` を採用。
- `DisplayPost` を生成し、`num / depth / dimmed / isAfter` を付与する。
  - `NUMBER`: `firstNewResNo` 以降を `isAfter=true` として判定。
  - `TREE`: 新着境界を基準に before/after を分離し、
    - 新着側に古い親が必要な場合は `dimmed=true` の親を挿入する。
    - 新着サブツリーは深さを 0 起点にシフトして表示する。
- 検索（`searchQuery`）は本文のひらがな化でフィルタする。
- NG判定済み番号（`ngPostNumbers`）は表示対象から除外する。
- `replyCounts` は `replySourceMap` から可視投稿の返信数を算出する。
- `firstAfterIndex` は `visiblePosts` 内の「最初の isAfter 投稿」の位置で決める。

## 5. UI表示（整形結果の適用）

エントリポイント:
- `ThreadScreen`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`）
- `PostItem`（`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/PostItem.kt`）

表示ルール:
- `visiblePosts` を `LazyColumn` で描画し、ツリー深さに応じてインデントを付与する。
- `PostItem` はヘッダー/本文/メディアに分割し、文字サイズ・行間は `ThreadUiState` の設定を反映する。
- `dimmed` な投稿は薄い表示で親の文脈を補助する。

## 6. 新着バーの表示位置

表示場所:
- `ThreadScreen` の一覧内で、`firstAfterIndex` の位置に `NewArrivalBar()` を挿入する。
  - 条件: `firstAfterIndex != -1` かつ `idx == firstAfterIndex` のとき。
  - つまり、フィルタ後の `visiblePosts` において「最初の新着レス」の直前に表示される。
- 表示ロジックは `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt` 内。

新着境界の決定:
- `ThreadScaffold` がタブ状態の `firstNewResNo/prevResCount` を `ThreadViewModel.setNewArrivalInfo` に渡す。
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
- `ThreadTabCoordinator` が最終既読位置と前回件数から `firstNewResNo` を算出する。
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadTabCoordinator.kt`
