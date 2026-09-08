## MODIFIED Requirements

### Requirement: 板画面の板情報シート
板画面のボトムバータイトルカードを長押しした場合、システムは現在表示中の板情報を表示する BoardInfoBottomSheet を表示しなければならない（SHALL）。タイトルカードを通常タップした場合は板タブ一覧を表示し、BoardInfoBottomSheet を表示してはならない（SHALL NOT）。

#### Scenario: 板画面のタイトルカードをタップしたとき
- **WHEN** ユーザーが板画面のボトムバーに表示されているタイトルカード本体を長押しする
- **THEN** 現在表示中の板名をタイトルとして持つ BoardInfoBottomSheet が表示される

#### Scenario: 板画面のタイトルカードを通常タップしたとき
- **WHEN** ユーザーが板画面のボトムバーに表示されているタイトルカード本体を通常タップする
- **THEN** 板タブ一覧が表示される
- **AND** BoardInfoBottomSheet は表示されない

#### Scenario: 板情報シートを閉じたとき
- **WHEN** ユーザーが BoardInfoBottomSheet を閉じる
- **THEN** 板画面に戻り、スレッド一覧とボトムバーの状態は維持される

## ADDED Requirements

### Requirement: スレ画面のスレ情報シート
スレ画面のボトムバータイトルカードを長押しした場合、システムは現在表示中のスレ情報を表示する ThreadInfoBottomSheet を表示しなければならない（SHALL）。タイトルカードを通常タップした場合はスレタブ一覧を表示し、ThreadInfoBottomSheet を表示してはならない（SHALL NOT）。

#### Scenario: スレ画面のタイトルカードを長押ししたとき
- **WHEN** ユーザーがスレ画面のボトムバーに表示されているタイトルカード本体を長押しする
- **THEN** 現在表示中のスレ情報を持つ ThreadInfoBottomSheet が表示される

#### Scenario: スレ画面のタイトルカードを通常タップしたとき
- **WHEN** ユーザーがスレ画面のボトムバーに表示されているタイトルカード本体を通常タップする
- **THEN** スレタブ一覧が表示される
- **AND** ThreadInfoBottomSheet は表示されない
