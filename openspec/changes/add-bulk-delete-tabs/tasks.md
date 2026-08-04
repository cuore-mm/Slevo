## 1. 一括クローズ状態とセッション操作

- [ ] 1.1 `TabListUiState.kt` に一括メニューの表示フラグとアンカー `IntRect?` を追加し、初期状態が非表示かつアンカーなしであることを `TabListViewModelTest.kt` で確認する。
- [ ] 1.2 `TabSessionStore.kt` に `TabPage` 指定の一括クローズ入口を追加し、Boardは `openBoardTabs.value`、Threadは `openThreadTabs.value` の未固定タブを一度だけスナップショットして、既存の単体クローズ経路へ一覧順に委譲する。Threadは1つのretained coroutine内で処理し、対象0件はNoOpにする。
- [ ] 1.3 `TabSessionStoreTest.kt` に板ページ・スレッドページそれぞれの未固定タブだけが委譲されるケースを追加し、固定タブ、反対ページ、対象0件がCoordinator呼び出しを受けないことと、Thread対象の処理完了を検証する。
- [ ] 1.4 `TabListViewModel.kt` にメニューopen/dismiss/executeイベントを追加し、executeではメニュー状態を消去してから指定 `TabPage` をStoreへ委譲する。既存 `onPageChanged` でも一括メニューをdismissする。
- [ ] 1.5 `TabListViewModelTest.kt` にアンカー付きopen、外側/Back相当dismiss、ページ変更dismiss、Board/Thread実行時のStore委譲と実行前後のUiStateを追加し、反対ページへ委譲しないことを検証する。

## 2. その他ボタンとAnchoredメニュー

- [ ] 2.1 `strings_common.xml` に表示文言「全てのタブを閉じる」を追加し、その他ボタンは既存 `R.string.more` の「その他」をcontent descriptionとして再利用する。
- [ ] 2.2 `AnchoredTabActionMenu.kt` に一括用の型安全な同名オーバーロードを追加し、「全てのタブを閉じる」だけを既存クローズ項目と同じCloseアイコン・error色で描画する。既存の単一タブ用シグネチャと3項目表示を変更しない。
- [ ] 2.3 `TabListSearchControls.kt` の通常表示を右寄せRowにし、検索ボタンの右へ `MoreVert` の `TabActionButton` を追加する。その他ボタンの `boundsInWindow()` を `IntRect` として `onMoreClick` に渡し、検索モードでは両ボタンを非表示にして既存入力幅を維持する。
- [ ] 2.4 `TabScreenContent.kt` で `TabListTopSearchArea`、`TabListViewModel`、一括用 `AnchoredTabActionMenu` を接続し、実行時だけ `TabPage.fromIndex(pagerState.currentPage)` を渡す。dismiss、ページ変更、BottomSheet/Scaffold両入口で同じ挙動になることをコードレビューで確認する。
- [ ] 2.5 変更した型・非自明関数へ規約どおり宣言アノテーションより上にKDocを追加し、Preview関数はコメントなしのまま、30行を超える関数は責務別セクションコメントで分割されていることを確認する。

## 3. UIと状態遷移の回帰テスト

- [ ] 3.1 `app/src/androidTest/java/com/websarva/wings/android/slevo/ui/tabs/` にComposeテストを追加し、通常時に検索の右へcontent description「その他」のボタンが存在し、検索モードでは通常アクションが非表示になることを検証する。
- [ ] 3.2 同Composeテストで、その他ボタンから「全てのタブを閉じる」1項目のメニューが開くこと、項目クリックが1回通知されること、外側タップまたはBackで実行せずdismissされることを検証する。
- [ ] 3.3 `BoardTabsCoordinatorTest.kt` と `ThreadTabsCoordinatorTest.kt` に不足がある場合は、既存closeを複数回順番に受理した結果、固定タブだけが残る、選択中固定タブが維持される、未固定だけならEmptyへ収束するケースを追加し、全件置換Repository APIが呼ばれないことを検証する。

## 4. 検証

- [ ] 4.1 Android実行環境で対象Composeテストを `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<追加した完全修飾テストクラス名>` により実行する。実行環境がない場合は未実行理由を成果報告へ明記する。
- [ ] 4.2 CI相当の単体テストとビルドを `./gradlew testCiUnitTest assembleCi --stacktrace` で実行し、両タスクの成功を確認する。
- [ ] 4.3 `git diff` でDAO、Repository、データベーススキーマ、全件置換APIに差分がないこと、固定タブ除外と表示中ページ境界がテストされていること、承認範囲外の確認/Undo/通知UIを追加していないことを確認する。
