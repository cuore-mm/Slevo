## ADDED Requirements

### Requirement: タブ一覧画面の責務分離後の挙動維持
システムはタブ一覧画面の内部実装を分割しても、長押し選択、floating card、dim overlay、タブアクションメニュー、詳細 BottomSheet、下部操作群のユーザー向け挙動を維持しなければならないMUST。責務分離は表示仕様や操作結果を変更してはならないMUST NOT。

#### Scenario: 長押し選択表示を維持する
- **WHEN** ユーザーが板タブまたはスレッドタブを長押しする
- **THEN** システムは変更前と同じ条件で dim overlay、floating card、タブアクションメニューを表示する

#### Scenario: floating card の戻るアニメーションを維持する
- **WHEN** ユーザーが長押し選択状態を解除する
- **THEN** システムは既存の floating card 退場アニメーション仕様を維持し、責務分離だけを理由に表示タイミングや scale 仕様を変更しない

#### Scenario: 詳細 BottomSheet 表示を維持する
- **WHEN** ユーザーがタブアクションメニューの「詳細」を選択する
- **THEN** システムは長押し選択状態を閉じた後も対象タブの詳細 BottomSheet を表示する

### Requirement: タブ一覧画面状態の単一収集
システムはタブ一覧画面で `TabsUiState` を画面上位の Composable で収集し、子 Composable へ必要な値と操作だけを渡さなければならないMUST。同じ画面ツリー内で同じ `StateFlow<TabsUiState>` を不要に複数回収集してはならないMUST NOT。

#### Scenario: 子 Composable が UiState 値を受け取る
- **WHEN** タブ一覧画面を描画する
- **THEN** システムは `TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList` に必要な状態を引数で渡し、各 Composable が独立して `tabsViewModel.uiState` を再収集しない

#### Scenario: lifecycle aware に状態を収集する
- **WHEN** タブ一覧画面が composition に入る
- **THEN** システムは画面上位で lifecycle aware な方法により `TabsUiState` を収集する
