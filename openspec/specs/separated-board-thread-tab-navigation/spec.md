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
システムは板/スレッドを開く入口に応じて、タブ登録・タブ選択・画面遷移を区別して実行しなければならないMUST。BoardからThreadを開く操作は履歴に積み、同種別タブ切り替えは履歴に積んではならないMUST NOT。ThreadからBoardを選ぶ操作は、直前のBoard画面があればpopし、なければ現在ThreadをreplaceしなければならないMUST。

#### Scenario: 登録板一覧から板を開く
- **WHEN** ユーザーが登録板一覧から板を選択する
- **THEN** システムは板タブを登録・選択し、板画面種別へ遷移する

#### Scenario: スレッドリンクからスレッドを開く
- **WHEN** ユーザーが板画面またはスレッド画面内でスレッドリンクを選択する
- **THEN** システムはスレッドタブを登録・選択し、スレッド画面 route を履歴に積む

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
