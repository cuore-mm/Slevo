## ADDED Requirements

### Requirement: 専用bulk delete commandの状態遷移
BoardおよびThreadのタブControllerは、複数の対象keyを1つのpending commandとして登録し、全対象不在のcanonical snapshotを確認してからcommandを完了しなければならない（SHALL）。

#### Scenario: bulk commandを登録する
- **WHEN** Controllerが順序付きの複数対象keyを受理する
- **THEN** Controllerは対象を1つのpending entryへ登録し、集合除去を1回projectionへ適用する

#### Scenario: canonical確認を待つ
- **WHEN** Repository書き込みは完了したがcanonical snapshotに対象keyが残っている
- **THEN** Controllerはbulk pendingを維持し、対象をprojectionへ再表示しない

#### Scenario: 全対象不在を確認する
- **WHEN** baselineより新しいcanonical snapshotで全対象keyが不在になる
- **THEN** Controllerはbulk pendingを完了し、計算済みの最終選択を公開する

#### Scenario: bulk書き込みが失敗する
- **WHEN** bulk Repository操作が失敗またはcancelされる
- **THEN** Controllerはbulk pending全体を除去し、部分成功を公開せずcanonical stateへ収束する

### Requirement: bulk deleteと同一key操作の順序を維持する
Controllerはbulk対象集合とEnsure、Pin、Info、単体Deleteの競合を受理順で解決し、単一keyの競合だけでbulk command全体をsupersedeしてはならない（MUST NOT）。

#### Scenario: 先行pinを対象判定へ反映する
- **WHEN** pin操作がbulk受理前にprojectionへ反映される
- **THEN** Storeは固定済みkeyをbulk対象へ含めない

#### Scenario: bulk後に同じkeyをEnsureする
- **WHEN** bulk受理後に対象keyへのEnsureを受理する
- **THEN** Controllerはbulkを完了した後にEnsureを処理し、再作成されたタブへ収束する

#### Scenario: Thread bulkをbarrierとして扱う
- **WHEN** Thread bulk intentの処理中に後続mutation intentがqueueへ入る
- **THEN** Thread Controllerはbulkのcanonical確認完了後に後続intentを処理する

#### Scenario: teardownでwaiterを解放する
- **WHEN** Controller lifetimeがbulk pending中に終了する
- **THEN** Controllerはbulk completionをFailureで完了し、waiterをハングさせない
