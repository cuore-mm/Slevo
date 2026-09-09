## MODIFIED Requirements

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
