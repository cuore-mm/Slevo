## Why

復元済み DB の Room migration が失敗してトランザクションがロールバックされると、DB の `user_version` は復元マーカーの `databaseVersion` のまま残る。現行の起動処理はこれを「migration 前の正常な初回状態」と毎回判定するため、同じ migration を各コールドスタートで再実行して恒久的な起動クラッシュループになり、既存のロールバックスナップショットを使った安全な復旧へ移れない。

## What Changes

- Room migration の実行開始を、既存の pending-restore マーカーに復元世代と一体のクラッシュセーフな証跡として永続化する。
- `MIGRATION_PENDING` かつ migration 前 DB を、証跡なしなら初回試行として Room に委ね、証跡ありなら前回試行失敗として既存の DB/DataStore ロールバックへ遷移させる。
- migration 成功後・DB コミット後・完了処理前のプロセス終了では、現行の厳密検証と完了処理を継続し、成功済み DB を誤ってロールバックしない。
- 旧マーカーを初回試行として扱い、任意のメモリ内カウンターや新しいユーザー向け表示・文言を追加しない。
- migration 開始証跡、失敗後の次回起動、DB/DataStore 世代整合、永続化失敗、各プロセス終了境界を単体テストで検証する。

## Capabilities

### New Capabilities

- `pending-restore-migration-attempt-recovery`: pending restore の Room migration 試行を永続的に識別し、反復失敗時にクラッシュループではなく安全なロールバックへ遷移する要件を定義する。

### Modified Capabilities

なし。

## Impact

- 対象は `PendingRestoreMarker` とその JSON 互換性、`PendingRestoreApplier` の `MIGRATION_PENDING` 復旧分岐、Room migration 登録経路、pending-restore のファイルストア、完了チェッカー、および関連する unit / migration test である。
- `SlevoApplication.onCreate()` が Room/Hilt より先に pending restore を処理する既存順序、DB/WAL と DataStore のロールバックスナップショット、結果通知 UI の契約は維持する。
- 新規依存関係、DB schema 変更、バックアップ形式変更、ユーザー向け UI 変更はない。
