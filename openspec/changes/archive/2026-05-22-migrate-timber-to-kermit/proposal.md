## Why

将来 Kotlin Multiplatform へ移行する可能性がある一方で、現在利用している Timber は KMP に対応していないため、ログ基盤が移行時の制約になる。今の Android アプリの挙動を変えずに、KMP 対応ライブラリである Kermit とアプリ内 logging interface へ切り替えることで、将来の shared module 化に備える。

## What Changes

- Timber 依存と `Timber` 直接利用を廃止する。
- KMP 対応ログライブラリ Kermit を導入する。
- アプリコードは Kermit を直接参照せず、Android 非依存の logging interface を介してログ出力する。
- Hilt で logging interface の Kermit 実装を提供し、Repository / DataSource / ViewModel / NetworkModule などの既存利用箇所を置き換える。
- Compose 内の単発デバッグログなど、不要な Timber ログは削除する。
- 現時点では KMP module の追加や shared source set 化は行わない。

## Capabilities

### New Capabilities
- `app-logging`: アプリ内部のログ出力を Android 非依存 interface 経由で扱い、実装として Kermit を利用するログ基盤。

### Modified Capabilities
- なし

## Impact

- 依存関係: `com.jakewharton.timber:timber` を削除し、`co.touchlab:kermit` を追加する。
- 起動処理: `SlevoApplication` の Timber 初期化を Kermit 初期化へ置き換える。
- DI: logging interface と Kermit 実装を Hilt module で提供する。
- 影響範囲: 既存の Timber 利用箇所である Repository、DataSource、ViewModel、NetworkModule、Compose UI のログ呼び出し。
- テスト: constructor injection の追加により、ViewModel などの既存テストで logger のテスト用実装または mock を渡す必要がある。
