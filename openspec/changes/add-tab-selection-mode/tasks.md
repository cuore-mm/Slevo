## 1. 一括固定の永続化基盤

- [x] 1.1 `OpenBoardTabDao`と`OpenThreadTabDao`に対象ID集合を指定値へ更新する固定状態queryを追加し、対象外行を更新しないDAO／Repositoryテストを通す
- [x] 1.2 `TabsRepository`に板／スレッドのbulk pin APIを追加し、最大900件chunk、1 write permit、1 Room transaction、失敗時rollback、sortOrder維持をRepositoryテストで検証する
- [x] 1.3 Boardのprojection operationと`BoardTabsCoordinator`にtarget pinned値を持つBulkPinを追加し、mixed状態の一括固定／解除、1 pending operation、対象外維持、失敗時canonical復帰を`BoardTabsCoordinatorTest`で検証する
- [x] 1.4 Threadのmutation intent queueと`ThreadTabsCoordinator`にtarget pinned値を持つBulkPinを追加し、mixed状態の一括固定／解除、FIFO barrier、対象外維持、失敗時canonical復帰を`ThreadTabsCoordinatorTest`で検証する
- [x] 1.5 `TabSessionStore`に板／スレッドのbulk pin委譲を追加し、caller cancellation後の所有権と非キャンセル例外封じ込めを`TabSessionStoreTest`で検証する

## 2. 選択状態とViewModel遷移

- [x] 2.1 `TabListUiState`に`selectionModePage`、板URL集合、`ThreadId`集合、選択メニュー状態／boundsと導出プロパティを追加し、既存の長押し単一対象フィールドと独立していることをコンパイルで確認する
- [x] 2.2 `TabListViewModel`に0件開始、長押し対象付き開始、板／スレ選択toggle、選択終了、選択メニュー開閉を追加し、`TabListViewModelTest`で各状態遷移と長押しoverlay解除を検証する
- [x] 2.3 `TabListViewModel`の検索開始／終了とページ変更処理を選択階層へ対応させ、選択→検索と検索→選択の双方、検索Back後の選択維持、次のBackでの選択終了を単体テストで検証する
- [x] 2.4 公開全一覧を基準に選択keyをpruneする処理を追加し、検索結果外のkeyは維持し、canonical一覧から消えたkeyだけ除去することを単体テストで検証する
- [x] 2.5 選択対象を一覧順にスナップショット化する一括close／pin関数を`TabListViewModel`へ追加し、固定タブをclose対象へ含めること、mixed時は全固定、全固定時は全解除、0件no-op、固定／解除後は選択集合だけをクリアして選択モードを維持することを単体テストで検証する

## 3. bulk close経路の選択対象対応

- [x] 3.1 `TabSessionStore`の既存bulk close経路を選択スナップショットから再利用し、固定タブを除外せず対象holderだけdisposeすることを`TabSessionStoreTest`で検証する
- [x] 3.2 `BoardTabsCoordinatorTest`と`ThreadTabsCoordinatorTest`へ固定／未固定混在対象のbulk closeケースを追加し、1 pending projection、逐次closeと同じ最終選択、失敗時canonical復帰を検証する
- [x] 3.3 通常時の「全てのタブを閉じる」が引き続き表示中ページの未固定タブだけを対象にする回帰テストを`TabListViewModelTest`と`TabSessionStoreTest`で通す

## 4. カード選択UIとジェスチャー制御

- [x] 4.1 `TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList`、`RemovableTabList`から`TabListCard`へ選択モード、選択済み、toggle callbackを伝播し、Previewがコンパイル・描画できることを確認する
- [x] 4.2 `TabListCard`で既存の円形ボーダーを未選択状態領域として維持し、選択済み時のチェック、固定時の左側ピン、選択時`primaryContainer`を実装し、固定ピンと状態領域が同時表示されるCompose testを追加する
- [x] 4.3 `TabListCard`の選択モードclickを選択toggleへ切り替え、通常のタブ遷移とcloseを発火しないことをCompose testで検証する
- [x] 4.4 選択モード中は長押しpointer input、reorder、横スワイプ削除を無効化し、haptic、メニュー、カードoffset、削除callbackが発生しないことをCompose testで検証する
- [x] 4.5 カードへ選択済み／固定済みsemanticsとcontent descriptionを追加し、Compose semantics testで支援技術から両状態を識別できることを検証する
- [x] 4.6 `TabListCard`の固定タブ右側領域を同じアニメーション進行度で移動させ、状態領域とチェックをfade＋scaleで切り替え、開始／終了／選択切り替えの中間フレームをCompose testで検証する

## 5. 上部・下部UIとメニュー

- [x] 5.1 `AnchoredTabActionMenu`と文字列resourceへ「タブを選択」を追加し、通常の右上メニューと長押しメニューの既存項目を維持したまま新項目が表示・通知されることをinstrumented testで検証する
- [x] 5.2 選択モード専用メニューへ「タブを閉じる」と状態依存の「タブを固定」／「タブの固定を解除」を追加し、選択0件では右上その他ボタンが視覚的・操作上無効になることをinstrumented testで検証する
- [x] 5.3 `TabListTopControls`へ選択モード時だけfade表示する左Backと、通常／選択モードで共通利用する右Search／Moreを追加し、Search／Moreの位置を変えず、既存検索欄のslide＋fade指定が維持されることをCompose testまたはanimation設定のテストで検証する
- [x] 5.4 `TabListBottomControls`へ中央の「n個選択中」を追加し、通常操作は通常時のみ、検索のみでは両方非表示、選択および選択＋検索では件数表示となる4状態をCompose testで検証する
- [x] 5.5 `TabScreenContent`で検索Backを選択Backより優先し、検索欄の戻る操作でも選択を維持するよう接続し、選択＋検索→選択→通常の順序をinstrumented testで検証する
- [x] 5.6 `TabScreenContent`から選択メニュー、bulk close、bulk pin、canonical pruningを接続し、アクション後に選択モードを終了しない統合テストを追加する

## 6. 回帰・品質確認

- [x] 6.1 通常時の単体close、単体pin、長押しpreview／復帰、reorder、スワイプ削除、検索表示の既存テストを実行し、選択モード追加による回帰がないことを確認する
- [x] 6.2 新規class／interfaceと非自明関数のKDoc、30行超関数の区分コメント、Preview追加をリポジトリ規約に照らして確認し、不足がない状態にする
- [ ] 6.3 `./gradlew testDebugUnitTest`を実行し、全unit testが成功することを確認する
- [ ] 6.4 `./gradlew assembleDebug`を実行し、debug buildが成功することを確認する
- [ ] 6.5 emulatorを利用できる環境で対象Compose instrumented testを実行し、利用できない場合は未実行理由と対象test classを実装報告へ記録する
