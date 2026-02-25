## ADDED Requirements

### Requirement: 投稿メニューとダイアログの統合ホスト
システムは主投稿とポップアップ投稿の `PostMenuSheet` および `PostItemDialogs` を単一の共通ホストで管理し、同一の操作結果を返さなければならないMUST。

#### Scenario: 主投稿からのメニュー要求
- **WHEN** 主投稿一覧からメニュー表示要求が発生する
- **THEN** システムは共通ホストで `PostMenuSheet` を表示し、返信/コピー/NG登録の処理結果を従来通り返す

#### Scenario: ポップアップ投稿からのメニュー要求
- **WHEN** ポップアップ投稿一覧からメニュー表示要求が発生する
- **THEN** システムは共通ホストで `PostMenuSheet` を表示し、返信/コピー/NG登録の処理結果を従来通り返す

### Requirement: ThreadScreen は表示責務を持たない
システムは `ThreadScreen` を投稿イベント通知に限定し、投稿メニューおよびダイアログの表示責務を持たせてはならないMUST。

#### Scenario: ThreadScreen からホストへの通知
- **WHEN** `ThreadScreen` が投稿メニューまたはダイアログの要求を受け取る
- **THEN** システムは表示責務を `ThreadScaffold` の共通ホストへ委譲する
