## MODIFIED Requirements

### Requirement: 既存Shared TransitionとNavigationを維持する
システムはBoard/ThreadコントローラーとTabs↔Board / Threadページ全体のShared Boundsを、既存の共通Shared Transition領域内で別の型付きkeyにより実行しなければならないMUST。Board、Thread、Tabsを別navigation destinationとして維持し、既存の最終back stack、contextual Tabs除去、およびTabs entry ID guardを変更してはならないMUST。Board↔Thread間のNavigationは既存の方向と時間を維持したslide-onlyとし、Tabs↔Board / Thread間では既存の横slideを使用せずページ全体のShared Boundsと短いfadeを使用しなければならないMUST。ImageViewerを含むそれ以外のdestination間NavigationおよびImageViewerのShared Transitionの照合・描画設定を変更してはならないMUST NOT。

#### Scenario: BoardとThreadを切り替える
- **WHEN** 下部コントローラーからBoard画面とThread画面を切り替える
- **THEN** システムは既存のrouteとback stack結果を維持しながら、画面全体ではslide-onlyのNavigationを実行する
- **AND** 対応するタイトルカード、画面種別ボタン、および下段ツール群の既存Shared Boundsを実行する

#### Scenario: TabsとBoardまたはThreadを切り替える
- **WHEN** 全画面TabsとBoardまたはThreadの間を遷移する
- **THEN** システムはTabs↔BBS間の横slideを実行せず、対応カードと現在表示ページ全体のShared Boundsを実行する
- **AND** Shared Boundsが成立しない場合は短いfadeで遷移を完了する

#### Scenario: contextual Tabsで同種タブを選択する
- **WHEN** BoardまたはThreadから開いたTabsで遷移元と同種のタブを選択する
- **THEN** システムはTabsを除去して既存destinationを再利用し、選択カードと選択後のsettle済み現在ページを1回のNavigation transitionで接続する

#### Scenario: contextual Tabsで別種タブを選択する
- **WHEN** BoardまたはThreadから開いたTabsで遷移元と異なる種別のタブを選択する
- **THEN** システムは既存と同じ最終back stackを作り、選択カードと最終destinationの現在表示ページを1回のNavigation transitionで接続する

#### Scenario: routeとsettle済みタブのidentityが異なる
- **WHEN** 同じBoardまたはThread destination内のPager操作により、navigation routeのidentityとsettle済み現在タブのidentityが異なる
- **THEN** システムはrouteではなくsettle済み現在タブのidentityをページ全体のShared Bounds照合に使用する

#### Scenario: Board/Thread以外のdestinationへ遷移する
- **WHEN** BoardまたはThreadからImageViewerを含むBoard / Thread / Tabs以外のdestinationへ遷移する
- **THEN** システムはBoard↔Thread専用slide-onlyとTabs↔BBS専用ページShared Boundsを適用せず、既存のdestination別Navigation transitionを使用する

#### Scenario: ThreadからImageViewerを開いて戻る
- **WHEN** ユーザーがThread画面の画像からImageViewerを開き、その後Thread画面へ戻る
- **THEN** システムは既存の画像Shared Transitionのキー、対象判定、overlay設定、およびNavigation transitionを従来どおり使用する
