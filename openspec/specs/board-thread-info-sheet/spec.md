# board-thread-info-sheet Specification

## Purpose
板画面とスレッド画面で、長押しまたはタイトルカードタップにより情報ボトムシートを表示する。スレッド情報シートと板情報シートは共通UIを利用し、コピー・外部ブラウザ・共有などの操作を提供する。

## Requirements

### Requirement: 板画面のスレッド情報シート
板画面のスレッド一覧で項目を長押しした場合、システムは選択したThreadInfoとBoardInfoを用いたThreadInfoBottomSheetを表示しなければならない（SHALL）。

#### Scenario: スレッド項目を長押ししたとき
- **WHEN** 板画面のスレッド項目を長押しする
- **THEN** そのスレッドの情報を表示するThreadInfoBottomSheetが表示される

#### Scenario: スレッド項目をタップしたとき
- **WHEN** スレッド項目をタップする
- **THEN** スレッド画面へ遷移し、情報シートは表示されない

### Requirement: 板画面の板情報シート
板画面のボトムバータイトルカードをタップした場合、システムは現在表示中の板情報を表示する BoardInfoBottomSheet を表示しなければならない（SHALL）。

#### Scenario: 板画面のタイトルカードをタップしたとき
- **WHEN** ユーザーが板画面のボトムバーに表示されているタイトルカードをタップする
- **THEN** 現在表示中の板名をタイトルとして持つ BoardInfoBottomSheet が表示される

#### Scenario: 板情報シートを閉じたとき
- **WHEN** ユーザーが BoardInfoBottomSheet を閉じる
- **THEN** 板画面に戻り、スレッド一覧とボトムバーの状態は維持される

### Requirement: 板情報シートの表示内容
BoardInfoBottomSheet は、板名の下にレス数や日付ではなく、その板のサービス名を表示しなければならない（MUST）。

#### Scenario: 板情報シートのサブ情報を表示するとき
- **WHEN** BoardInfoBottomSheet が表示される
- **THEN** タイトルとして板名が表示される
- **AND** タイトルの下に板のサービス名が表示される
- **AND** レス数、スレッド作成日時、勢いは表示されない

### Requirement: 板情報シートのアクション
BoardInfoBottomSheet は、アクションとしてコピー、外部ブラウザで開く、共有のみを表示しなければならない（MUST）。

#### Scenario: 板情報シートのアクションを表示するとき
- **WHEN** BoardInfoBottomSheet が表示される
- **THEN** コピーアクションが表示される
- **AND** 外部ブラウザで開くアクションが表示される
- **AND** 共有アクションが表示される
- **AND** NG登録、板遷移、ブックマーク操作は表示されない

#### Scenario: コピーアクションを選択したとき
- **WHEN** ユーザーが BoardInfoBottomSheet のコピーアクションを選択する
- **THEN** 板名、板URL、板名と板URLの組み合わせをコピー対象として選択できる

#### Scenario: 外部ブラウザで開くアクションを選択したとき
- **WHEN** ユーザーが BoardInfoBottomSheet の外部ブラウザで開くアクションを選択する
- **THEN** 現在表示中の板URLが外部ブラウザで開かれる

#### Scenario: 共有アクションを選択したとき
- **WHEN** ユーザーが BoardInfoBottomSheet の共有アクションを選択する
- **THEN** 現在表示中の板URLを含む共有 Intent が起動される

### Requirement: 情報シートUIの共通化
ThreadInfoBottomSheet と BoardInfoBottomSheet は、タイトル、サブ情報、アクションボタン領域を描画する共通UIを利用しなければならない（MUST）。

#### Scenario: スレッド情報シートを表示するとき
- **WHEN** ThreadInfoBottomSheet が表示される
- **THEN** 共通情報シートUIを通してスレッドタイトル、レス数・日付・勢い、スレッド用アクションが表示される

#### Scenario: 板情報シートを表示するとき
- **WHEN** BoardInfoBottomSheet が表示される
- **THEN** 共通情報シートUIを通して板名、サービス名、板用アクションが表示される

