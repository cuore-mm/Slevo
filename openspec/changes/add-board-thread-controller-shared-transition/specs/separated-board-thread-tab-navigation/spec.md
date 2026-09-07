## ADDED Requirements

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
