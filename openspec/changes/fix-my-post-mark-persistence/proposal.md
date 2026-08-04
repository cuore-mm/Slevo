## Why

投稿成功直後のスレッド再取得に新しいレスがまだ含まれない場合、現行実装は未確定投稿を破棄するため、自分の投稿レスにマークが永久に付かない。特定サービスのレスポンスヘッダーに依存せず、対象スレッドの取得結果と後から照合できる永続的な仕組みに改める。

## What Changes

- 投稿成功時に、サービス・板・スレッドの識別子、投稿内容、照合範囲、期限を持つ未確定投稿を Room の `pending_own_posts` に保存する。
- スレッド取得成功時、そのスレッドに属する `PENDING` レコードだけを未確認レス範囲と照合し、一意に一致した場合に既存の投稿履歴へ確定保存する。
- 候補なしまたは複数候補の場合は未確定投稿を維持し、後続の再読み込み、手動更新、自動更新、スレッド再表示で再照合する。期限超過時は `EXPIRED` とする。
- `x-resnum` や取得結果の末尾位置を自分の投稿判定の必須情報として使用しない。
- Room DB を v10 へ移行し、バックアップ復元の current schema validation と version-aware table set を更新する。
- 現行の投稿行、返信ポップアップ、ミニマップのマーク表示は変更しない。

## Capabilities

### New Capabilities
- `my-post-mark`: 未確定投稿の永続化、対象スレッド限定の照合、状態遷移、自分の投稿番号の確定と既存マーク表示を規定する。

### Modified Capabilities
- `thread-load-state`: dat取得成功後の保留投稿処理を、メモリ上の単発記録から永続化された対象スレッド限定の照合へ変更する。
- `backup-restore`: current Room DB version、identity hash、必須application tableをv10 schemaへ更新する。
- `backup-database-prevalidation`: version-aware expected application table setのsource of truthをexported Room schema v2-v10へ拡張する。

## Impact

- `app` モジュールの Room Entity/DAO/Repository、`AppDatabase` migration、Hilt DB bindingを変更する。
- 投稿成功イベントを処理する `ThreadRouteViewModel` と、スレッド取得後に照合する新規UseCaseを変更・追加する。
- 照合成功後は既存の `PostHistoryRepository` と `myPostNumbers` 監視を再利用するため、UIの見た目や操作は変更しない。
- DB schema v10、exported schema、バックアップ事前検証、migration/DAO/照合/ViewModelテストに影響する。
