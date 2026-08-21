## 1. 依存追加とジェスチャーPoC

- [ ] 1.1 `gradle/libs.versions.toml`と`app/build.gradle.kts`へ`sh.calvin.reorderable:reorderable:3.1.0`を追加し、`./gradlew :app:dependencies`でAndroid variantが解決できることを確認する。
- [ ] 1.2 `app/src/androidTest/.../ui/tabs/`へPoC用Compose testを追加し、`clickable`と`draggableHandle(custom DragGestureDetector)`の同居状態で通常tap、長押し検出、同一pointer sequenceのdrag開始を確認する。
- [ ] 1.3 PoCへ既存の横スワイプ削除と`LazyColumn`縦スクロールを組み込み、長押し成立前は両操作へ入力を譲り、長押し成立後はreorderが所有できることを確認する。
- [ ] 1.4 PoCで長押し後のupを`PointerEventPass.Initial`でconsumeし、MenuOpen分岐で通常`onClick`が発火しないことを確認する。失敗した場合だけModifier内のpointer sequence限定抑止フラグを試す。
- [ ] 1.5 PoCで非focusable Popupを長押し途中に表示し、Preview→OpenとPreview→dragでPointerEvent、表示位置、Popup properties更新が途切れないことを実機でも確認する。
- [ ] 1.6 1.2〜1.5の合格条件を満たさない場合は本実装へ進まず、失敗条件と端末・Composeバージョンを`design.md`へ追記してfallback設計を更新する。満たした場合はPoC専用コードを本実装へ統合できる形に整理する。

## 2. メニュー状態とPopup再利用

- [ ] 2.1 `TabListUiState.kt`へ`Idle`、`Preview(tabKey)`、`Open(tabKey)`を表す型安全なメニュー状態を追加し、既存の板・スレッド選択とboundsを重複なく保持できるようにする。
- [ ] 2.2 `TabListViewModel.kt`の長押し開始、選択解除、詳細、固定切替、close処理を新しいメニュー状態へ移行し、Preview→Open、Preview→drag、Back、外側dismissのunit testを`TabListViewModelTest.kt`へ追加する。
- [ ] 2.3 `AnchoredOverlayMenu.kt`へ`focusable`、`dismissOnBackPress`、`dismissOnClickOutside`、`interactive`パラメータを追加し、既存呼び出しの既定挙動を変えないことを`TabBulkCloseMenuTest.kt`で確認する。
- [ ] 2.4 `AnchoredTabActionMenu.kt`の項目内容をPreview/Openで共有し、`interactive=false`ではclick、ripple、accessibility actionを公開しないCompose UI testを追加する。
- [ ] 2.5 `TabScreenContent.kt`の`TabLongPressOverlayLayer`を、Previewでは非focusable Popup、Openではfocusable Popupとして同じ`AnchoredOverlayMenu`を維持する構成へ変更し、Preview→drag時にfloating cardを閉じて元カードを復帰させる。

## 3. カード領域分離とReorderable UI

- [ ] 3.1 `TabListCard.kt`のCard直下をBox構成へ整理し、ContentAreaとCloseIconButtonまたはpin表示を兄弟として配置する。既存レイアウト、カード全体bounds、スワイプoffset、選択scale/alphaを維持するPreviewを更新する。
- [ ] 3.2 通常`clickable`、既存スワイプDetector、カスタム`SlevoTabDragGestureDetector`をContentAreaだけへ付け、close領域のtap・長押し・dragがカードtap、swipe、reorderを開始しないCompose UI testを追加する。
- [ ] 3.3 `RemovableTabList.kt`でstable keyを変えずに`ReorderableItem`を適用し、`OpenBoardsList.kt`と`OpenThreadsList.kt`から板・スレッドそれぞれのmove/start/stop/cancel callbackを配線する。
- [ ] 3.4 削除中、飛び出し中、検索結果表示中、MenuOpen中はreorderを無効化し、通常表示へ戻ると再度有効になるテストを追加する。
- [ ] 3.5 Reorderableのplacement animationと既存`AnimatedVisibility`削除アニメーションを同じitemへ重複適用しないmodifier構成にし、既存`TabBulkCloseMenuTest`と削除アニメーションテストを通す。

## 4. ドラッグ中のkey順序draft

- [ ] 4.1 `TabListUiState.kt`へstable keyだけを持つ`ReorderDraft(originalOrder, currentOrder)`を追加し、板の`boardUrl`とスレッドの`ThreadId.value`を既存key契約のまま扱う。
- [ ] 4.2 keyを別indexへ移動するpure functionと、最新Storeリストへdraft順を適用してDBにのみあるkeyを末尾追加・消失keyを除去するpure functionを追加し、先頭・中間・末尾・同位置・追加・削除のunit testを作成する。
- [ ] 4.3 `TabListViewModel.kt`へdrag start、move、cancel、dropイベントを追加し、moveでは`currentOrder`だけを更新、cancelと画面破棄では保存せずdraftを破棄するunit testを追加する。
- [ ] 4.4 `TabScreenContent.kt`で最新のStoreタブ情報をdraft key順に合成し、ドラッグ中にタイトル、レス数、pin状態が更新されても最新情報を表示するunitまたはCompose UI testを追加する。

## 5. 順序列専用のRoom永続化

- [ ] 5.1 `OpenBoardTabDao.kt`と`OpenThreadTabDao.kt`へ、stable keyを指定して`sortOrder`列だけを更新するDAO APIを追加する。
- [ ] 5.2 `TabsRepository.kt`へ板・スレッド用reorderメソッドを追加し、`DatabaseWriteGate`と`db.withTransaction`内でDBの現在順を読み、要求key、DBのみkey、削除済みkeyを設計規則どおりmergeして`0..n-1`へ再採番する。
- [ ] 5.3 reorderメソッドが`upsertAll`、`deleteNotIn`、Entity全体置換、thread state保存、GCを呼ばず、pin、scroll、metadataを変更しないことを`TabsRepositoryThreadStateTest.kt`等のinstrumented testで検証する。
- [ ] 5.4 repositoryのFailure時にtransactionがrollbackされ、途中の`sortOrder`が公開されないinstrumented testを追加する。
- [ ] 5.5 1,252件を逆順または長距離移動として再採番するinstrumented performance testを追加し、既存CIで許容できない場合だけbind上限以下のchunk方式へ変更して再計測する。

## 6. Coordinator pending projection

- [ ] 6.1 `TabProjectionPrimitives.kt`へ、要求key順に残存タブを並べ、未指定の新規keyを相対順のまま末尾へ残すpure reorder projectionを追加し、追加・削除・重複・未知keyのunit testを作成する。
- [ ] 6.2 `BoardTabsCoordinator.kt`へ`Operation.Reorder`、pending登録、repository実行、baseline後の順序確認、Failure rollback、連続reorder supersessionを追加し、`BoardTabsCoordinatorTest.kt`で各terminal resultが1回だけ返ることを検証する。
- [ ] 6.3 `ThreadTabsCoordinator.kt`のmutation intentとpending operationへReorderを追加し、add/delete/pin/infoとの受理順、canonical確認、Failure、連続reorderを`ThreadTabsCoordinatorTest.kt`で検証する。
- [ ] 6.4 reorder pending中の新規タブを末尾へ投影し、削除タブを除外し、選択keyとpin/info/scrollを維持するBoard/Thread両方のunit testを追加する。
- [ ] 6.5 `TabSessionStore.kt`へ板・スレッド用reorder facadeを追加し、Coordinatorがpending projectionを公開した後に呼び出しが受理完了する契約をテストする。
- [ ] 6.6 `TabListViewModel.kt`のdrop処理をStoreのreorder APIへ接続し、pending受理後にdraftを破棄して表示順が途切れずStore projectionへ引き継がれるテストを追加する。

## 7. アクセシビリティと回帰確認

- [ ] 7.1 `TabListCard.kt`へ「上へ移動」「下へ移動」のcustom accessibility actionを追加し、境界では不可能な方向を成功扱いにせず、可能な移動は通常dropと同じStore経路へ渡す。
- [ ] 7.2 TalkBack相当のsemantics testで、通常tap、MenuOpen、上下移動、Preview中項目の非操作性、closeボタンの独立ノードを確認する。
- [ ] 7.3 検索、固定タブ、スワイプしきい値、削除アニメーション、bulk close、選択維持、最後に選択した板/スレッドページ復元の既存テストを実行し、reorder導入前の挙動を維持する。

## 8. ビルドと最終検証

- [ ] 8.1 新規・変更したclass、interface、non-trivial functionへ必須KDocを追加し、annotationより上への配置、長いfunctionのセクションコメント、データ変換の順序不変条件を確認する。
- [ ] 8.2 `./gradlew :app:testDebugUnitTest`を実行し、全unit testが成功するまで修正する。
- [ ] 8.3 `./gradlew :app:assembleDebug`を実行し、アプリbuildを成功させる。
- [ ] 8.4 関連instrumented testを接続端末またはemulatorで実行し、PoCジェスチャー、Room再採番、アクセシビリティ、1,252件性能が成功することを記録する。
- [ ] 8.5 `openspec validate add-drag-reorder-tabs --strict`を実行し、実装とspecの差分がないことを確認する。
