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

- [x] 4.1 返信先プレビューの下に未取得範囲だけを表示する部品を追加し、`strings_reply_preview.xml`へ未取得範囲の文言を追加する。Compose testで統合範囲とアクセシビリティを確認する。
- [x] 4.2 `ReplyPreviewCard`と返信先モードの`ReplyPopup`に開示/隠す状態を接続する。Compose testでプレビューはその場で三行上限のカード、ポップアップは同じ行で通常レスとして開示し、隠すで戻ること、ボタンが別ポップアップを開かないことを確認する。非表示時は本文/名前/IDのsemantics不在、画像ロードコールバック未発火を検証する。
- [x] 4.3 固定上限と超過通知を取り除き、未取得rangeを「ほかN件」の直下のインラインプレビューだけに表示する。返信先一覧は取得済み候補だけとし、未取得だけの行から開かず、更新後に候補がなくなれば閉じる。Compose testで巨大rangeの非展開と取得済み残数の分離、一覧の未取得range非表示を検証する。
- [x] 4.4 `ReplyTargetUiSamples.kt`を削除し、Previewを担当Composableのファイルへ配置する。`PostItemBody.kt`の既存本文・複数挿入Previewを維持し、`ReplyPreviewCard.kt`に分割された複数range例、`ReplyTargetUnavailableRangesRow.kt`に範囲例を置く。狭幅Previewは作らず、Studioで各Previewを描画確認する。
- [x] 4.5 `ReplyTargetDisclosureCard.kt`を削除し、NG開示中の`ReplyPreviewCard`は既存の番号・名前・IDヘッダーと日時省略・三行本文を保って末尾へ「隠す」を配置する。返信先`ReplyPopup`では`PostItemHeader`の投稿番号・名前・日時・IDの同じ並びへ「隠す」を追加し、非表示行は同じ投稿番号/状態/操作の書式で本文情報を構成しない。`showPostNumber`による番号位置変更と独自カード枠を廃止する。
- [x] 4.6 `ReplyTargetStatusRow.kt`を削除し、未取得範囲だけを`ReplyTargetUnavailableRangesRow.kt`へ移す。Compose testで通常投稿と開示中NG投稿のヘッダー/本文書式が同じで末尾操作だけが異なること、インラインの日付省略・三行上限、非表示内容の非露出を確認する。
- [x] 4.7 共有`ReplyTargetDisclosureAction`に細いテーマ枠線を追加し、既存の高さ下限・文字スタイル・表示／隠す操作を維持する。カード面タップ動作は追加しない。
- [x] 4.8 `ReplyPreviewCard`を状態分岐、非表示状態、ヘッダー、本文・画像のComposable責務に分割し、既存のレイアウト、semantics、test tag、画像遷移、操作callbackを維持する。

## 5. 統合検証と仕様整合

- [x] 5.1 parser・transformer・ViewModel・Compose各テストでNG/追加取得更新、範囲統合、ポップアップの未取得除外と既存の返信元/通常リンクfilterを統合検証する。
- [x] 5.2 Preview配置変更後にdebug build/unit testを再実行し、接続可能ならAndroid testも実行する。
- [x] 5.3 strict validation、`git diff --check`、KDoc/Preview規約、Preview描画結果と永続化や通知への非波及を再確認する。
- [x] 5.4 既存`ReplyPreviewCard`/`PostItemHeader`の変更PreviewをStudio描画し、`mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`と接続可能なAndroid testを再実行する。strict validation、`git diff --check`、通常投稿との配置一致、インライン日時省略/三行上限、NG情報の非露出を確認する。
- [x] 5.5 枠線と分割後のComposable Previewを確認し、開示操作・表示構成・レイアウトに意図しない差がないこと、カード面タップ動作を新設していないことを確認する。strict validation、`git diff --check`、debug build/unit testも実行する。

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

## Preview配置整理後

- `ReplyTargetUiSamples.kt`を削除し、分割rangeの返信先カードPreviewを`ReplyPreviewCard.kt`へ、NG＋rangeとrangeのみのPreviewを`ReplyTargetStatusRow.kt`へ配置した。`PostItemBody.kt`の既存Previewと`ReplyPreviewCard.kt`の開示済みNG Previewは維持した。
- 対象3ファイルで狭幅指定（`widthDp` / `fontScale`）がないことを確認した。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（57 actionable tasks、16 executed・41 UP-TO-DATE）。
- Studio Preview: 移設・更新した4件と既存の本文Preview 2件を描画し、範囲表記、NG状態、開示状態を目視確認した。
- Studio解析: `ReplyPreviewCard.kt`と`ReplyTargetStatusRow.kt`にコンパイル阻害エラーなし。IDEのINFO提案のみ。
- Android instrumentation test: 今回は実行せず。`android info`で接続端末を確認できず、AVDは定義済みだが実行中端末はなかった。前回の118件ずつの成功は今回のPreview配置変更より前の結果。
- `openspec validate preserve-reply-target-references --strict`と`git diff --check`: 成功。既知のINFOは先行change依存による単独archive不可のみ。今回の差分はPreviewと計画記述だけで、永続化・通知への変更なし。

## NG返信先の開示カード統一後（後に撤去した旧UI）

- `ReplyTargetDisclosureCard.kt`へ共通surfaceとヘッダーを実装した。左上の番号・右上のTextButtonは両状態で固定し、onSurfaceVariant系の文字色で塗りつぶしを抑え、操作領域は48dp以上とした。
- インラインの`ReplyPreviewCard`/`ReplyTargetStatusRow`と返信先`ReplyPopup`で共通カード枠を使用する。非表示時は投稿内容を描画せず、開示時だけ本文を下へ追加する。ポップアップでは`PostItem`の番号表示を抑えて重複を防ぐ。
- Compose testでインラインとポップアップの開閉前後の番号・ヘッダー・操作ボタンの座標不変、カード高さの増加、番号重複なし、非表示内容の非露出を確認した。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（57 actionable tasks、6 executed・51 UP-TO-DATE）。
- `mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin`: 成功。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest`: 成功。Android 17の`OpenCode_Mobile_Test`と`Medium_Phone`で各118 tests、失敗/skipなし。
- Studio解析: 変更ファイルにコンパイル阻害エラーなし。既存のIDE INFO提案のみ。
- Studio Preview: 共通カードの非表示/開示、開示済みインラインカード、NG状態行を描画し、操作が右上にあり、番号位置とカード枠が共通で、本文追加で高さが伸びることを目視確認した。既存`ReplyPopupPreview`の描画結果は白紙だったためポップアップのStudio Preview確認はスキップし、返信先ポップアップはCompose instrumentation testで検証した。
- `openspec validate preserve-reply-target-references --strict`と`git diff --check`: 成功。先行change依存による単独archive不可のINFOのみ。永続化、NG設定、ViewModelの開示状態所有者、通知経路に変更なし。

## 通常レス表示への整合後

- 旧`ReplyTargetDisclosureCard`と`ReplyTargetStatusRow`を削除。開示中インラインは既存`ReplyPreviewCard`の番号・名前・ID行と三行本文、返信先ポップアップは番号・名前・日時・ID・本文を持つ既存`PostItem`の右端へ「隠す」操作だけを追加した。非表示時は番号・NG状態・「表示」だけを構成し、投稿情報・本文・画像を描画しない。未取得範囲は`ReplyTargetUnavailableRangesRow.kt`へ分離した。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（57 actionable tasks、23 executed・34 UP-TO-DATE）。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest`: 成功。Android 17の`OpenCode_Mobile_Test`と`Medium_Phone`で各115 tests、skip・failureなし。開閉時のヘッダー/番号/操作位置、本文非露出、インライン日時省略と三行上限を確認した。
- Studio: 対象worktreeが`READY`。変更した`PostItemHeader.kt`、`PostItem.kt`、`PostItemBody.kt`、`ReplyPreviewCard.kt`、`ReplyPopup.kt`、開示操作・未取得範囲部品を解析し、コンパイル阻害なし（INFO提案のみ）。Preview 7件（開示/非表示プレビュー、開示/非表示投稿ヘッダー、未取得範囲、複数返信先本文、通常返信先プレビュー）を描画確認した。
- `openspec validate preserve-reply-target-references --strict`: valid。先行change依存のため`thread-reply-preview`/`thread-reply-relations`は単独archive不可とのINFOのみ。`git diff --check`: 成功。

## 返信プレビュー分割・開示ボタン枠線後

- `ReplyTargetDisclosureAction`に`outlineVariant`の1dp枠線を追加し、高さ下限32dp・`labelSmall`・既存の開示操作を維持した。カード面の新しいタップ操作は追加していない。
- `ReplyPreviewCard`は表示分岐の調整役とし、NG非表示状態、通常表示、ヘッダー、本文・画像、overflow操作をComposable責務に分割した。既存のtest tag、semantics、画像共有遷移、操作callbackを維持した。
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`: 成功（57 actionable tasks、13 executed・44 UP-TO-DATE）。
- Studio解析: 分割した`ReplyPreviewCard.kt`、`ReplyPreviewCardHiddenContent.kt`、`ReplyPreviewCardVisibleContent.kt`、`ReplyTargetDisclosureAction.kt`にコンパイル阻害エラーなし。IDE INFO提案のみ。
- Studio Preview: NG非表示・開示の2件を描画し、両状態のボタン枠線、既存のヘッダー・本文配置、三行上限を確認した。
- Android instrumentation test: 接続端末がないためスキップ。`android info`にはSDKとCLI versionのみが表示された。
- `openspec validate preserve-reply-target-references --strict`: valid。先行change依存による単独archive不可のINFOのみ。`git diff --check`: 成功。
