## Why

投稿成功直後のスレッド再取得に新しいレスがまだ含まれない場合、現行実装は未確定投稿を破棄するため、自分の投稿レスにマークが永久に付かない。投稿成功応答のヘッダーを利用できる場合は確定精度を高め、利用できない場合も対象スレッドの取得結果と後から照合できる永続的な仕組みに改める。

## What Changes

- 投稿成功応答から、大文字小文字を区別せず `X-Resnum`、`X-Postplace`、`X-Postdate`、`X-Posterid` を読み取る5ch互換 `PostReceipt` parserを導入し、当面は全providerで安全なfallbackとして試行する。
- 投稿成功時に、サービス・板・スレッドの識別子、投稿内容、照合範囲、期限、妥当な確定レス番号、サーバー投稿時刻、投稿者IDヒントを持つ未確定投稿を Room の `pending_own_posts` に保存する。
- スレッド取得成功時、scope整合済み `X-Resnum` を最優先し、それがなければ日時+本文、投稿者ID prefix、入力済み名前/メールの順で候補を絞り、一意に一致した場合に既存の投稿履歴へ確定保存する。
- 候補なしまたは複数候補の場合は未確定投稿を維持し、後続の再読み込み、手動更新、自動更新、スレッド再表示で再照合する。期限超過時は `EXPIRED` とする。
- ヘッダー欠落・不正・scope不整合時はヘッダーを必須にせず、永続化した投稿内容による汎用照合へfallbackする。
- Room DB を v11 へ移行し、バックアップ復元の current schema validation と version-aware table set を更新する。
- 現行の投稿行、返信ポップアップ、ミニマップのマーク表示は変更しない。

## Capabilities

### New Capabilities
- `my-post-mark`: 未確定投稿の永続化、対象スレッド限定の照合、状態遷移、自分の投稿番号の確定と既存マーク表示を規定する。

### Modified Capabilities
- `thread-load-state`: dat取得成功後の保留投稿処理を、メモリ上の単発記録から永続化された対象スレッド限定の照合へ変更する。
- `backup-restore`: current Room DB version、identity hash、必須application tableをv11 schemaへ更新する。
- `backup-database-prevalidation`: version-aware expected application table setのsource of truthをexported Room schema v2-v11へ拡張する。

## Impact

- `app` モジュールの Room Entity/DAO/Repository、`AppDatabase` migration、Hilt DB bindingを変更する。
- `PostRepository`、投稿成功イベント、`ThreadRouteViewModel`、スレッド取得後の照合UseCaseへ `PostReceipt` を接続する。
- 照合成功後は既存の `PostHistoryRepository` と `myPostNumbers` 監視を再利用するため、UIの見た目や操作は変更しない。
- DB schema v11、exported schema、バックアップ事前検証、ヘッダーparser、migration/DAO/照合/ViewModelテストに影響する。
