## ADDED Requirements

### Requirement: File backed application logging
アプリ SHALL `AppLogger` 経由で出力されたログを、アプリ内部領域のログファイルにも保存しなければならない。ログファイル保存の追加 SHALL `AppLogger` の公開 API に Android SDK 型または Kermit 型を追加してはならない。

#### Scenario: Application log is persisted
- **WHEN** アプリコードが `AppLogger` 経由でログを出力する
- **THEN** ログメッセージ、ログレベル、tag、時刻、Throwable 情報がログファイルへ保存される

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
アプリ SHALL ログファイルが無制限に肥大化しないように、サイズ上限またはローテーションを適用しなければならない。

#### Scenario: Log file exceeds configured limit
- **WHEN** ログファイルが設定されたサイズ上限を超える
- **THEN** アプリは古いログを削除、退避、または再作成し、以後のログ書き込みが上限管理されたファイルに保存される

#### Scenario: Logging continues after rotation
- **WHEN** ログファイルのローテーションまたは再作成が実行される
- **THEN** その後に出力されたログは新しいログファイルへ保存される
