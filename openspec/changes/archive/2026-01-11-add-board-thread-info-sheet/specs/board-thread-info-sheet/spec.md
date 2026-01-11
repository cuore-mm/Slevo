## ADDED Requirements
### Requirement: 板画面のスレッド情報シート
板画面のスレッド一覧で項目を長押しした場合、システムは選択したThreadInfoとBoardInfoを用いたThreadInfoBottomSheetを表示しなければならない（SHALL）。

#### Scenario: スレッド項目を長押ししたとき
- **WHEN** 板画面のスレッド項目を長押しする
- **THEN** そのスレッドの情報を表示するThreadInfoBottomSheetが表示される

#### Scenario: スレッド項目をタップしたとき
- **WHEN** スレッド項目をタップする
- **THEN** スレッド画面へ遷移し、情報シートは表示されない
