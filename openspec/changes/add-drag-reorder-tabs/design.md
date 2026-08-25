## Context

動機は`proposal.md`を参照する。現在のタブ一覧は`RemovableTabList.kt`の`LazyColumn`で、`TabListCard.kt`の外側`Box`が横スワイプ削除、内側`Row`の`combinedClickable`が通常タップと長押しメニューを処理する。長押し後は`TabScreenContent.kt`の`TabLongPressOverlayLayer`が元カードを透明化し、floating cardと`AnchoredOverlayMenu`の`Popup`を表示する。

表示順の正本は、Roomの`sortOrder`をcanonicalとして`BoardTabsCoordinator`または`ThreadTabsCoordinator`がpending operationを投影したリストである。`TabSessionStore`はそのFlowを公開し、`TabScreenContent`が直接収集する。`TabListViewModel`は一覧の正本を所有しない。

Compose BOMは`2026.02.00`でFoundation/UI 1.10.3を利用する。Lazy layoutの完成した公式reorder APIはないため、Calvin-LL Reorderable 3.1.0のカスタム`DragGestureDetector`拡張点を利用する。

初回実装の`SlevoTabDragGestureDetector.detect`は、長押し成立後に`awaitTouchSlopOrCancellation`でもう一度slopを待つ。この待機中はposition changeを所有しないため、DOWNから移動量を蓄積している外側の横スワイプDetectorまたは`LazyColumn`が先にconsumeすると、Previewからdragへ移行できない。また、`TabListCard`の横スワイプDetectorはconsume済みchangeを確認せず、drag開始時に`cancelTabSelection()`で長押し選択を解除するとスワイプが再有効になる。今回の修正はこのgesture所有権だけを対象とし、Reorderableの位置計算や他のpointer処理は統合しない。

## Goals / Non-Goals

**Goals:**

- 既存の通常タップ、長押しメニュー、横スワイプ削除、縦スクロールを維持したまま、同じpointer sequenceで長押しメニューから並び替えへ遷移させる。
- ドラッグ中の高頻度な座標状態をUI層へ閉じ、アプリ状態はstable key順序だけで表現する。
- ドロップ後の楽観表示、Room確認、失敗rollbackを既存Coordinatorのstate machineへ統合する。
- `sortOrder`だけを原子的に更新し、タブの集合や他列を変更しない。

**Non-Goals:**

- 横型ブラウザタブバーへの変更。
- 検索で絞り込まれた部分集合の並び替え。
- 固定タブを先頭区画へ自動移動する仕様。
- Reorderableライブラリの位置計算、アニメーション、エッジスクロールアルゴリズムの再実装。
- Room schemaまたは`sortOrder`型の変更。

## Decisions

### 1. ジェスチャー統合テストを修正ゲートにする

初回実装では、`clickable`、既存スワイプ、カスタム`DragGestureDetector`、Reorderableを同じカードで共存させるPoCを完了しないまま永続化層まで実装したため、buildとunit testが成功してもPreviewからdragへ移行できない不具合を検出できなかった。修正前に実際の`TabListCard`と`RemovableTabList`を使うinstrumented Compose testで現象を再現し、修正後に同じtestを合格させる。

合格条件は、通常タップ、PreviewからOpen、Previewからdrag、長押し前の横スワイプと縦スクロール、drag中のスワイプ抑止、close領域除外が同時に成立することである。タップ、横スワイプ、縦スクロールを一つの巨大なpointer detectorへ統合せず、長押し成立後からReorderableへ渡すまでの区間だけを`SlevoTabDragGestureDetector`が所有する。接続端末またはemulatorでこのtestを実行するまでgesture修正を完了扱いにしない。

### 2. Calvin-LL Reorderable 3.1.0へreorder機構を委譲する

`gradle/libs.versions.toml`と`app/build.gradle.kts`へ`sh.calvin.reorderable:reorderable:3.1.0`を追加する。`RemovableTabList`はstable keyを維持したまま、`itemsIndexed`のcontent直下を`ReorderableItem`にして各itemを包む。`ReorderableItem`のcontent内に削除用`AnimatedVisibility`と後続item用のspacing `Spacer`を含む`Column`を置き、`animateItemModifier`をLazy item直下のreorder wrapperへ渡すことで、Lazy layoutがplacement animationを観測できる構造にする。カードのContentAreaへ`draggableHandle(dragGestureDetector = ...)`を付ける。

ライブラリはdrag offset、移動先、`onMove`、placement animation、エッジ自動スクロールを所有する。Slevoは長押し待機、メニュー状態、追加touch slop、順序draft、永続化だけを所有する。標準`longPressDraggableHandle`は長押し成立時点でdragを開始するため使用しない。

### 3. 長押し後だけカスタムDetectorがMain passで所有する

`combinedClickable`の長押し経路は成立後のpointer sequenceを消費するため撤去し、通常タップには可能な限り`clickable`を残す。カスタムDetectorは`awaitFirstDown(requireUnconsumed = false)`でdownを監視し、長押し前の移動を消費しない。

`awaitLongPressOrCancellation`で長押しが成立した後は`awaitTouchSlopOrCancellation`を使わず、`SlevoTabDragGestureDetector.detect`内の小さなループで既定の`PointerEventPass.Main`から対象pointerの各eventを受け取る。現在のModifier階層では内側のreorderHandleがMain passで外側の`clickable`、横スワイプDetector、`LazyColumn`より先に処理できるため、deltaを取得してから同じchangeを即時consumeする。長押し後に取得時点で既にconsume済みのchangeが現れた場合は、別handlerから所有権を奪い返さずdrag cancelへ収束させる。

長押し成立位置からの移動量をDetector内だけで累積し、追加touch slop未満ではPreviewを維持する。slopを超えたeventでReorderableの`onDragStart`を1回呼び、累積移動全体ではなくslop超過分だけを最初の`onDrag`へ渡してカードのジャンプを防ぐ。以後のdrag offset、移動先、エッジスクロールはReorderableへ委譲する。

追加touch slop前に対象pointerがUPした場合はMain passでUPをconsumeして`onLongPressReleased`を1回呼び、通常`clickable.onClick`を抑止する。pointer消失、system cancel、別handlerによる予期しないconsumeでは`onDragCancelled`へ収束させる。Main passのUP consumeでも通常clickが発火することをtestで確認した場合だけ、UP取得を`PointerEventPass.Initial`へ限定して変更する。pointer sequence限定の抑止フラグはその方法でも解消しない場合の最終手段とし、ViewModelへ置かない。

`TabListCard`の横スワイプDetectorは、各eventの処理前に対象changeの`isConsumed`を確認し、trueならそのgestureから撤退する。横スワイプ用`pointerInput` Modifier自体は常に同じカードへ付け、`canHandleSwipeGesture`、`canDeleteBySwipe`、`isFlyingOut`、`cardWidthPx`は`rememberUpdatedState`でDetector内から読む。状態変化でpointer nodeをdetachさせず、無効化を検出したactive swipeはoffsetを0へspring-backして終了する。`!isDragging`は内部ゲートとして維持し、Reorderableがdragging itemを所有している間は横スワイプ処理を開始しない。`isConsumed`確認がpointer所有権調停の本体で、常設Modifierが長押し時の親node detachによる子reorder cancelを防ぐ。

`RemovableTabList`の`LazyColumn.userScrollEnabled`は、長押し選択やreorder draftで切り替えず常にtrueを渡す。長押し成立後とdrag中は内側のreorder DetectorがMain passでposition changeをconsumeするため、LazyColumnはユーザーの縦スクロールを開始しない。Reorderableのedge autoscrollはprogrammatic scrollであり、この値の固定によって禁止しない。

### 4. Card内のBoxで操作領域とcloseボタンを分離する

`TabListCard`のCard直下をBoxとして、ContentAreaとCloseIconButtonを兄弟にする。ContentAreaへ通常タップ、横スワイプ、reorder detectorを付け、CloseIconButtonには付けない。`ReorderableItem`とスワイプoffsetはカード全体へ適用するため、ContentAreaから開始してもcloseボタンを含むカード全体が移動する。

CloseIconButtonは`Alignment.TopEnd`と必要な`zIndex`で配置し、ContentAreaはMaterialの拡張タッチ領域を含む右余白を確保する。固定タブのpin表示も同じtrailing領域へ置く。メニューアンカーにはContentAreaではなくカード全体のwindow boundsを使用する。

### 5. 同じPopupをPreviewとOpenで再利用する

`AnchoredOverlayMenu`へ`focusable`、`dismissOnBackPress`、`dismissOnClickOutside`、`interactive`を追加する。PreviewとOpenは同じComposableとPopupを`expanded = true`のまま維持し、propertiesだけを更新する。

- Preview: `focusable=false`、back/outside dismissなし、項目のclick、ripple、accessibility actionなし。
- Open: 現行どおりfocusableで、back/outside dismissと項目操作あり。

AndroidはACTION_DOWN時にtouch対象windowを決めるため、長押し途中で非focusable Popupを追加しても進行中のMOVE/UPは元カード側へ継続する。PoCで端末差が確認された場合だけinline Previewを再検討する。メニュー項目の見た目と文言は共通Composableへ抽出し、二重実装しない。

### 6. ドラッグ中だけViewModelがkey順序draftを持つ

`TabListUiState`へ`MenuState`とnullableな`ReorderDraft(originalOrder, currentOrder)`を追加する。draftは`BoardTabInfo`または`ThreadTabInfo`を保持せずstable keyだけを保持する。`TabScreenContent`は最新のStoreリストをkeyで索引化し、draft順に並べ直す。Storeにだけ現れた新規keyは末尾へ加え、Storeから消えたkeyはdraftから除く。

`onMove`はViewModelの`currentOrder`だけを更新し、DB writeを行わない。drag cancelまたは画面破棄では`originalOrder`へ戻してdraftを破棄する。正常終了では最終key順序を`TabSessionStore`へ渡す。

### 7. ドロップ後は既存Coordinatorのpending projectionへ引き継ぐ

`TabSessionStore`へ板用・スレッド用のreorder APIを追加する。`BoardTabsCoordinator.Operation`と`ThreadTabsCoordinator`のmutation/pending型へReorderを追加し、stable key順を投影するprimitiveを`TabProjectionPrimitives.kt`へ追加する。

Controllerはpending reorderをstateへ登録して投影順を公開してからrepository writeを開始する。登録完了後にViewModel draftを破棄するため、表示はdraftからCoordinator projectionへ連続して引き継がれる。Roomのbaselineより新しいsnapshotで期待する残存key順を確認してSuccessを返す。Failure時はpendingを除去し、canonical順へ戻す。

後続addは投影順の末尾、後続deleteは該当key除外としてfoldする。pin、info、scrollは順序を変えない。reorder同士の連続操作は後続の最終順序を有効とし、先行結果を既存supersession規則に従って終端させる。

### 8. `sortOrder`だけを全件再採番する

`OpenBoardTabDao`と`OpenThreadTabDao`へkey単位の`sortOrder`更新APIを追加し、`TabsRepository`にreorder専用メソッドを追加する。既存の`saveOpenBoardTabs`または`replaceOpenThreadTabsForBulkOperation`は集合置換と`deleteNotIn`を含むため使用しない。

repositoryは`DatabaseWriteGate`と`db.withTransaction`内でDBの現在key順を読み、次の規則で最終順を作る。

1. 要求順に含まれ、DBにも存在するkeyを要求順で採用する。
2. DBにのみ存在するkeyをDB上の相対順のまま末尾へ追加する。
3. DBに存在しない要求keyを無視する。
4. 最終順へ`0..n-1`を割り当て、`sortOrder`列だけを更新する。

単一巨大CASE文はSQLite bind上限を超え得るため使用しない。DAOの対象行updateを同一transaction内で反復する実装を第一候補とし、1,252件テストで許容時間を確認する。性能が受入基準を満たさない場合のみ安全なchunk方式へ変更する。

### 9. アニメーションとアクセシビリティを既存契約へ統合する

Reorderableのplacement animationは`itemsIndexed`直下の`ReorderableItem`へ適用し、並び替え中のLazy item位置変更を補間する。`RemovableTabList`の削除`AnimatedVisibility`と同じitemへ重複適用せず、削除用表示とspacingは`ReorderableItem`のcontent内に置く。削除中key、飛び出し中カード、検索結果ではreorderを無効にする。

カードには「上へ移動」「下へ移動」のcustom accessibility actionを追加し、境界で不可能な方向を成功扱いにしない。通常タップのfocus、keyboard/DPAD、ripple、TalkBack semanticsは`clickable`で維持する。

## Implementation Contract

1. 現行の失敗を再現するgesture統合testを追加してからDetectorを修正し、同じtestを接続端末またはemulatorで合格させるまでgesture修正を完了扱いにしない。
2. `TabListCard`のclose/pin trailing領域へreorderまたはswipe detectorを付けない。
3. pointer ID、座標、経過時間、touch slop、drag offsetを`TabListUiState`へ保存しない。
4. `ReorderDraft`はstable keyだけを保持し、drag中の`onMove`からDB writeを呼ばない。
5. 正常終了した順序は`TabSessionStore`からCoordinatorへ渡し、Controllerがpendingを登録した後にdraftを破棄する。
6. reorder persistenceでは`upsertAll`、`deleteNotIn`、Entity全体の置換、thread state保存、GCを呼ばない。
7. DB key集合との差分で行を削除せず、DBにのみ存在するkeyを末尾へ維持する。
8. 既存`boardUrl`と`ThreadId.value`のstable key契約を変更しない。
9. 新規class、interface、non-trivial functionにはリポジトリのKDoc規則を適用し、30行を超えるfunctionはセクションコメントで分割する。
10. 実装完了時にapp buildとunit testを必ず成功させ、関連instrumented testも実行する。
11. 長押し前のpointer changeをreorder側でconsumeせず、長押し成立後はMain passでdelta取得後に即consumeする。
12. Swipe用`pointerInput` Modifierをactive gesture中に付け外しせず、状態ゲートは`rememberUpdatedState`でDetector内から読む。無効化時はoffsetを復帰して撤退する。
13. 横スワイプDetectorはconsume済みchangeを処理せず、`isDragging`中は起動しない。
14. `LazyColumn.userScrollEnabled`をgesture stateで切り替えず、reorder側のconsumeでユーザーscrollと調停する。edge autoscrollは継続する。
15. post-long-pressのpointer ID、累積移動量、slop判定はDetector内に閉じ、`TabListUiState`またはViewModelへ追加しない。

## Error Cases and Compatibility

- Reorderable 3.1.0とCompose UI 1.10.3の依存解決またはruntime互換性に問題がある場合、バージョンを推測で変更せず計画を更新する。
- pointer cancel、画面破棄、drag対象key消失では保存せずdraftを破棄する。
- repository FailureではCoordinator pendingを除去し、canonical順へ戻す。
- Room確認前に新規keyが増えた場合は末尾へ維持し、削除済みkeyは確認対象から除く。
- Popup properties更新でpointer継続または表示位置が壊れる端末差が確認された場合、inline Previewへのfallbackを別design更新として扱う。
- Room schemaは変更しないためmigrationは追加しない。バックアップはDB snapshot内の`sortOrder`をそのまま保持する。

## Testing Strategy

- Compose UI/実機PoC: 実際の`TabListCard`と`RemovableTabList`へ`performTouchInput`でDOWN、長押し時刻、slop未満MOVE、slop超過MOVE、UPを送る。tap、Preview→Open、Preview→drag、長押し前の横スワイプと縦スクロール、drag中のスワイプ不発火、close除外、UP時click抑止、Popup properties更新を個別に確認する。
- Gesture callback順序: Previewでは`onLongPress`だけ、slop超過では`onDragStart`を1回、正常終了では`onDragEnd`と`onDragFinished`を各1回、cancelでは`onDragCancel`と`onDragCancelled`を各1回通知する。MenuOpen経路ではreorder callbackと通常`onClick`を通知しない。
- Unit: key順move、Store最新情報とのmerge、cancel rollback、ViewModelからCoordinatorへのhandoff、Board/Threadのpending projection、confirmation、Failure、連続reorder、add/delete競合。
- Room instrumented: `sortOrder`以外が不変、全key連番、DBのみkey維持、削除済みkey無視、transaction rollback、1,252件性能。
- Accessibility: 上下移動action、境界、TalkBackの通常タップとメニュー、Preview項目の非操作性。
- 回帰: 既存の削除アニメーション、スワイプしきい値、固定タブ、検索、bulk close、選択維持。

## Risks / Trade-offs

- [複数gesture detectorがPointerEventを競合する] → 長押し前は消費せず、成立後は内側reorderHandleがMain passで各changeを即consumeする。外側スワイプは常設pointer nodeの内部ゲートと`isConsumed`で撤退し、drag中は無効化する。LazyColumnのuser scrollは値を切り替えずreorder consumeで調停する。
- [長押し成立時にSwipe親nodeをdetachしてreorder子nodeへcancelが伝播する] → Swipe pointerInputを常設し、状態変化ではDetector内ゲートだけを更新する。active swipeを無効化する場合はoffsetをspring-backして通常終了する。
- [長押し後のupで通常clickが誤発火する] → まずMain passでUPをconsumeし、実機testで誤発火した場合だけUP取得をInitial passへ変更する。それでも失敗する場合だけ局所抑止フラグを追加する。
- [slop超過時にカードがジャンプする] → 最初の`onDrag`へ累積量ではなくslop超過分だけを渡す。
- [Popup更新でpointerまたは表示が途切れる] → 同じPopupを維持してpropertiesだけ更新し、端末差が出た場合にinline fallbackを計画し直す。
- [ViewModel draftとCoordinator projectionのhandoffで一瞬戻る] → pending登録後にdraftを破棄し、同じstable key順を双方で共有する。
- [全件再採番が大規模一覧で遅い] → 1,252件のinstrumented performance testを必須とし、不合格時だけchunk方式を採用する。
- [ドラッグ中のadd/deleteでkey集合がずれる] → transaction時のDB集合とmergeし、行削除を行わない。

## Migration Plan

1. 現行実装でPreviewからdragへ移行できないgesture統合testを追加し、修正前に失敗を確認する。
2. `SlevoTabDragGestureDetector`のpost-long-press区間をMain pass所有ループへ置き換え、横スワイプDetectorを常設pointer nodeの内部ゲートへ変更し、drag中無効化とoffset復帰を追加する。
3. `RemovableTabList`の`userScrollEnabled`を固定し、長押し後はreorder consumeでscrollと調停する。同じgesture統合testを接続端末またはemulatorで実行し、tap、menu、reorder、swipe、scroll、closeの全経路を確認する。
4. 既存のUI状態、Coordinator handoff、Room永続化、アクセシビリティの未完了testを実行する。
5. unit testとapp buildをCIで再確認し、instrumented testの端末・API level・結果を`tasks.md`へ記録する。

問題が発生した場合は依存追加とreorder導線を戻せば、既存`sortOrder`およびRoom schemaに影響を残さずロールバックできる。
