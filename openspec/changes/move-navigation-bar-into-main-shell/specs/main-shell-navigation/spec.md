## Purpose

アプリ共通の下部NavigationをMainShell画面の一部として管理し、詳細画面への遷移中も安定した画面boundsと復元可能な二階層Navigation履歴を提供する。

## ADDED Requirements

### Requirement: MainShellと詳細画面のNavigation階層を分離する
システムはTabs、BookmarkおよびBBSサービス一覧群をMainShell内のNavigation履歴として管理し、Board、Thread、History、Settings、AboutおよびImageViewerをMainShellと同階層のRoot Navigation履歴として管理しなければならない（MUST）。

#### Scenario: MainShell内のトップレベル画面を切り替える
- **WHEN** ユーザーが下部NavigationでTabs、BookmarkまたはBBSサービスを選択する
- **THEN** システムはRoot Navigation履歴を追加せず、MainShell内の対象画面を表示する
- **AND** 各トップレベル画面の保存可能な状態を既存規則どおり復元する

#### Scenario: MainShellからBoardを開いて戻る
- **WHEN** ユーザーがMainShell内の画面からBoardを開き、Backを実行する
- **THEN** システムは直前のMainShell画面とその内部履歴を復元する

#### Scenario: MainShellからSettingsを開いて戻る
- **WHEN** ユーザーがMainShellからSettingsを開き、Backを実行する
- **THEN** システムはSettingsをRoot履歴から除去して以前のMainShell状態を復元する

### Requirement: NavigationBarをMainShellの一部として遷移させる
システムはNavigationBarをMainShellの表示領域に含め、MainShellからRoot詳細画面へ遷移するときにMainShell全体と同じ遷移期間で退出させなければならない（MUST）。NavigationBarの表示状態変更によってRoot画面の利用可能boundsを遷移中に変更してはならない（MUST NOT）。

#### Scenario: TabsからBoardへ遷移する
- **WHEN** ユーザーがTabsカードからBoardを開く
- **THEN** NavigationBarは遷移開始前に単独で消失せず、退出中のMainShellの一部として扱われる
- **AND** Root画面の幅と高さはNavigationBarの表示切替によって遷移途中に変化しない

#### Scenario: BoardからMainShellへ戻る
- **WHEN** ユーザーがBoardからBackでMainShellへ戻る
- **THEN** NavigationBarはMainShellと同じRoot遷移で復帰し、MainShell contentを途中で再配置しない

### Requirement: 遷移元に応じたRoot画面遷移を復元する
システムはMainShellからBoardまたはThreadを開いたときの遷移種別を対象履歴entryへ保持し、Backおよび状態復元後も遷移元に応じた既存transitionを再現しなければならない（MUST）。

#### Scenario: TabsからBoardを開く
- **WHEN** Tabsカードから対応するBoardを開く
- **THEN** システムは横slideを適用せず、対応するカードとBoardページをShared Boundsで接続する

#### Scenario: BookmarkからBoardを開く
- **WHEN** BookmarkからBoardを開く
- **THEN** システムはTabs用Shared Boundsを適用せず、既存の通常slideとfadeを実行する

#### Scenario: 復元後にBackを実行する
- **WHEN** BoardまたはThreadを含む履歴が状態復元され、その画面からBackを実行する
- **THEN** システムは復元された遷移文脈に対応する逆方向transitionを使用する

### Requirement: MainShellを跨ぐNavigation要求を現在entryへ限定する
システムはMainShell内で開始した非同期のタブ登録または選択処理について、処理開始時のMainShellおよび内部画面entryが現在も有効な場合だけRoot履歴を変更しなければならない（MUST）。

#### Scenario: 登録処理中に別画面へ移動する
- **WHEN** タブ登録または正規化の完了前にユーザーがMainShellまたは対象内部画面を離れる
- **THEN** 遅れて完了した処理はRoot画面を予期せず変更しない

#### Scenario: 現在のTabsで登録が完了する
- **WHEN** タブ登録が成功し、処理開始時のMainShellとTabs entryが現在も有効である
- **THEN** システムは指定された履歴規則に従って対象BoardまたはThreadを開く
