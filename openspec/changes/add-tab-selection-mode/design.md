## Context

`TabListUiState`は検索、長押し対象、メニュー、削除アニメーション、並び替えdraftを保持するが、複数選択状態を持たない。現在の`selectedBoardTab`／`selectedThreadTab`は長押しoverlayの単一対象であり、複数選択へ流用すると長押しメニューの表示段階や復帰アニメーションと競合する。

タブ一覧の表示データは`TabSessionStore.openBoardTabs`／`openThreadTabs`から供給され、`TabScreenContent`が検索と並び替えdraftを反映して`TabsPagerContent`以下へ渡す。単体固定はCoordinatorのtoggle API、一括削除はBoard／Threadそれぞれの専用bulk commandから`TabsRepository`のtransactional deleteへ流れる。複数対象の固定状態を明示値へ揃えるbulk APIは存在しない。

上部操作UIは`TabListTopControls`の外側で検索欄をスライド＋フェード表示し、非検索時の操作列をフェード表示する。検索UIの既存アニメーションは維持する。通常／選択操作の切り替えだけをフェードにする。

## Goals / Non-Goals

**Goals:**

- 長押しoverlay状態とは独立した、表示中ページ単位の複数選択状態を追加する。
- 検索と選択を共存させ、常に選択を下位、検索を上位としてBack順序を決定する。
- 選択対象の一括削除と固定状態の一括設定を、既存のprojection・retained Store・Room canonical state契約に統合する。
- 通常操作の回帰を防ぐため、通常、検索、選択、選択＋検索の4状態を明示的に描画・テストする。

**Non-Goals:**

- 板ページとスレッドページをまたぐ選択、全選択、選択反転は追加しない。
- 選択状態をプロセス再生成やアプリ再起動後に復元しない。
- 通常時の「全てのタブを閉じる」の未固定タブ限定契約は変更しない。
- 検索クエリ、検索結果判定、検索欄のスライド＋フェードアニメーションは変更しない。
- 固定状態によるタブ順の変更は行わない。

## Decisions

### 1. 長押し対象と複数選択を別のUI stateとして保持する

`TabListUiState`へ次を追加する。

- `selectionModePage: TabPage?`
- `selectedBoardTabKeys: Set<String>`（正規化済み`boardUrl`）
- `selectedThreadTabIds: Set<ThreadId>`
- 選択モード用メニューの表示状態とアンカーbounds

`selectionModePage != null`を選択モード判定とする。既存の`selectedBoardTab`、`selectedThreadTab`、`selectedTabBounds`、`tabActionMenuMode`は長押しoverlay専用のまま維持する。これにより、長押しメニューから選択開始するときは対象stable keyを複数選択集合へコピーしてから長押しoverlayを閉じられる。

選択集合に表示モデル全体を保持する案は採用しない。Room更新で`isPinned`やタイトルが変わっても選択同一性を保ち、既存のselection source of truthと一致させるためである。

### 2. ViewModelで選択状態遷移を一元管理する

`TabListViewModel`へ、少なくとも次の責務を持つ明示的なイベント関数を追加する。

- 右上メニューから0件で選択開始
- 長押し対象を初期選択して選択開始
- 板／スレッドstable keyの選択切り替え
- 選択終了と集合クリア
- canonical一覧に存在しないkeyの除去
- 選択メニュー表示／非表示
- 選択スナップショットの一括クローズ
- 選択スナップショットの一括固定状態設定

検索開始時は長押しoverlay、通常時その他メニュー、並び替えdraftを閉じるが、複数選択集合を消さない。検索終了時も複数選択を維持する。`BackHandler`は検索用を優先し、`isSearchMode`がfalseになった次のBackで選択終了を処理する。検索欄自身の戻る操作も同じ`closeSearchMode()`へ集約する。

選択中はページ切替UIを表示せず、Pagerの横スワイプも既存どおり無効なため、`selectionModePage`は開始ページに固定される。予期しないページ変更を受けた場合は、異なるページに選択集合を持ち越さず選択モードを終了する。

### 3. canonical一覧との差分で選択集合を収束させる

`TabScreenContent`が購読するBoard／Threadの公開一覧更新をViewModelへ通知し、選択集合と現在存在するstable keyの積集合を取る。検索済みの表示リストではなく、検索前の公開一覧を基準にする。したがって検索クエリで一時的に非表示になったタブは選択数と操作対象へ残り、削除projectionから消えたタブだけが選択集合から除去される。

一括操作はクリック受理時に、該当ページの公開一覧順で選択keyに一致する`TabInfo`をスナップショット化する。以後の検索変更、pin変更、新規タブ追加で対象を再計算しない。

### 4. カードは選択入力と表示を明示的な引数で受け取る

`TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList`、`RemovableTabList`、`TabListCard`へ、選択モード、選択済み、選択切り替えcallbackを伝播する。`TabListCard`は選択モード中に次を行う。

- カードclickを通常遷移ではなく選択切り替えへ接続する。
- `primaryContainer`と通常色を短いcolor animationで切り替える。
- 右端に空丸／チェックを表示し、固定タブではその左に表示専用ピンを併記する。
- close button、長押しpointer input、reorder handle、横スワイプoffset処理を構成しない、またはenabled=falseにする。

親でcallbackを空実装にするだけの案は採用しない。pointer detector自体を無効化し、長押しhaptic、カード移動、削除しきい値判定が起きないことを保証するためである。

空丸、チェック、ピンにはcontent descriptionまたはカードsemanticsを付け、選択済み状態はComposeの`selected` semanticsでも公開する。通常カードの固定ピン／閉じる表示は変更しない。

### 5. 検索アニメーションを外側に残して選択固有操作だけをフェードする

`TabListTopControls`の`isSearchMode`用`AnimatedVisibility`（スライド＋フェード）はそのまま残す。`!isSearchMode`側は固定幅の`Box`にし、検索／その他ボタンの共通Rowを通常モードと選択モードで同じ位置に配置する。選択モード固有の戻るボタンだけを`fadeIn`／`fadeOut`で切り替える。

選択モードでは左にBackを表示し、右の検索／その他Rowは通常モードと共通利用する。その他ボタンは通常モードでは有効、選択モードでは選択0件の場合に`enabled=false`とし、無効状態を色／alphaとsemanticsで判別できるようにする。共通Rowのその他ボタンboundsを両モードのアンカーに利用し、メニュー種別はstateで分岐する。

`TabListBottomControls`は次の表示条件へ変更する。

- 通常操作群: `!isSearchMode && !isSelectionMode`
- 選択数表示: `isSelectionMode`
- 検索のみ: どちらも非表示

選択＋検索では下位の選択状態を示す「n個選択中」を維持する。通常操作群と選択数表示の切り替えはスライドなしのフェードとし、選択数表示にclick処理を持たせない。

### 6. 選択アクションメニューは通常メニューと役割を分ける

通常時の右上メニューには、既存の「全てのタブを閉じる」に「タブを選択」を追加する。長押しメニューには既存項目の末尾へ「タブを選択」を追加する。

選択モードの右上メニューは次の2項目だけを表示する。

- 「タブを閉じる」（破壊的操作色）
- 選択対象がすべて固定済みなら「タブの固定を解除」、それ以外は「タブを固定」

メニュー文言は表示時点の公開一覧と選択keyから導出するが、実行対象は押下時に再度スナップショット化する。対象0件ならメニューを開かず、操作関数でもguardする。メニュー選択後はメニューだけを閉じ、選択モードは終了しない。

### 7. 選択クローズは既存bulk delete経路を再利用する

`TabListViewModel`は選択対象を一覧順の`List<BoardTabInfo>`または`List<ThreadTabInfo>`として`TabSessionStore`へ渡す。Storeは既存の一括削除遅延、holder一括dispose、Board／Thread Coordinatorのbulk deleteを使用する。通常時の全件クローズだけが事前に未固定をfilterし、選択クローズでは固定状態によるfilterを行わない。

対象を単体closeのloopへ展開する案は採用しない。既存の1 pending projection、選択補正、最大900件chunk、1 transaction、retained ownershipを維持するためである。

### 8. 一括固定は明示値を設定する専用bulk mutationとする

`TabSessionStore`、`BoardTabsCoordinator`、`ThreadTabsCoordinator`、`TabsRepository`へ、対象keyと`pinned: Boolean`を受けるbulk pin経路を追加する。Boardは既存のpending operation foldへBulkPinを追加し、Threadはmutation intent queueへBulkPinを追加する。projectionは対象だけの`isPinned`を1回の操作で明示値へ更新する。

Room DAOには対象ID集合の固定状態を更新するqueryを追加する。SQLite bind上限を避けるため最大900件にchunk化し、全chunkを1つの`DatabaseWriteGate` write permitとRoom transactionで実行する。対象外行、sortOrder、メタデータは変更しない。Thread側の非キャンセル例外は既存bulk deleteと同様にStore root coroutineへ伝播させずログ記録し、Coordinatorはpendingを除去してcanonical stateへ戻す。

既存`togglePin*`を件数分呼ぶ案は採用しない。混在状態を一時表示すること、N回のDB書き込み、途中失敗、連打時の反転競合を避け、選択全体を指定値へ原子的に収束させるためである。

## Implementation Contract

1. `TabListUiState`の長押し単一対象フィールドを削除・改名せず、複数選択フィールドを別に追加する。
2. `TabListViewModel.enterSearchMode()`／`closeSearchMode()`は複数選択集合を変更しない。長押しoverlayとreorder draftの解除は維持する。
3. 選択対象の照合は検索結果ではなく`TabSessionStore`の公開全一覧を使い、Boardは`boardUrl`、Threadは`ThreadId`で行う。
4. `TabListTopControls`の検索欄に対する既存スライド＋フェード指定を変更しない。検索／その他ボタンは通常モードと選択モードで位置を変えず、選択モード固有の戻るボタンだけにフェード切り替えを適用する。
5. 選択モード中の固定カードでは、ピンを状態アイコンの左に同時表示する。ピンで空丸／チェックを置換しない。
6. 選択中の一括クローズでは固定タブを除外しない。通常時の「全てのタブを閉じる」は引き続き未固定だけを対象にする。
7. 一括固定／解除はtoggleのloopで実装せず、target pinned値を持つbulk commandとtransactional repository APIを追加する。
8. 一括アクション後は選択モード終了関数を呼ばない。削除されたkeyは公開一覧との差分で除去し、固定変更対象keyは維持する。
9. 新規class／interfaceと非自明関数にはリポジトリ規約どおりKDocを付け、30行を超える関数は処理区分コメントで分割する。

## Error Cases and Compatibility

- 一括操作受付直前に対象が消えた場合は存在する対象だけをスナップショット化し、0件ならno-opとしてメニューだけ閉じる。
- bulk delete／bulk pinが失敗した場合はCoordinatorのpending projectionを除去し、Room canonical stateへ戻す。選択集合はcanonical一覧との積集合へ再収束させる。
- 選択モード中に画面のタブ一覧が空になっても、選択モード自体は明示的なBackまで維持し、選択数0件と無効なその他ボタンを表示する。
- UI stateと新規APIはアプリ内部だけの変更であり、DB schema migrationや外部API互換性への影響はない。
- 通常時の単体close、単体pin、長押しpreview、reorder、検索アニメーションは既存挙動を維持する。

## Testing Strategy

- `TabListViewModelTest`: 0件／長押し初期選択、toggle、検索との共存、Back順序、canonical pruning、操作後のモード維持、選択スナップショットを検証する。
- `TabSessionStoreTest`: 固定を含む選択bulk close、対象holderだけのdispose、caller cancellation後の継続、bulk pin委譲を検証する。
- `BoardTabsCoordinatorTest`／`ThreadTabsCoordinatorTest`: mixed pin状態の明示値一括更新、1 pending projection、対象外保持、失敗時rollback、連続command収束を検証する。
- Repository／DAO test: 900件超chunk、transaction rollback、対象外のpin／sortOrder保持を検証する。
- Compose instrumented test: メニュー項目、0件時disabled、カードtap、背景色、ピン＋状態アイコン、gesture無効化、4つのモード表示、検索アニメーション維持、フェード切り替え、accessibility semanticsを検証する。
- 実装完了時に`./gradlew testDebugUnitTest`と`./gradlew assembleDebug`を実行し、利用可能なemulatorがある環境では対象Compose instrumented testも実行する。

## Migration Plan

DB schemaと永続データの移行は不要である。UI state、Coordinator command、Repository APIを同一リリースで追加する。問題発生時は選択開始導線と新規bulk pin経路をまとめて戻せば、既存の単体操作と通常時一括クローズへ復帰できる。
