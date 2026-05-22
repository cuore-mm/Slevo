# app-logging Specification

## Purpose
TBD - created by archiving change migrate-timber-to-kermit. Update Purpose after archive.
## Requirements
### Requirement: Logging through application interface
アプリ内のログ出力 SHALL、ログライブラリを直接参照せず、アプリ定義の logging interface を介して行わなければならない。logging interface SHALL Android SDK 型および Kermit 型を公開 API に含めてはならない。

#### Scenario: Application code writes logs
- **WHEN** Repository、DataSource、ViewModel、NetworkModule がログを出力する
- **THEN** それらのクラスは Kermit を直接参照せず、logging interface のメソッドを呼び出す

#### Scenario: Logging interface remains platform independent
- **WHEN** logging interface の公開 API を確認する
- **THEN** 公開 API は Kotlin 標準型を中心に構成され、Android SDK 型および Kermit 型を含まない

### Requirement: Kermit backed logging implementation
アプリ SHALL Kermit をログ出力の実装として利用しなければならない。Kermit への委譲、tag の指定、Throwable の受け渡し SHALL logging interface の実装内で扱わなければならない。

#### Scenario: Error log includes throwable
- **WHEN** 呼び出し側が logging interface にエラーメッセージと Throwable を渡す
- **THEN** Kermit 実装はメッセージと Throwable を保持したエラーログとして出力する

#### Scenario: Tagged debug log is emitted
- **WHEN** 呼び出し側が tag 付きの debug ログを出力する
- **THEN** Kermit 実装は指定された tag をログ出力に反映する

### Requirement: Timber removal
アプリ SHALL Timber 依存を持ってはならず、アプリコードに `Timber` の import または呼び出しが残っていてはならない。

#### Scenario: Dependencies are inspected
- **WHEN** Gradle の依存定義を確認する
- **THEN** Timber 依存は存在せず、Kermit 依存が定義されている

#### Scenario: Source code is inspected
- **WHEN** アプリのソースコードを検索する
- **THEN** `import timber.log.Timber` および `Timber.` の直接呼び出しは存在しない

### Requirement: Preserve user-visible behavior
ログ基盤の移行 SHALL ユーザー向け機能の挙動を変更してはならない。既存のエラー処理、画面状態、通信処理、データ取得処理 SHALL logging interface への置き換え後も同じ結果を返さなければならない。

#### Scenario: Existing error handling runs after logging migration
- **WHEN** Repository、DataSource、ViewModel の既存エラー処理が実行される
- **THEN** ログ出力の有無に関わらず、既存の戻り値、UiState 更新、通知処理は移行前と同等に動作する

### Requirement: Future KMP readiness boundary
ログ基盤 SHALL、現時点で KMP module を追加せずに、将来 shared module へ移しやすい境界を提供しなければならない。

#### Scenario: Future shared module extraction is considered
- **WHEN** logging interface を将来 shared module に移すことを検討する
- **THEN** interface は Android 固有 API に依存していないため、呼び出し側の基本的なログ API を維持したまま移動できる

