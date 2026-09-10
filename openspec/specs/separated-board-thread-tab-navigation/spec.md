# separated-board-thread-tab-navigation Specification

## Purpose
TBD - created by archiving change refactor-separated-board-thread-tab-navigation. Update Purpose after archive.

## Requirements

### Requirement: 板画面とスレッド画面の route 分離維持
システムは板画面とスレッド画面を異なる navigation destination として維持しなければならないMUST。`AppRoute.Board` / `AppRoute.Thread` の既存引数構造を維持しなければならないMUST。板画面からスレッドを開く操作はスレッド画面 route を back stack に積み、戻る操作で直前の板画面へ戻れるようにしなければならないMUST。

#### Scenario: 板画面からスレッドを開いて戻る
- **WHEN** ユーザーが板画面でスレッドを選択し、表示されたスレッド画面で戻る操作を行う
- **THEN** システムは直前の板画面を表示する

#### Scenario: 板とスレッドの画面種別を分離する
- **WHEN** NavController が現在 destination を判定する
- **THEN** システムは板画面とスレッド画面を同一 route ではなく別々の画面種別として扱う

#### Scenario: route 引数を placeholder として保持する
- **WHEN** 板画面またはスレッド画面の初期データ読み込み中である
- **THEN** システムは既存 route 引数に含まれる板名、URL、スレッドタイトルなどを読み込み中 placeholder として利用できる

#### Scenario: route 引数で selected key を常時上書きしない
- **WHEN** ユーザーが板画面で横スワイプにより別板タブを選択した後に再コンポーズが発生する
- **THEN** システムは route 引数の板情報ではなく TabSessionStore の選択中板タブ key に基づいて表示タブを維持する

### Requirement: 同種別タブ切り替えと別種別遷移の履歴を区別する
システムはタブ一覧シート、フルスクリーンタブ一覧、横スワイプによる同種別タブの切り替えで、不要な navigation back stack を積んではならないMUST NOT。別種別タブの選択は画面種別の遷移として扱い、現在の画面種別と直前のback stack entryに応じたpush、pop、replaceを行わなければならないMUST。

#### Scenario: タブ一覧シートで同種別タブを選ぶ
- **WHEN** ユーザーが板画面内のタブ一覧シートで別の板タブを選択する
- **THEN** システムは板画面 route を追加で積まず、選択中の板タブだけを更新してシートを閉じる

#### Scenario: Board画面のタブ一覧シートでThreadタブを選ぶ
- **WHEN** ユーザーが板画面内のタブ一覧シートでスレッドタブを選択する
- **THEN** システムは選択中のスレッドタブを更新し、現在の板画面をback stackに残してスレッド画面 routeをpushする
- **AND** 戻る操作で元の板画面へ戻る

#### Scenario: Thread画面のタブ一覧シートでBoardタブを選ぶ
- **WHEN** ユーザーがスレッド画面内のタブ一覧シートで板タブを選択する
- **THEN** システムは選択中の板タブを更新する
- **AND** 直前のback stack entryが板画面なら現在のスレッド画面をpopしてその板画面へ戻り、そうでなければ現在のスレッド画面を選択済みBoard routeへreplaceする
- **AND** 破棄したスレッド画面をBackで再表示しない

#### Scenario: 横スワイプでタブを切り替える
- **WHEN** ユーザーが板またはスレッド画面の Pager を横スワイプして別タブへ移動する
- **THEN** システムは NavController の back stack を変更せず、対応する選択中タブだけを更新する

### Requirement: 入口ごとに履歴操作を区別する
システムは板/スレッドを開く入口に応じて、タブ登録・タブ選択・画面遷移を区別して実行しなければならないMUST。BoardからThreadを開く操作は履歴に積み、同種別タブ切り替えは新規タブの登録を伴う場合も履歴に積んではならないMUST NOT。ThreadからBoardを選ぶ操作は、直前のBoard画面があればpopし、なければ現在ThreadをreplaceしなければならないMUST。
ThreadからBoardへの画面遷移は、popまたはreplaceの操作方式にかかわらず、現在Threadを右方向へ退出させ、対象Boardを左方向から復帰させる既存pop準拠のslide-only transition（300ms）を適用しなければならないMUST。BoardからThreadへの遷移方向と、Board/Thread以外のdestinationのtransitionは変更してはならないMUST NOT。

#### Scenario: 登録板一覧から板を開く
- **WHEN** ユーザーが登録板一覧から板を選択する
- **THEN** システムは板タブを登録・選択し、板画面種別へ遷移する

#### Scenario: スレッドリンクからスレッドを開く
- **WHEN** ユーザーが板画面またはスレッド画面内でスレッドリンクを選択する
- **THEN** システムはスレッドタブを登録・選択する
- **AND** 板画面から選択した場合だけスレッド画面 route を履歴に積み、スレッド画面から選択した場合は現在のrouteと履歴を維持する

#### Scenario: スレッド画面のスレッドリンクからスレッドを開く
- **WHEN** ユーザーがスレッド画面内で別スレッドへのリンクを選択する
- **THEN** システムは対象スレッドタブを登録・選択し、現在のスレッド画面 route と履歴を維持する

#### Scenario: スレッドから板へ戻る
- **WHEN** ユーザーがスレッド画面の下部ボタンまたはThreadInfoBottomSheetから板を選択し、直前の板が存在するためpopする、または現在のスレッドを板へreplaceする
- **THEN** システムはスレッドを右方向へ退出させ、板を左方向から復帰させる
- **AND** transitionはfadeを含めず、300msのslide-onlyで実行する

### Requirement: 下部コントローラーから別画面種別へ遷移する
システムは板画面のタイトルカード右側にアイコンと「スレ」ラベルのボタンを、スレッド画面のタイトルカード左側にアイコンと「板」ラベルのボタンを表示しなければならないMUST。Boardの「スレ」は遷移先routeをback stackへ積み、Threadの「板」は直前のback stack entryがBoardなら現在ThreadをpopしてそのBoard画面へ戻し、Boardがなければ現在Threadを選択済みBoard routeへ置換しなければならないMUST。

#### Scenario: Boardから選択済みThreadを開く
- **WHEN** スレッドタブの選択状態が有効な状態で、ユーザーが板画面の「スレ」ボタンを選択する
- **THEN** システムは現在選択済みのスレッドタブを登録・選択済みとして確認し、そのスレッド画面 route を back stack に積む
- **AND** 戻る操作で元の板画面へ戻れる

#### Scenario: 選択済みThreadを解決できない
- **WHEN** スレッドタブの状態が初回読込中、0件、または選択タブの一時的不在である
- **THEN** システムは板画面の「スレ」ボタンから不完全なスレッド route へ遷移しない

#### Scenario: Threadから背後のBoardへ戻る
- **WHEN** 板タブの選択状態が有効な状態で、ユーザーがスレッド画面の「板」ボタンを選択する
- **AND** 現在スレッド画面の直前のback stack entryが板画面である
- **THEN** システムは現在選択済みの板タブを登録・選択済みとして確認し、現在のスレッド画面をpopして背後の板画面へ戻る
- **AND** 背後の板画面のdestinationを新しいrouteで置き換えない

#### Scenario: 背後にBoardがないThreadから選択済みBoardへ遷移する
- **WHEN** 板タブの選択状態が有効で、スレッド画面の直前のback stack entryが板画面ではない状態でユーザーが「板」ボタンを選択する
- **THEN** システムは現在のスレッド画面をback stackから破棄し、選択済みBoard routeへ置換する
- **AND** 戻る操作で破棄したスレッド画面を再表示しない

#### Scenario: 選択済みBoardを解決できない
- **WHEN** 板タブの状態が初回読込中、0件、または選択タブの一時的不在である
- **THEN** システムはスレッド画面の「板」ボタンから不完全な板routeへ遷移せず、現在のスレッド画面を維持する

#### Scenario: 別画面種別ボタンを表示する
- **WHEN** 板画面またはスレッド画面の下部コントローラーを表示する
- **THEN** システムはアイコンと短い可視ラベルをタイトルカード外の固定位置に表示し、画面種別を示す読み上げ可能な説明を提供する

### Requirement: 下部コントローラーの画面種別要素をShared Boundsで接続する
システムは下部コントローラーからBoard画面とThread画面を切り替えるとき、同じタブidentityを表すタイトルカードと画面種別ボタンをShared Boundsで接続し、位置とサイズを連続的に変化させなければならないMUST。Boardを表す要素とThreadを表す要素は異なる共有キー種別として扱い、異なるタブidentity同士を接続してはならないMUST NOT。

#### Scenario: BoardからThreadを開く
- **WHEN** settle済みBoardタイトルカードと有効なThreadボタンが表示され、ユーザーがThreadボタンから選択済みThread画面を開く
- **THEN** システムはThreadボタンと同じスレッドタブidentityを持つThreadタイトルカードをShared Boundsで接続する
- **AND** Boardタイトルカードと遷移先に表示されるBoardボタンが同じ板タブidentityを持つ場合は、その2要素もShared Boundsで接続する

#### Scenario: ThreadからBoardへ戻るまたは置換する
- **WHEN** settle済みThreadタイトルカードと有効なBoardボタンが表示され、ユーザーがBoardボタンから選択済みBoard画面へ遷移する
- **THEN** システムはBoardボタンと同じ板タブidentityを持つBoardタイトルカードをShared Boundsで接続する
- **AND** Threadタイトルカードと遷移先に表示されるThreadボタンが同じスレッドタブidentityを持つ場合は、その2要素もShared Boundsで接続する

#### Scenario: 対応identityが遷移先に存在しない
- **WHEN** Navigation時のroute正規化、タブ同期中、または対象要素の非表示によって遷移元と遷移先に同一の共有キーが存在しない
- **THEN** システムは異なるidentityの要素を代替接続しない
- **AND** 既存の通常画面遷移を継続する

### Requirement: Shared Transitionを画面種別切替の確定要素に限定する
システムはBoard/Thread画面種別切替のShared Transition対象を、Pagerがsettleして横ドラッグされていないタイトルカード、解決済み遷移先を持つ画面種別ボタン、および現在の下段ツール群を表す`BottomActionsRow`全体に限定しなければならないMUST。同種タブPagerのドラッグ、検索表示、下部コントローラーの展開・縮退の状態管理をShared Transitionのために変更してはならないMUST NOT。下段ツール群のShared Boundsは個別アイコンを対応付けず、Board/Threadの行全体を共通keyで接続しなければならないMUST。

#### Scenario: 同種タブPagerをドラッグする
- **WHEN** ユーザーがBoardまたはThreadの下部コントローラーを横ドラッグして同種タブを移動する
- **THEN** システムはドラッグ中のタイトルカードをBoard/Thread画面種別切替のShared Transition対象にしない
- **AND** タイトルカード、固定ツール群、選択タブ更新、およびsettle処理は従来どおり動作する

#### Scenario: 遷移先タブを解決できない
- **WHEN** Board画面のThreadボタンまたはThread画面のBoardボタンに対応する選択済みタブを解決できない
- **THEN** システムはそのボタンをShared Transition対象にせず、既存の無効状態とNavigation抑止を維持する

#### Scenario: 検索または縮退状態から画面種別を切り替える
- **WHEN** 検索状態または下部コントローラーの縮退状態でBoard/Thread画面種別切替が発生する
- **THEN** システムは既存の検索状態と展開・縮退状態の管理を変更せず、現在表示されている対象要素だけでShared Boundsの照合を行う

### Requirement: 既存Shared TransitionとNavigationを維持する
システムはBoard/ThreadコントローラーのShared Boundsを既存の共通Shared Transition領域内で実行しなければならないMUST。BoardとThreadを別navigation destinationとして維持し、既存のpush、pop、replaceを変更してはならないMUST。Board↔Thread間のNavigationは既存の方向と時間を維持したslide-onlyとし、それ以外のdestination間NavigationおよびImageViewerのShared Transitionの照合・描画設定を変更してはならないMUST NOT。

#### Scenario: BoardとThreadを切り替える
- **WHEN** 下部コントローラーからBoard画面とThread画面を切り替える
- **THEN** システムは既存のrouteとback stack操作を実行しながら、画面全体ではslide-onlyのNavigationを実行する
- **AND** 対応するタイトルカード、画面種別ボタン、および下段ツール群のShared Boundsを実行する

#### Scenario: Board/Thread以外のdestinationへ遷移する
- **WHEN** BoardまたはThreadからImageViewerを含むBoard/Thread以外のdestinationへ遷移する
- **THEN** システムはBoard↔Thread専用のslide-onlyを適用せず、既存のdestination別Navigation transitionを使用する

#### Scenario: ThreadからImageViewerを開いて戻る
- **WHEN** ユーザーがThread画面の画像からImageViewerを開き、その後Thread画面へ戻る
- **THEN** システムは既存の画像Shared Transitionのキー、対象判定、overlay設定、およびNavigation transitionを従来どおり使用する
