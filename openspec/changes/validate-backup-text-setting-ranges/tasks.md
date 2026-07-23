## 1. Canonical 制約

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/model/TextDisplaySettingsConstraints.kt` に KDoc 付き `TextDisplaySettingsConstraints` object を追加し、scale/line-height の inclusive range、4 既定値、finite かつ range 内を判定する `isValidTextScale()` / `isValidLineHeight()` を一元定義する。完了条件: 範囲値と既定値を所有する production 定義がこの object だけである。
- [x] 1.2 `app/src/test/java/com/websarva/wings/android/slevo/data/model/TextDisplaySettingsConstraintsTest.kt` を追加し、canonical range から導出した endpoint、`nextDown()` / `nextUp()`、NaN、正負 infinity、および全既定値の range 内性を検証する。完了条件: test 側に min/max の numeric literal がない。

## 2. UI と既定値 producer の統合

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/sheet/DisplaySettingsBottomSheet.kt` の 3 scale slider、line-height slider、reset callback を `TextDisplaySettingsConstraints` の range/default 参照へ置換し、private default constants と inline range literals を削除する。完了条件: label、steps、snap factor、Composable 構造、表示値、accessibility semantics は変更されない。
- [x] 2.2 `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/impl/SettingsLocalDataSourceImpl.kt` の 4 fallback と `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/state/ThreadUiState.kt` の 4 constructor default を canonical defaults へ置換する。`app/src/main/java/com/websarva/wings/android/slevo/data/model/ThreadConstants.kt` の `DEFAULT_THREAD_LINE_HEIGHT` を削除し、残る参照も canonical default に移す。完了条件: DataStore key/type と setter の pass-through behavior は不変で、旧 default symbol の参照が 0 件である。
- [x] 2.3 `app/src/androidTest/java/com/websarva/wings/android/slevo/ui/thread/sheet/DisplaySettingsBottomSheetTest.kt` を追加し、`DisplaySettingsContent` の 4 slider の `ProgressBarRangeInfo` が canonical range と一致し、reset が canonical 4 defaults を callback へ渡すことを canonical 値から検証する。必要な識別子を追加する場合は非表示 test tag のみに限定する。完了条件: UI file が別 range/default に drift すると test が失敗する。

## 3. Backup validation

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupReader.kt` の `validateSettings()` で 3 scale field に `isValidTextScale()`、line-height に `isValidLineHeight()` を使用し、reader 固有の positivity/min/max 判定を削除する。完了条件: invalid 値は引き続き `null` から `BackupRestoreResult.Invalid("invalid settings JSON")` へ進み、clamp・例外・部分適用を追加していない。
- [x] 3.2 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/restore/BackupReaderTest.kt` の valid fixture を canonical defaults/ranges 内の値へ統一し、4 field ごとの inclusive endpoint acceptance と `nextDown()` / `nextUp()` rejection を canonical range から導出して追加する。完了条件: 4 field の下限・上限・直外値を個別に検証し、reader test に range literal がない。
- [x] 3.3 `BackupReaderTest.kt` に 4 field ごとの NaN・正の infinity・負の infinity rejection を追加する。Moshi が非有限 `Float` を serialize できないケースは field 名と raw token だけを差し替える手書き JSON helper で検証する。完了条件: 全 12 非有限ケースが既存 `BackupRestoreResult.Invalid` になる。
- [x] 3.4 既存 `BackupReaderTest.preview_containsSettingsAndTabsJson` の範囲外 `textScale = 2.0f` fixture を canonical range 内の非境界値へ変更し、preview passthrough の意図を維持する。完了条件: 旧範囲外 fixture が valid backup として残っていない。

## 4. Compatibility と verification

- [x] 4.1 `BackupDataMapperTest.kt` と既存 round-trip tests を確認し、JSON field 名、`Float` 型、backup format version、DataStore key の期待値を変更せず通す。完了条件: serialized schema の変更が diff に含まれない。
- [x] 4.2 `rg '0\.7f\s*\.\.\s*1\.6f|1\.2f\s*\.\.\s*1\.8f|DEFAULT_THREAD_LINE_HEIGHT' app/src` を実行し、空白表記を含む range literal は `TextDisplaySettingsConstraints.kt` だけ、旧 line-height default symbol は 0 件であることを確認する。`rangeTo` など別表記も `rg '0\.7f|1\.6f|1\.2f|1\.8f' app/src` で確認し、canonical 定義以外に範囲を再定義していれば canonical 参照へ修正する。
- [x] 4.3 `android-ci` workflow を `ci-build` 手順で起動し、`testCiUnitTest` と `assembleCi` が成功するまで修正する。完了条件: 対象 commit の GitHub Actions run が成功している。
- [ ] 4.4 API 対応 emulator/device で `DisplaySettingsBottomSheetTest` を実行し、4 slider range と reset drift test が成功することを確認する。これは機械テストのみとし、user-waived post-implementation audit や追加の目視 UI audit は行わない。
