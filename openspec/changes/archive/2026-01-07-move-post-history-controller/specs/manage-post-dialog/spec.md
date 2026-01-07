## ADDED Requirements
### Requirement: PostDialogControllerによる投稿履歴管理
システムは投稿履歴の監視・候補更新・削除をPostDialogControllerが管理するSHALL。

#### Scenario: 履歴監視の準備
- **WHEN** PostDialogControllerがboardIdで履歴監視を開始する
- **THEN** 名前/メールの履歴候補を更新し続ける

#### Scenario: 履歴候補の更新
- **WHEN** 入力中の名前/メールが変更される
- **THEN** PostDialogControllerが最新の候補リストを再計算する

#### Scenario: 履歴の削除
- **WHEN** ユーザーが名前/メールの履歴削除を要求する
- **THEN** PostDialogControllerが履歴ストアから該当値を削除する

### Requirement: BaseViewModelからの履歴処理削除
システムは投稿履歴処理をBaseViewModelに保持しないSHALL。

#### Scenario: 履歴処理の移管
- **WHEN** 投稿履歴処理がPostDialogControllerに移管される
- **THEN** BaseViewModelには履歴処理のAPIが存在しない
