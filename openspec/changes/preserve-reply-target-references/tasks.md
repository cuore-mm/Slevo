# Tasks

実装・検証進捗。ソースパスは`app/src/main/java/com/websarva/wings/android/slevo/`、テストは対応する`app/src/test/`・`app/src/androidTest/`配下。

## 1. 取得済み候補と未取得範囲の抽出

- [x] 1.1 `ReplyAnchorParser.kt`に取得済みレス数との交差から取得済み候補を出現順で抽出し、未取得部分は範囲として返すAPIを追加する。取得済み候補の重複除去と、未取得範囲の重複・隣接統合を行い、未取得範囲内の番号を展開しない。巨大範囲・Int最大値・重複範囲・非昇順token・無効構文をテストする。
- [x] 1.2 `extractReferencedNumbers`の既存処理を変えず、通常リンク・返信関係・通知の呼出元が新APIに置換されていないことを差分確認する。既存の取得済み100件への巨大範囲制限・無効構文・通知判定のunit testが通ることを確認する。

## 2. 行別候補stateと本文挿入

- [x] 2.1 `ReplyPreviewUiState.kt`の行stateに取得済み候補・選択・統合済み未取得範囲を保持させる。固定候補上限や未取得番号ごとの候補stateを除き、行内残数は取得済み候補数−1とするstate testを追加する。
- [x] 2.2 `ThreadReplyPreviewTransformer`は取得済み候補だけを正常→NGの優先順で選び、残りの取得済み候補数を算出する。未取得範囲は区間演算でまとめ、`>>1-2147483647`等を列挙しない。五例、未取得のみ、重複・隣接range、上限を要しない巨大rangeをtestする。
- [x] 2.3 `buildBodySegments`を全対象行の置換へ移行し、複数安価元行の再追加を廃止する。不要になった`isSingleAnchor`は用途確認後に除く。unit testで先頭/途中/末尾/連続/空行付きの各行を置換し、通常本文・文中安価・無効安価のoffsetが保持され、未取得巨大範囲の行が後続行を妨げないことを確認する。
- [x] 2.4 `PostItemBody`で「ほかN件」の直下に未取得範囲を表示し、Nには取得済み候補だけを数える。取得済み候補がない場合も範囲状態を表示する。Compose testで取得済み候補・残数・未取得範囲の分離を検証する。

## 3. 返信先専用ポップアップと状態管理

- [x] 3.1 `PopupInfo.kt`と`ThreadRouteViewModel`へ取得済み候補番号だけを渡す返信先専用入口を接続する。通常リンク等の既存filterは維持する。
- [x] 3.2 popup stackの返信先モードはNGを含む取得済み候補が残る間だけ維持し、候補がすべて未取得になれば閉じる。同一判定は番号順と表示種別で行い、更新後の番号解決と通常モードの既存挙動をテストする。
- [x] 3.3 `ThreadUiState`と`ThreadRouteViewModel`へ表示箇所単位のNG開示集合と表示/隠すイベントを追加する。プレビューの行キーと`popupId`を区別し、閉鎖・無効化・NGルール更新時に掃除する。unit testで他箇所への非伝播、再構成時保持、閉じて再表示時のリセット、新たなNGの非表示、NG設定非変更を検証する。
- [x] 3.4 `ReplyPopup`で選択済みを含む取得済み正常レス/NG状態だけを表示し、未取得候補と範囲行を表示しない。更新後に解決不能な番号を描画せず、全候補消失時に閉じることをテストする。

## 4. NG表示・隠すUIと未取得範囲表示

- [x] 4.1 状態行に統合済みrange表示を追加し、`strings_reply_preview.xml`へ未取得範囲の文言を追加する。NG/未取得rangeの狭幅・文字拡大PreviewとCompose testを確認する。
- [x] 4.2 `ReplyPreviewCard`と返信先モードの`ReplyPopup`に開示/隠す状態を接続する。Compose testでプレビューはその場で三行上限のカード、ポップアップは同じ行で通常レスとして開示し、隠すで戻ること、ボタンが別ポップアップを開かないことを確認する。非表示時は本文/名前/IDのsemantics不在、画像ロードコールバック未発火を検証する。
- [x] 4.3 固定上限と超過通知を取り除き、未取得rangeを「ほかN件」の直下のインラインプレビューだけに表示する。返信先一覧は取得済み候補だけとし、未取得だけの行から開かず、更新後に候補がなくなれば閉じる。Compose testで巨大rangeの非展開と取得済み残数の分離、一覧の未取得range非表示を検証する。
- [x] 4.4 Preview fixtureを未取得range表示へ更新し、通常幅の取得済み候補＋range、狭幅の長いrange、NG非表示／開示、rangeのみ、複数の離れたrangeを個別に確認できるCompose Previewを複数用意する。Studioで描画し、複数挿入と既存の通常本文・返信元Previewも維持する。

## 5. 統合検証と仕様整合

- [x] 5.1 parser・transformer・ViewModel・Compose各テストでNG/追加取得更新、範囲統合、ポップアップの未取得除外と既存の返信元/通常リンクfilterを統合検証する。
- [x] 5.2 artifact変更後にdebug build/unit testを再実行し、接続可能ならAndroid testも実行する。
- [x] 5.3 strict validation、`git diff --check`、KDoc/Preview規約、永続化や通知への非波及を再確認する。

仕様統合上の注意: 未アーカイブの`add-thread-reply-previews`を基点とするMODIFIED deltaが含まれる。将来の統合順は旧change→本changeであり、本計画作成ではarchiveを行わない。

## 初回実装時の検証記録

- `openspec status --change preserve-reply-target-references --json`: proposal/specs/design/tasksがすべてdone。実装タスクの完了を意味しない。
- `openspec validate preserve-reply-target-references --strict`: 成功。`thread-reply-preview`と`thread-reply-relations`はmain spec未作成のため単独archive不可というINFOあり。先行changeに依存する既知の状態であり、統合順は上記のとおり。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（debug assemble、全unit tests）。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest`: 成功。2台のAndroid 17 AVDで各116 testsを実行し、失敗・skipなし。
- Studio: `ReplyTargetStatusRow`、`PostItemBody`、`ReplyPreviewCard`、`ReplyPopup`を解析。PreviewはNG/未取得の狭幅行、開示済みNGカード、複数返信先本文、PostItemを描画して目視確認。Studio解析はINFO提案のみ。
- 返信先候補のNG/追加取得時の再導出、通常リンク・返信元経路の既存除外をTransformer/RouteViewModel/unit・Compose testで確認。
- `git diff --check`: 成功。永続化スキーマやNG設定変更なし。

## 方針変更後の実装・検証記録

- 未取得番号の個別候補と1,000件上限を廃止し、取得済み候補と統合済み未取得範囲を分離した。`extractReferencedNumbers`や通常リンク・返信元・通知の対象選別は維持した。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest`: 成功。Android 17の2台で各117 tests、skip・failureなし。途中の初回実行ではReplyTargetPopupTestが1件失敗したため、巨大範囲の更新後UIを確認するfixtureを小さな取得増加例へ直し、対象テスト両端末成功後に全件を再実行した。
- Studio解析: `ReplyTargetStatusRow`、`PostItemBody`、`ReplyPreviewCard`、`ReplyPopup`はINFO提案のみ。
- Studio Preview: NG行・狭幅未取得範囲行は描画できた。`PostItemBodyWithReplyTargetPreview`は`ReplyPreviewCard`の呼出しで`NoSuchMethodError`となり未描画。Gradle出力の両クラスのdescriptorは一致していることを`javap`で確認したが、Previewの再描画は未解決。Task 4.4は未完了のまま。
- `openspec validate preserve-reply-target-references --strict`: valid。先行change依存によりthread-reply-preview / thread-reply-relationsの単独archive不可INFOあり。`git diff --check`: 成功。型・非自明関数のKDocとPreview関数のコメント不在、DB/永続化/通知への非波及を確認した。

## 未取得範囲の表示方針更新後

- 未取得範囲はインラインプレビューだけに表示し、返信先一覧への範囲データ経路を削除した。未取得範囲だけの行は非操作とし、一覧起動を抑止。更新後に取得済み候補が消えた一覧も閉じる。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（57 actionable tasksはすべてUP-TO-DATE）。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest`: 成功。Android 17の2台で各118 tests、failure・skipなし。
- Studio Preview: 新規の5パターン（通常幅の複数範囲、狭幅の長い範囲、NG非表示、NG開示、未取得のみ）をすべて描画。既存の複数返信先、通常本文、返信先本文Previewも描画して確認した。
- Studio解析: 新規Previewファイルは問題なし。変更対象の既存ファイルには既存のINFO提案・警告のみ。
- `openspec validate preserve-reply-target-references --strict`と`git diff --check`: 成功。OpenSpecのINFOは先行change依存による単独archive不可のみ。DB・永続化・NGルール・通知の変更なし。
