## ADDED Requirements

### Requirement: スレ読み込み失敗時の通知イベント発行
ThreadViewModel はスレ読み込みが失敗した場合、ローディング状態を解除したうえで、スレ画面に読み込み失敗 Toast を表示させる one-shot event を発行しなければならないMUST。

#### Scenario: dat 取得中に例外が発生する
- **WHEN** ThreadViewModel の loadData が dat 取得または変換中の例外で失敗する
- **THEN** ThreadViewModel は isLoading を false に戻し、スレッド読み込み失敗 Toast 用 event を発行する

#### Scenario: dat 取得が失敗結果で終了する
- **WHEN** ThreadViewModel の loadData が投稿一覧を取得できない失敗結果で終了する
- **THEN** ThreadViewModel は isLoading を false に戻し、スレッド読み込み失敗 Toast 用 event を発行する
