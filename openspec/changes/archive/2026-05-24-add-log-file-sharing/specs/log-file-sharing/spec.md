## ADDED Requirements

### Requirement: About screen exposes log sharing action
「このアプリについて」画面 SHALL、ユーザーが保存済みログを共有するための「ログを共有」項目を表示しなければならない。

#### Scenario: User opens About screen
- **WHEN** ユーザーが「このアプリについて」画面を開く
- **THEN** 画面には「ログを共有」項目が表示される

#### Scenario: User taps share log item
- **WHEN** ユーザーが「ログを共有」項目をタップする
- **THEN** アプリは保存済みログファイルの共有処理を開始する

### Requirement: Log file is shared through Android share sheet
アプリ SHALL 保存済みログファイルを共有時点で cache 領域へ一時コピーし、その一時コピーを FileProvider の URI として公開して Android の共有 Intent で共有しなければならない。共有 Intent SHALL 共有先アプリに一時コピーされたログファイルの読み取り権限を一時付与しなければならない。

#### Scenario: Log file exists and user shares it
- **WHEN** 保存済みログファイルが存在し、ユーザーがログ共有を実行する
- **THEN** アプリは保存済みログファイルを共有用 cache ディレクトリへ一時コピーする
- **AND** アプリは `ACTION_SEND` Intent を作成し、一時コピーの URI を添付する
- **AND** Intent には読み取り権限付与が設定される
- **AND** Android の共有 UI が表示される

#### Scenario: Shared URI is limited to shared log cache directory
- **WHEN** アプリがログファイルの共有 URI を作成する
- **THEN** URI は FileProvider に設定された共有用 cache ディレクトリ配下の一時コピーファイルのみを指す

#### Scenario: Shared content is stable during logging
- **WHEN** ログ共有開始後に現行ログファイルへ追記またはローテーションが発生する
- **THEN** 共有 Intent に添付済みの一時コピーファイルの内容は共有開始時点の内容を維持する

### Requirement: Log sharing handles unavailable logs gracefully
アプリ SHALL 共有可能なログファイルが存在しない、空である、または共有開始に失敗した場合でもクラッシュしてはならない。アプリ SHALL ユーザーに共有できない状態を通知しなければならない。

#### Scenario: No log file exists
- **WHEN** 保存済みログファイルが存在しない状態でユーザーがログ共有を実行する
- **THEN** アプリは共有 Intent を開始せず、共有可能なログがないことを通知する

#### Scenario: Log file is empty
- **WHEN** 保存済みログファイルが空の状態でユーザーがログ共有を実行する
- **THEN** アプリは共有 Intent を開始せず、共有可能なログがないことを通知する

#### Scenario: Share target is unavailable
- **WHEN** 共有 Intent を処理できるアプリが存在しない
- **THEN** アプリはクラッシュせず、ログを共有できないことを通知する

#### Scenario: FileProvider URI creation fails
- **WHEN** ログファイルの URI 作成に失敗する
- **THEN** アプリはクラッシュせず、ログを共有できないことを通知する
