## 1. DSL移行準備

- [ ] 1.1 `gradle.properties` の `android.newDsl=false` を削除し、新 DSL 既定構成で同期できることを確認する
- [ ] 1.2 `app/build.gradle.kts` 内の旧 API 参照箇所（`applicationVariants` と internal API キャスト）を移行対象として整理する

## 2. ビルドスクリプト移行

- [ ] 2.1 `applicationVariants` ベースの処理を公開 API ベースへ置換し、internal API 参照を除去する
- [ ] 2.2 リリース APK の命名・コピー処理を新 DSL 互換の成果物処理へ移行する
- [ ] 2.3 `androidComponents` による `ci` バリアントの versionCode 上書き要件が継続して満たされるよう調整する

## 3. 検証

- [ ] 3.1 `:app:assembleRelease` を実行し、期待される命名規則と出力先に APK が生成されることを確認する
- [ ] 3.2 `:app:testDebugUnitTest` を実行し、移行後もユニットテストが成功することを確認する
- [ ] 3.3 AGP 9 環境で deprecation 警告（旧 DSL/旧 Variant API 起因）が再発しないことを確認する
