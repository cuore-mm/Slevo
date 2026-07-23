## Context

`DisplaySettingsBottomSheet.kt` は `textScale`、`headerTextScale`、`bodyTextScale` に `0.7f..1.6f`、`lineHeight` に `1.2f..1.8f` を inline 指定している。一方、`BackupReader.validateSettings()` は各値について `isFinite()` かつ `> 0f` のみを確認するため、UI が生成できない巨大値や下限未満の正数を復元可能とする。

範囲を所有する domain 定義は現時点で存在しない。文字倍率の既定値も `DisplaySettingsBottomSheet.kt`、`SettingsLocalDataSourceImpl.kt`、`ThreadUiState.kt` に分散し、行間のみ `ThreadConstants.kt` の `DEFAULT_THREAD_LINE_HEIGHT` を共有する。値は全層で `Float` のまま DataStore と `BackupSettingsJson` を通過し、Room migration には関与しない。

現在の経路は次のとおりである。

```text
DisplaySettingsBottomSheet (inline range/default)
  -> ThreadRouteViewModel
  -> SettingsRepository
  -> SettingsLocalDataSourceImpl (Float DataStore)
  -> BackupDataMapper / BackupSettingsJson (Float JSON)
  -> BackupReader.validateSettings (positive finite only)
  -> PendingRestoreDataStoreWriter
```

この変更では画面の見た目や操作可能範囲を変えず、既存の UI 範囲を domain 契約として昇格する。復元時に新たに拒否されるのは、従来 validator が誤って受理していた UI 範囲外の値だけである。

## Goals / Non-Goals

**Goals:**

- 文字倍率・行間の範囲と既定値を一つの production 定義で所有する。
- UI、既定値生成、backup validation が同じ定義を直接参照し、将来の範囲変更で validator 編集を不要にする。
- NaN、正負の infinity、下限未満、上限超過を既存の `BackupRestoreResult.Invalid` 経路で拒否する。
- 境界値を受理し、現在 UI が生成する値と serialized format を維持する。
- production の canonical 値から境界データを導出する unit/Compose テストで drift を検出する。

**Non-Goals:**

- slider の範囲、step、snap、label、reset 結果、layout、文言、accessibility semantics を変更すること。
- DataStore に既に保存された範囲外値の migration、clamp、正規化を追加すること。
- `SettingsLocalDataSourceImpl` の setter 契約や backup export schema を変更すること。
- restore-picker の multi-launch finding、backup format version 更新、post-implementation audit。

## Decisions

### 1. `data/model` の一つの object が範囲・既定値・判定を所有する

新規 `app/src/main/java/com/websarva/wings/android/slevo/data/model/TextDisplaySettingsConstraints.kt` に、KDoc 付き `object TextDisplaySettingsConstraints` を追加する。object は次を公開する。

- `TEXT_SCALE_RANGE: ClosedFloatingPointRange<Float>`: 現在の `0.7f..1.6f`
- `LINE_HEIGHT_RANGE: ClosedFloatingPointRange<Float>`: 現在の `1.2f..1.8f`
- `DEFAULT_TEXT_SCALE: Float`: 現在の `1f`
- `DEFAULT_HEADER_TEXT_SCALE: Float`: 現在の `0.85f`
- `DEFAULT_BODY_TEXT_SCALE: Float`: 現在の `1f`
- `DEFAULT_LINE_HEIGHT: Float`: 現在の `1.4f`
- `isValidTextScale(value: Float): Boolean`
- `isValidLineHeight(value: Float): Boolean`

各判定関数は `value.isFinite()` と対応する inclusive range membership の両方を確認する。既定値は対応範囲内でなければならない。`ThreadConstants.kt` の `DEFAULT_THREAD_LINE_HEIGHT` は削除して全 call site を新 object に移し、既定値を二重所有しない。

範囲を UI file に公開する案は backup/data 層から UI 層への逆依存を作るため採用しない。validator 用 constants を別に作る案は将来 drift を再発させるため採用しない。DI 可能な constraint class は不変な process-wide 定義に不要な配線を追加するため採用しない。

### 2. 全 production consumer は canonical object を直接参照する

- `DisplaySettingsBottomSheet.kt`: 3 個の scale slider は `TEXT_SCALE_RANGE`、line-height slider は `LINE_HEIGHT_RANGE`、reset は 4 個の `DEFAULT_*` を使う。local range/default literals は残さない。
- `SettingsLocalDataSourceImpl.kt`: DataStore key 欠損時の 4 個の fallback は `DEFAULT_*` を使う。raw persisted 値の clamp は行わない。
- `ThreadUiState.kt`: 4 個の constructor default は `DEFAULT_*` を使う。
- `BackupReader.validateSettings()`: 3 個の scale field は `isValidTextScale()`、line-height は `isValidLineHeight()` を使う。reader 内に min/max literal や独自の positivity 判定を残さない。

これにより将来範囲を変更する場合、`TextDisplaySettingsConstraints` の範囲だけを変更すれば UI と reader が同時に追従する。既定値を変更する場合も同 object だけを変更し、対応範囲内であることを constraint test が保証する。

### 3. 復元失敗は既存 invalid-backup 経路を維持する

範囲外または非有限値の場合、`validateSettings()` は従来どおり `null` を返す。`readBackup()` は `BackupRestoreResult.Invalid("invalid settings JSON")` を返し、ViewModel/UI は既存の汎用 invalid-backup snackbar を表示する。例外、clamp、部分適用、新しい user-facing detail は追加しない。

### 4. schema と保存形式は変更しない

`BackupSettingsJson` の field 名・`Float` 型、Moshi adapter、backup format version、DataStore key・型を変更しない。範囲外 backup の受理契約だけを厳格化するため migration は不要である。下限・上限を含む現在の UI 許容値は以前と同じ値として round-trip する。

### 5. テスト値は canonical 制約から導出する

- 新規 `TextDisplaySettingsConstraintsTest.kt` は、各既定値の範囲内性、inclusive endpoint の受理、`nextDown()`/`nextUp()` で作る直外値の拒否、NaN と正負 infinity の拒否を確認する。期待 min/max の numeric literal をテスト側に複製しない。
- `BackupReaderTest.kt` は 4 field を parameterized/loop helper で個別に差し替え、対応する canonical range の endpoint は成功、`start.nextDown()` と `endInclusive.nextUp()` は `BackupRestoreResult.Invalid` になることを確認する。NaN と正負 infinity は手書き JSON helper を使い、Moshi の非有限値 serialization 制約に依存しない。reader test に range literal を書かない。
- 新規 `DisplaySettingsBottomSheetTest.kt` は `DisplaySettingsContent` を Compose test に描画し、各 slider の `ProgressBarRangeInfo` が対応する canonical range と一致すること、および reset callback が canonical defaults を返すことを確認する。test の期待値も object から参照し、UI 側に inline range/default が戻る drift を検出する。
- 既存 `BackupDataMapperTest.kt` の field/type assertion を維持し、必要なら canonical defaults/range 内の値を fixture に使用するが JSON の数値表現自体は変更しない。

## Implementation Contract

1. 共有 object 以外に `0.7f`、`1.6f`、`1.2f`、`1.8f` の範囲 literal を新規追加しない。reader と tests は必ず object の range/validation API を参照する。
2. `DEFAULT_THREAD_LINE_HEIGHT` および `DisplaySettingsBottomSheet.kt` の private default constants を canonical object に統合し、`SettingsLocalDataSourceImpl.kt` と `ThreadUiState.kt` の inline default literals も置換する。
3. `BackupReader.validateSettings()` の theme/gesture validation と `parseSettings()` の error mapping は変更しない。
4. 範囲外値を clamp せず、常に既存 `Invalid("invalid settings JSON")` 経路へ送る。restore 前の preview と confirmed restore の双方が同じ reader を通る契約を維持する。
5. `BackupSettingsJson.kt`、DataStore key、backup manifest/version、resource string、Composable の label/structure/steps/snap factor は変更しない。
6. 新規 object と非自明関数には repository 規約どおり KDoc を付け、長い test helper には必要な section comment を付ける。

## Error Cases and Compatibility

- `Float.NaN`、`Float.POSITIVE_INFINITY`、`Float.NEGATIVE_INFINITY`: validation false、既存 invalid-backup result。
- scale の `TEXT_SCALE_RANGE.start` 未満または `endInclusive` 超過: invalid。endpoint 自体は valid。
- line height の `LINE_HEIGHT_RANGE.start` 未満または `endInclusive` 超過: invalid。endpoint 自体は valid。
- 4 field のいずれか一つでも invalid: settings 全体を拒否し、DataStore 書き込みを開始しない。
- 既存の範囲内 v1 backup: schema/型/version を変えず従来どおり valid。
- 既存端末に範囲外 DataStore 値がある場合: 本変更では migration/clamp しない。そこから export した backup は restore 時に invalid となり得るが、既存保存データを暗黙変更しないことを優先する。

## Risks / Trade-offs

- [従来受理された範囲外 backup が拒否される] → 意図した validation 厳格化として spec に明記し、範囲内値と endpoint の互換性テストを追加する。
- [UI consumer の一部だけが inline literal のまま残る] → 3 scale slider、line-height slider、reset、DataStore fallback、`ThreadUiState` default を明示的に task 化し、Compose drift test と repository search を verification に含める。
- [Float endpoint の外側テストが丸めで endpoint と同値になる] → Kotlin/JDK の `nextDown()`/`nextUp()` を使い、任意 epsilon literal を使わない。
- [Compose semantics test が slider 順序に依存する] → label text と同じ subtree または明示的 test tag で各 slider を識別する。test tag が必要な場合も accessibility semantics や表示は変更しない。
- [既存の範囲外 DataStore 値は残る] → migration/clamp は別の product/data policy を要するため scope 外とし、reader は安全側で拒否する。

## Migration Plan

1. canonical object と unit test を追加する。
2. defaults と UI slider を canonical 参照へ移す。この時点で表示・操作値が変わらないことを Compose test で確認する。
3. reader validation を canonical 判定へ切り替え、boundary/finite tests を追加する。
4. CI で build、unit test、対象 instrumented Compose test を実行する。

Data migration と backup format migration は不要である。rollback は object 導入と consumer 置換を同一 revert で戻せるが、rollback 後は範囲外 backup を再び受理するため、validation defect が再発する。

## Open Questions

なし。
