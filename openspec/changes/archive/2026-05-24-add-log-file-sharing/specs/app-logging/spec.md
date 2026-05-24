## ADDED Requirements

### Requirement: File backed application logging
アプリ SHALL `AppLogger` 経由で出力されたログを、アプリ内部領域のログファイルにも保存しなければならない。Debug ビルドでは DEBUG / INFO / ERROR を保存し、Release ビルドでは ERROR 以上のみを保存しなければならない。ログファイル保存の追加 SHALL `AppLogger` の公開 API に Android SDK 型または Kermit 型を追加してはならない。

#### Scenario: Application log is persisted
- **WHEN** アプリコードが `AppLogger` 経由でログを出力する
- **THEN** ログメッセージ、ログレベル、tag、時刻、Throwable 情報がログファイルへ保存される

#### Scenario: Debug build persists all application log levels
- **WHEN** Debug ビルドで DEBUG、INFO、ERROR のログが出力される
- **THEN** すべてのログレベルがログファイルへ保存される

#### Scenario: Release build persists only error diagnostics
- **WHEN** Release ビルドで DEBUG、INFO、ERROR のログが出力される
- **THEN** ERROR ログのみがログファイルへ保存される
- **AND** DEBUG と INFO のログはログファイルへ保存されない

#### Scenario: Logging interface remains unchanged
- **WHEN** ログファイル保存を追加した後に `AppLogger` の公開 API を確認する
- **THEN** 公開 API は Kotlin 標準型を中心に構成され、Android SDK 型および Kermit 型を含まない

### Requirement: Crash information persistence
アプリ SHALL 未捕捉例外が発生した場合、クラッシュ情報をログファイルへ記録しなければならない。クラッシュ情報の記録 SHALL 既存の未捕捉例外処理の委譲を妨げてはならない。

#### Scenario: Uncaught exception is written before crash handling continues
- **WHEN** 未捕捉例外が発生する
- **THEN** 例外のメッセージとスタックトレースがログファイルへ記録される
- **AND** 既存の未捕捉例外 handler が存在する場合は、その handler へ処理が委譲される

#### Scenario: Crash logging failure does not block crash handling
- **WHEN** 未捕捉例外発生時のログファイル書き込みに失敗する
- **THEN** アプリは書き込み失敗による追加例外で停止せず、既存の未捕捉例外処理を継続する

### Requirement: Log file size management
アプリ SHALL ログファイルが無制限に肥大化しないように、`app.log` と `app.log.old` の 1 世代ローテーションを適用しなければならない。各ファイルのサイズ上限は初回実装では 1MB を目安としなければならない。

#### Scenario: Log file exceeds configured limit
- **WHEN** ログファイルが設定されたサイズ上限を超える
- **THEN** アプリは既存の `app.log.old` を削除し、`app.log` を `app.log.old` へ退避して、新しい `app.log` を作成する

#### Scenario: Logging continues after rotation
- **WHEN** ログファイルのローテーションまたは再作成が実行される
- **THEN** その後に出力されたログは新しいログファイルへ保存される
