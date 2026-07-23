## Why

`DatabaseWriteGate` はバックアップ取得中の Room DB 書き込みを停止し、DB スナップショットの整合性を守る役割を持つ。しかし Codex review により、停止区間終了で再開された writer が `activeWriters` に反映される前に後続の `withWritesSuspended` が開始できる race condition が指摘された。

この race が発生すると、バックアップ用の排他区間と通常 DB 書き込みが同時実行され、バックアップ ZIP 内の DB snapshot が不整合になる可能性があるため、実装開始前に gate の待機・再開モデルを明確化する。

## What Changes

- `DatabaseWriteGate` の待機モデルを、writer と suspension の順序が明確な単一 FIFO queue ベースに整理する。
- 待機 writer を再開する前に `activeWriters` を予約し、再開後に後続 suspension が割り込めないようにする。
- `withWritesSuspended` は active writer が 0、suspension active でない、かつ待機 queue が空の場合のみ即時実行する。
- 待機 writer は、後続の `withWritesSuspended` に追い越されないことを維持する。
- cancellation/exception 時にも queue と active count が復旧することをテストで保証する。
- Codex が指摘した「再開 writer と後続 suspension の race」を再現する unit test を追加する。

## Capabilities

### New Capabilities

- `database-write-gate-race-safety`: `DatabaseWriteGate` が待機 writer の再開と後続 suspension の開始を race-free に制御し、バックアップ用停止区間の排他性を維持することを扱う。

### Modified Capabilities

- なし

## Impact

- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt`
  - `app/src/test/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGateTest.kt`
- Affected behavior:
  - `withWritePermit` と `withWritesSuspended` の待機・再開順序
  - `DatabaseBackupExporter` が依存する DB write suspension の排他保証
- API impact:
  - public method signature は変更しない。
  - `DatabaseWriteGate.withWritePermit` / `withWritesSuspended` の呼び出し側 API は維持する。
- Dependencies:
  - 新規 dependency は追加しない。
