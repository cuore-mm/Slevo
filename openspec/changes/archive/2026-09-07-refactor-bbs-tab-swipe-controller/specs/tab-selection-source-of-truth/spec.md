## MODIFIED Requirements

### Requirement: Pager 操作は selected key に反映する
システムはユーザー操作または既存のページ移動要求によって Pager が別ページへ settle した場合、settle したページに対応するタブの stable key を選択中 key として反映しなければならないMUST。ドラッグまたは移動アニメーションの途中で最寄りページが変化しただけでは、選択中 key を更新してはならないMUST NOT。

#### Scenario: 板 Pager をスワイプする
- **WHEN** ユーザーが下部コントローラーを操作し、板 Pager が別の板タブへ settle する
- **THEN** システムは settle した板タブの boardUrl を選択中板タブ key に設定する

#### Scenario: スレッド Pager をスワイプする
- **WHEN** ユーザーが下部コントローラーを操作し、スレッド Pager が別のスレッドタブへ settle する
- **THEN** システムは settle したスレッドタブの ThreadId を選択中スレッドタブ key に設定する

#### Scenario: ドラッグ途中で最寄りページが変わる
- **WHEN** ドラッグ中に最寄りページが隣接タブへ変わった後、Pager が元のタブへ settle する
- **THEN** システムはドラッグ途中の隣接タブを選択中 key に設定しない

#### Scenario: 既存のページ移動要求が完了する
- **WHEN** 次・前タブへの既存のページ移動要求によるアニメーションが完了する
- **THEN** システムは最終的に settle したタブの stable key を選択中 key に設定する
