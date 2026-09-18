## ADDED Requirements

### Requirement: 選択中stable keyを端末内に永続化する
システムは選択中の板タブとスレッドタブのstable keyを互いに独立した端末内状態として永続化し、アプリ再起動またはプロセス再生成後も復元しなければならないMUST。板タブは正規化済みboardUrl、スレッドタブはThreadId相当の一意識別子を保存し、page indexを選択状態として保存してはならないMUST NOT。選択タブの永続状態を既存のアプリ内バックアップJSONへ追加してはならないMUST NOT。

#### Scenario: 板タブの選択を再起動後に復元する
- **WHEN** ユーザーが登録済み板タブを選択した後にアプリを再起動し、同じ板タブがloaded一覧に存在する
- **THEN** システムは保存した正規化済みboardUrlを選択中板タブkeyとして復元する
- **AND** 復元した板タブに対応するcontentを最初のloaded表示として構成する

#### Scenario: スレッドタブの選択を再起動後に復元する
- **WHEN** ユーザーが登録済みスレッドタブを選択した後にアプリを再起動し、同じスレッドタブがloaded一覧に存在する
- **THEN** システムは保存したThreadId相当のkeyを選択中スレッドタブkeyとして復元する
- **AND** 復元したスレッドタブに対応するcontentを最初のloaded表示として構成する

#### Scenario: 板とスレッドの選択を独立して保存する
- **WHEN** ユーザーが板タブとスレッドタブをそれぞれ選択する
- **THEN** システムは両方のstable keyを独立して保存する
- **AND** 一方の選択変更で他方の保存keyを変更しない

#### Scenario: 選択タブ削除後の選択を保存する
- **WHEN** 選択中タブの削除がcanonical一覧で確定し、隣接タブが新しい選択として確定する
- **THEN** システムは新しい選択中stable keyを保存する

#### Scenario: 最後のタブ削除後に保存keyを消去する
- **WHEN** 最後の板タブまたはスレッドタブの削除がcanonical一覧で確定する
- **THEN** システムは対応する選択中keyをnullにし、保存済みkeyを消去する

#### Scenario: アプリ内バックアップから選択状態を除外する
- **WHEN** ユーザーがアプリ内バックアップを作成または別環境へ復元する
- **THEN** システムは板・スレッドの選択中stable keyをバックアップJSONへ出力または復元しない
- **AND** 既存のバックアップformat versionとtabs JSON構造を変更しない

## MODIFIED Requirements

### Requirement: 確定無効 selection を coordinator が補正する
システムは loaded な非空 tab 一覧で selected key が存在せず、かつその不在を説明する pending cause がない場合、共有 UI に状態を公開する前に coordinator が selected key を一覧末尾の有効な key へ補正しなければならないMUST。UI は確定無効 selection を page 0 表示だけで補ってはならないMUST NOT。初回読込またはcanonical reconciliationで確定した補正結果は、次回復元に使用するselected keyとして永続化しなければならないMUST。

#### Scenario: 復元した selected key が確定無効である
- **WHEN** 初回 loaded 一覧が非空で、復元した selected key が一覧に存在せず pending cause もない
- **THEN** coordinator は一覧の末尾 key を selected key に設定する
- **AND** 補正した末尾 keyを次回復元用に保存する
- **AND** UI は補正済み key に対応する tab content を表示する

#### Scenario: selected key が null のまま非空一覧を読み込む
- **WHEN** 初回 loaded 一覧が非空で復元可能な selected key がない
- **THEN** coordinator は一覧の末尾 key を selected key に設定してから loaded selection を公開する
- **AND** 補正した末尾 keyを次回復元用に保存する
- **AND** UI は blank content を表示しない

#### Scenario: 選択中 tab の close が確定する
- **WHEN** 選択中 tab が削除され、削除後も tab が残る
- **THEN** coordinator は削除前 index と同じ位置の tab を選択し、その位置が範囲外なら末尾 tab を選択する

#### Scenario: 選択中でない tab の close が確定する
- **WHEN** 選択中でない tab が削除され、現在の selected key が削除後一覧にも存在する
- **THEN** coordinator は現在の selected key を維持する

#### Scenario: 最後の tab の close が確定する
- **WHEN** 選択中の最後の tab が削除される
- **THEN** coordinator は selected key を null にし、0 tab 状態を公開する
