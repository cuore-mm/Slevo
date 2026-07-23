## Why

Room DB のバックアップでは checkpoint 後に main DB ファイルをコピーするため、コピー中にアプリ内の通常書き込みが入ると WAL が再生成され、バックアップ DB の一貫性が弱くなる。バックアップ機能本体に先立ち、Room DB 書き込みを一時停止できる共通の書き込みゲートを導入する。

## What Changes

- Room DB 書き込みを制御する `DatabaseWriteGate` を追加する。
- 通常の Room DB 書き込み経路を `DatabaseWriteGate.withWritePermit { ... }` 経由に移行する。
- バックアップなどの排他処理が `DatabaseWriteGate.withWritesSuspended { ... }` という中立 API で新規 DB 書き込みを待機させられるようにする。
- 複数 Repository/DataSource をまたぐ書き込みで二重 gate が発生しないよう、外側 orchestration と内側 ungated helper の分担を明確にする。
- DataStore 書き込み、read-only DAO query、Flow observe、remote data source、parser は gate 対象外とする。
- バックアップ作成 UI、ZIP 出力、DB ファイルコピーはこの変更では実装しない。
- 掲示板サービス更新の `BbsLocalDataSourceImpl` 廃止は先行変更 `remove-bbs-local-data-source` に切り出し、この変更では同変更後の `BbsServiceRepository` を gate 対象にする。

## Capabilities

### New Capabilities

- `database-write-gate`: Room DB 書き込みを通常時は通し、バックアップ準備中は新規書き込みを待機させる共通制御。

### Modified Capabilities

- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/database/` または同等の DB 制御用 package の新規 `DatabaseWriteGate`
  - Room DB へ書き込む Repository/DataSource:
    - `BbsServiceRepository.kt`
    - `BoardRepository.kt`
    - `BookmarkBoardRepository.kt`
    - `ThreadBookmarkRepository.kt`
    - `TabsRepository.kt`
    - `ThreadHistoryRepository.kt`
    - `ThreadReadStateRepository.kt`
    - `PostHistoryRepository.kt`
    - `ThreadStateRepository.kt`
    - `NgRepository.kt`
    - `DatabaseCallback.kt`
- Hilt DI に `DatabaseWriteGate` を singleton として提供する。
- 先行変更 `remove-bbs-local-data-source` の完了を前提にする。
- 既存の Room schema、DAO query、DataStore schema は変更しない。
- 既存機能のユーザー向け挙動は変えない。
