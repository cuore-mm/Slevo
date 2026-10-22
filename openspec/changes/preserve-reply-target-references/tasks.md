# Tasks

計画のみ。アプリ変更・ビルド・テストは実装担当が実施する。ソースパスは`app/src/main/java/com/websarva/wings/android/slevo/`、テストは対応する`app/src/test/`・`app/src/androidTest/`配下。新しい型/API名はdesignの提案であり、既存宣言を確認して確定する。

## 1. 上限付き返信先候補の抽出

- [ ] 1.1 `ReplyAnchorParser.kt`の`parseAnchors`に基づく返信先表示専用の上限付き抽出APIと結果型を追加する。番号出現順の重複除去・最大1,000保持・1,001個目で`truncated`を返す。`ReplyAnchorParserTest`で1,000/1,001、未取得番号、巨大範囲、重複範囲、Int最大値、無効構文を検証する。KDocに取得済み上限を使う既存APIとの用途差を記す。
- [ ] 1.2 `extractReferencedNumbers`の既存処理を変えず、通常リンク・返信関係・通知の呼出元が新APIに置換されていないことを差分確認する。既存の取得済み100件への巨大範囲制限・無効構文・通知判定のunit testが通ることを確認する。

## 2. 行別候補stateと本文挿入

- [ ] 2.1 `ReplyPreviewUiState.kt`へ正常/NG/未取得の候補型、行単位の候補・選択・超過状態を導入する。`PostReplyPreviewUiState`の投稿全体の単数返信先状態を行別集合へ移行し、全消費箇所とfixtureを列挙する。型KDocとstateのunit testで行内残数が候補数−1、返信元残数は従来どおりであることを確認する。
- [ ] 2.2 `ThreadReplyPreviewTransformer`の返信先生成を各安価行へ拡張する。候補をNG/未取得で落とさず、保持範囲内で正常→NG→未取得、同種内は最初を選ぶ。`ThreadReplyPreviewTransformerTest`に提示の正常3NG2・正常1NG4・NG3・NG2未取得1・未取得3の五例、先頭未取得、別行重複、上限外の正常候補を選ばない例を追加する。
- [ ] 2.3 `buildBodySegments`を全対象行の置換へ移行し、複数安価元行の再追加を廃止する。不要になった`isSingleAnchor`は用途確認後に除く。unit testで先頭/途中/末尾/連続/空行付きの各行を置換し、通常本文・文中安価・無効安価のoffsetが保持され、上限超過後も後続行が処理されることを確認する。
- [ ] 2.4 `PostItemBody`の`ReplyTarget`描画を行別候補に接続し、`PostItem`・画面側のイベントを行単位の一覧要求へ移行する。`PostItemBodyReplyPreviewTest`の元行保持期待値を置換し、二行以上の挿入順・元行非存在・前後のURL/文中安価/長押し操作を検証する。返信元の親番号除去テストは変更せず維持する。

## 3. 返信先専用ポップアップと状態管理

- [ ] 3.1 `PopupInfo.kt`と`ThreadRouteViewModel`へ返信先候補モードと超過フラグ、専用表示イベントを導入する。`ThreadScaffold`まで接続し、プレビューの一覧操作とカード本体の単体表示を新入口へ渡す。ViewModel testで全件NG・未取得でも開き、通常リンク/ID/ツリー/返信元の既存filterを維持することを確認する。
- [ ] 3.2 `prunePopupStackWithoutRenderablePosts`と同一ポップアップ判定をモード対応させる。返信先モードは候補番号がある限り残し、同一判定は種別・番号順・超過フラグで行う。ViewModel testで更新後の全件参照不能・未取得→正常・件数減少・通常モードの空一覧除去・連続重複抑止を検証する。
- [ ] 3.3 `ThreadUiState`と`ThreadRouteViewModel`へ表示箇所単位のNG開示集合と表示/隠すイベントを追加する。プレビューの行キーと`popupId`を区別し、閉鎖・無効化・NGルール更新時に掃除する。unit testで他箇所への非伝播、再構成時保持、閉じて再表示時のリセット、新たなNGの非表示、NG設定非変更を検証する。
- [ ] 3.4 `ReplyPopup`で返信先モードの正常レス/NG状態/未取得状態を全候補順に生成し、番号と最新状態を安全に解決する。ViewModel/Compose testで選択済み一件を含む全候補・安定キー・未取得時の添字アクセス回避・通常モード既存表示を確認する。

## 4. NG表示・隠すUIと上限通知

- [ ] 4.1 `ui/thread/res`へ番号・状態・表示ボタンの共有状態行を追加し、`strings_reply_preview.xml`へ合意した日本語文言を追加する。NG非表示/未取得/狭幅・文字拡大のPreviewとCompose testで番号・状態・48dp以上の操作領域・独立フォーカスを確認する。型と非自明関数にKDocを付け、Preview関数にはKDocを付けない。
- [ ] 4.2 `ReplyPreviewCard`と返信先モードの`ReplyPopup`に開示/隠す状態を接続する。Compose testでプレビューはその場で三行上限のカード、ポップアップは同じ行で通常レスとして開示し、隠すで戻ること、ボタンが別ポップアップを開かないことを確認する。非表示時は本文/名前/IDのsemantics不在、画像ロードコールバック未発火を検証する。
- [ ] 4.3 「ほかN件」の直下と返信先一覧末尾に、`truncated`時だけ「参照先が上限を超えたため、一部を省略しています」を表示する。Compose testで1,000は通知なし、1,001以上は両箇所通知と「ほか999件」、残数ゼロでは省略操作なし、行別件数・一覧対象が混ざらないことを確認する。
- [ ] 4.4 `PostItemWithReplyPreviewsPreview`と`PostItemBodyWithReplyTargetPreview`を新stateへ移行し、複数挿入とNG開示状態を確認できるfixtureを追加する。Studioが利用可能ならPreview描画を確認し、利用不能なら理由を記録する。既存通常本文Previewと返信元Previewが残ることを差分確認する。

## 5. 統合検証と仕様整合

- [ ] 5.1 parser・transformer・ViewModel・Compose各テストの合格を照合し、NG/追加取得で表示中候補と件数が更新されること、無関係なstate更新で再解析されないことを統合検証する。返信元件数/一覧/通知のNG除外・通常リンクの既存期待値が維持されていることを記録する。
- [ ] 5.2 `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest`を実行して両方の成功を記録する。端末接続時は`mise exec -- ./gradlew :app:connectedDebugAndroidTest`を実行し、上記の追加UIテストを含む結果を記録する。環境阻害は成功扱いせず具体的に報告する。
- [ ] 5.3 `openspec validate preserve-reply-target-references --strict`と`git diff --check`を実行し、全Scenarioとテストを対応付ける。変更が返信先表示に閉じ、DB/永続設定/通知への仕様外変更がないこと、KDocとPreview規約を確認して検証結果を記録する。

仕様統合上の注意: 未アーカイブの`add-thread-reply-previews`を基点とするMODIFIED deltaが含まれる。将来の統合順は旧change→本changeであり、本計画作成ではarchiveを行わない。

## 計画作成時の検証記録

- `openspec status --change preserve-reply-target-references --json`: proposal/specs/design/tasksがすべてdone。実装タスクの完了を意味しない。
- `openspec validate preserve-reply-target-references --strict`: 成功。`thread-reply-preview`と`thread-reply-relations`はmain spec未作成のため単独archive不可というINFOあり。先行changeに依存する既知の状態であり、統合順は上記のとおり。
- `git diff --check`: 成功。新しい`openspec/changes/preserve-reply-target-references/`内だけを変更。
- アプリのビルド・unit test・instrumentation testは計画作成のため未実施。
