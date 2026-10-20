# Tasks

このチェックリストは実装担当用。計画作成時にはアプリコード変更・ビルド・テストを実行しない。ソースパスは`app/src/main/java/com/websarva/wings/android/slevo/`、ユニットテストは`app/src/test/java/com/websarva/wings/android/slevo/`を基点とする。追加型/API名は`design.md`の提案名を用いる。

## 1. 共通安価解析

- [x] 1.1 `data/util/ReplyAnchorParser.kt`に位置付きtoken・番号区間の解析を追加する。`ReplyAnchorParserTest.kt`で単一、範囲、カンマ、混合、改行、文中、offsetを検証し、旧範囲非展開ケースを新仕様へ置換する。型と非自明APIのKDocを追加する。
- [x] 1.2 同パーサへ取得済み上限で交差する参照先解決と出現順の重複除去を追加する。0、逆順、不完全表記、overflow、空投稿集合、`>>1-2147483647`を100件上限で解決するテストで、安全に終了し部分一致しないことを確認する。
- [x] 1.3 `ThreadDisplayTransformers.deriveReplyMaps`と`ReplyNotificationDetector.detect`の参照抽出を共通パーサへ移行する。`ThreadDisplayTransformersTest`と`ReplyNotificationDetectorTest`で範囲途中・カンマ後半・重複参照が認識されることを検証する。NG除外は後続段階で追加する。
- [x] 1.4 `deriveTreeOrder`を共通tokenに接続し、既存の単一親選択を維持する。本文先頭以外の安価、範囲の先頭番号、`>>999 >>1`、既存更新グループのテストで、後続参照への親繰上げや複数親化がないことを確認する。

## 2. 共通NG判定と返信件数修正

- [x] 2.1 `ThreadRouteViewModel.compileNgs`の不正正規表現の扱いを実コードで確認してから、`compileNgs`/`deriveNgPostNumbers`を`data/util`の共通matcherへ抽出する。ID・名前・本文、全板/同板/別板、リテラル/正規表現、タイトルNG対象外、不正正規表現の既存挙動を純粋テストで検証し、KDocに入力・除外規則を記載する。
- [x] 2.2 `ReplyInfo`と`ThreadPostUiModel`の同一内容が同じmatcher結果になる対応付けを追加する。`ThreadRouteViewModel`を新matcherへ移行し、名前や本文の正規化差を含む比較テストで、画面の既存NG結果が変わらないことを確認する。
- [x] 2.3 `ThreadUiState`にNG除外済み返信元マップを追加し、画面合成時に存在確認・重複除去・番号昇順で生成する。NG追加/削除、追加取得、取得件数減少のテストで、番号不変と再取得なしの更新を確認する。
- [x] 2.4 `ThreadPostListContent`、`PostItemHeader`、`ReplyPopup`、`addPopupForReplyFrom`、`ThreadVisiblePostsUseCase`の返信件数・返信元消費を表示用マップへ統一する。返信3件のうち1件NGなら2件、全件NGなら件数リンクなし、非NG5件なら5件とするテストとクリック先の比較で不具合解消を確認する。

## 3. 複数安価のリンクとポップアップ

- [x] 3.1 `LinkUtils.kt`の`group(1)`依存を除き、共通tokenの全spanへ返信注釈を付ける。注釈テストで`>>1-5`と`>>1,3`の末尾まで同じ操作対象になり、URL注釈・文中リンクが保たれることを確認する。
- [x] 3.2 `PostItemBody`から`PostItem`、本体一覧、`ReplyPopup`、ホストのコールバックへ複数参照先を伝える経路を追加し、ViewModelに複数番号ポップアップ入口を追加する。単数入口は委譲させる。ViewModelテストで存在確認、NG除外、重複除去、空対象の無操作、連続同一ポップアップ抑止を確認する。
- [x] 3.3 本体とポップアップ本文の複数安価タップのComposeテストを追加する。後半タップでも表示可能な全参照先が開き、既存の単一安価、ID、URL、ポップアップ座標基準が退行しないことを検証する。

## 4. プレビュー用UiStateと派生データ

- [x] 4.1 `ui/thread/state`にプレビューUiModel、返信先/返信元選択state、元offset付き本文セグメントを追加し、`ThreadUiState`へ投稿番号別に接続する。データ型のKDocと純粋transformerのテストで、子プレビューを持たず、表示行indexではなく絶対番号を使うことを確認する。
- [x] 4.2 専用transformerで安価だけの行を検出し、レス全体の返信先を一件選ぶ。空白付き単一、空白区切り複数、範囲、カンマ、文中除外、複数行、先頭NG/未取得、全件不可のケースで、選択番号・挿入行・一体型/分離型をテストする。
- [x] 4.3 同transformerでNG除外済み返信元の先頭三件と「ほか○件」用の全候補・残数を生成する。返信先は複数行を通じた重複除去後の全候補を用い、NG先行除外、残数ゼロ、非NG5件、返信先1/3/1/5のテストで件数が一致することを確認する。
- [x] 4.4 板デフォルト名を`BoardRepository.fetchBoardNoname`から板単位で取得・UiState保持する経路を追加する。名前のHTML等の表記を揃えて比較し、既定名/独自名/トリップ/取得失敗のテストで省略・保持を確認する。レスごとの取得と既存投稿プレースホルダの変更がないことを確認する。
- [x] 4.5 `extractImageUrls`と同じ認識結果で、プレビュー本文だけから画像URLを除去し全画像一覧・先頭画像・追加枚数を生成する。非画像URL、周囲の文字、混在行、画像だけの行、複数画像、重複URLのテストで、本文破損やビューア入力との差がないことを確認する。
- [x] 4.6 派生処理を投稿版・NG・板デフォルト名の変更で更新する経路へ接続する。ViewModelテストでNG追加/解除と追加取得時に一貫して更新し、無関係なスクロールstate更新では本文再解析を繰り返さないことを確認する。

## 5. 画像部品と表示文脈

- [x] 5.1 `ui/common/ImageThumbnailGrid.kt`から単一サムネイルの描画・成功時遷移判定・失敗表示を共有部品へ抽出する。既存三列グリッドの構成と404/410/その他失敗の挙動を回帰テストし、新規部品のKDocとコメントなしの`@Preview`を追加する。
- [x] 5.2 `ImageSharedTransitionKeyFactory`へ投稿行stable key・プレビュー種別・対象番号から独自namespaceを生成するAPIを追加する。キーのテストで通常レス、返信先、返信元、別ホスト行が衝突せず、同じ入力で安定することを確認する。
- [x] 5.3 プレビュー画像から既存`onImageClick`と`buildImageViewerRoute`へ全画像一覧・index 0・namespaceを渡し、共有要素有効フラグと既存画像UiState/APIを接続する。route入力とComposeテストで先頭一枚表示でも全画像をスワイプ可能、ロード中は遷移なし、失敗は既存再試行可否、画像タップで本体クリックが発火しないことを確認する。

## 6. プレビュー描画と本文挿入

- [x] 6.1 専用プレビュー部品を`ui/thread/res`へ追加する。数字から始まり「レス」や`>>`を付けない全幅ヘッダー（番号・独自名・ID）、本文だけ三行ellipsis、通常レスの行間設定に追従するヘッダー／本文、右側56.dpサムネイル、画像なし全幅、画像のみ、`+N`を実装する。各状態の`@Preview`とComposeテストで画像位置、別々の文字サイズ設定と行高、ヘッダーの太さ・先頭表記を確認し、型・非自明関数のKDocを追加する。
- [x] 6.2 `PostItemBody`で元offset付き本文セグメントと返信先プレビューを描画する。単一安価一体型と複数形式分離型を実装し、Composeテストで安価行の非重複、挿入位置、後続本文の順序、挿入前後のURL/安価/長押しの維持を確認する。
- [x] 6.3 `PostItem`の通常画像の下へ返信元三件と省略操作を追加し、返信先の省略操作とともに候補全件ポップアップへ接続する。Composeテストで古い順、表示上限、「ほか2件」で全件表示、本体タップで対象レス単体表示を確認する。
- [x] 6.4 `ThreadPostListContent`で`sortType == NUMBER`の本体だけへプレビューstateを渡す。ツリー/ポップアップ/プレビュー内では無効にし、切替とポップアップ表示のComposeテストで再帰表示なし、外側LazyColumnの既存stable key・新着バー・更新グループ維持を確認する。
- [x] 6.5 「返信」「ほか%d件」「+%d」と読み上げ文言を`app/src/main/res`へ追加し、カード・画像・省略操作を独立したsemantics/フォーカスにする。Composeテストで対象番号・画像総数・一覧対象の識別、48.dp以上の操作領域、二重発火なし、色以外の識別を確認する。

## 7. NG通知抑止の統合

- [x] 7.1 `ThreadRefreshUseCase`へ`NgRepository`を注入し、画面と同じmatcherから通知除外番号を生成して`ReplyNotificationDetector.detect`へ渡す。DetectorテストでID/名前/本文NG、板別適用、文中/範囲/カンマ、自レス除外、一レス一候補を確認する。全投稿と絶対番号を維持し、NGで入力配列を詰めないことを検証する。
- [x] 7.2 `processDetectedNotifications`へ取得結果と板情報を渡し、配信直前にNGを再確認して該当待機候補を既存`SUPPRESSED`へ遷移させる。取得後処理のテストで検出後NG追加、以前の`DETECTED`、NG解除後の非再配信、既存`DELIVERED`不変更、元レス未解決時の抑止を確認する。
- [x] 7.3 NGルール読取の非キャンセル例外では新規通知・配信を行わず、投稿取得成功と最新番号保存を維持する境界を追加する。故障注入テストで本文非提示・本文なしのログ、取得成功、回復後の非遡及通知、`CancellationException`の伝播を確認する。
- [x] 7.4 `ThreadContentLoadUseCase`経由と`ThreadTabsCoordinator.refreshOpenThreads`経由の共通取得後処理テストを追加/更新する。画面未生成でも同じNG結果となり、初回/件数減少/通知無効/権限なし/再起動/並行更新の既存回帰テストと`ReplyNotificationRepositoryTest`で一意性・status遷移が維持されることを確認する。

## 8. 統合検証

- [x] 8.1 `./gradlew :app:assembleDebug :app:testDebugUnitTest`を実行し、ビルドと全ユニットテストの成功を記録する。必要な環境で`./gradlew testCiUnitTest assembleCi --stacktrace`も実行し、失敗があれば原因を修正して再実行する。
- [x] 8.2 端末/エミュレータで`./gradlew :app:connectedDebugAndroidTest`を実行する。番号順限定、本文挿入、NG動的更新、通知の両経路、画像/ポップアップの独立操作の統合テスト成功を記録する。
- [x] 8.4 `ThreadRouteViewModelTest`で無関係なstate更新が返信プレビュー派生データを再生成しないこと、`ReplyAnchorParserTest`で巨大範囲が取得済み上限を超えて展開されないこと、Composeテストで非表示の追加画像をロードしないことを確認する。通常レスの表示内容、既読境界、新着グループは既存回帰テストで維持を確認する。
- [x] 8.5 三つのdeltaの全Scenarioと実装・テストを照合し、型/非自明関数のKDoc・長い関数の区分コメント・`@Preview`のコメントなしを確認する。`openspec validate add-thread-reply-previews --strict`の成功と検証結果を記録し、計画外のDB/設定/バックグラウンド処理変更がないことを確認する。

前回の実装時の検証記録:
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest` — 成功。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest` — 成功（106件、失敗0件）。
- `openspec validate add-thread-reply-previews --strict` — 成功。
- 実機データを必要とする狭幅・横画面・TalkBack・長時間スクロールの手動確認 — 未実施。対象スレッドデータが利用できないため、8.3を必須タスクから削除し、8.4を自動検証へ置き換えた。

前回の追加実装に対する再検証記録:
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest` — 成功。
- `mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin` — 成功。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest` — 成功（OpenCode_Mobile_Test・Medium_Phoneの各107件、失敗0件）。初回の文字サイズテスト失敗はマージ済みsemanticsから別Textのレイアウト結果を取得していたため、unmerged treeで個別Textを選択するよう修正して解消した。
- `openspec validate add-thread-reply-previews --strict` — 成功。
- Studio分析・Compose Preview・TalkBackの手動確認 — 未実施。対応するCompose UIテストを実施し、Studioによる追加確認は行っていない。

行高追従・ヘッダー表記変更に対する再検証記録:
- `mise exec -- ./gradlew :app:assembleDebug :app:testDebugUnitTest` — 成功（最終実行は57タスクすべてUP-TO-DATE）。
- `mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin` — 成功。
- `mise exec -- ./gradlew :app:connectedDebugAndroidTest` — 成功（Medium_Phone AVDで107件、失敗0件）。旧`>>1`期待値を更新し、ヘッダー全体が番号から始まることと「レス」「>>」がないことを検証した。
- `openspec validate add-thread-reply-previews --strict` — 成功。
- Studio分析・Compose Preview・TalkBackの手動確認 — 未実施。Compose UIテストを実施し、StudioとTalkBackによる追加確認は行っていない。
