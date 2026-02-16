## 1. DSL移行準備

- [x] 1.1 `gradle.properties` の `android.newDsl=false` を削除し、新 DSL 既定構成で同期できることを確認する
- [x] 1.2 `app/build.gradle.kts` 内の旧 API 参照箇所（`applicationVariants` と internal API キャスト）を移行対象として整理する

## 2. ビルドスクリプト移行

- [x] 2.1 `applicationVariants` ベースの処理を公開 API ベースへ置換し、成果物取得を `androidComponents` の Artifacts API に一本化する
- [x] 2.2 Artifacts API で取得した出力を入力に Copy タスクを構成し、配布名を生成する
- [x] 2.3 リリース配布名を `Slevo-<versionName>.apk` 固定で出力するルールを実装する
- [x] 2.4 `androidComponents` による `ci` バリアントの versionCode 上書き要件が継続して満たされるよう調整する
- [x] 2.5 CI/検証向け成果物は variant 名入りで出力される命名規則に調整する

## 3. 検証

- [ ] 3.1 `:app:assembleRelease` を実行し、期待される命名規則と出力先に APK が生成されることを確認する
- [ ] 3.2 `:app:testDebugUnitTest` を実行し、移行後もユニットテストが成功することを確認する
- [ ] 3.3 CI/検証用 variant の成果物で variant 名入り命名が出力されることを確認する
- [ ] 3.4 AGP 9 環境で deprecation 警告（旧 DSL/旧 Variant API 起因）が再発しないことを確認する
