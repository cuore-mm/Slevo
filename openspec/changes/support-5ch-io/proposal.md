## Why

5ch の主要ドメインが `5ch.net` から `5ch.io` に変更されたため、既存の板/スレURLやDeep Link処理が新ドメインを前提に動作できない。ユーザーが新旧ドメインのURLを自然に開けるようにし、アプリ内で開く板/スレは新しい `5ch.io` 側へ寄せる必要がある。

## What Changes

- Android Deep Link の受け付け対象に `*.5ch.io` と `itest.5ch.io` を追加する。
- URL入力とDeep Linkの許可ドメインに `5ch.io` を追加する。
- 共通URLリゾルバで `5ch.io` / `itest.5ch.io` の板・スレURLを解析対象に含める。
- 既定で追加/参照する5ch BBSMenu URLを `5ch.io` 側へ変更する。
- 全般設定に `5ch.net` の板/スレを `5ch.io` として開く設定を追加する。
  - デフォルトはオン。
  - 変換は板/スレを開く直前の `boardUrl` 正規化に限定し、投稿処理やネットワーク層では追加変換しない。

## Capabilities

### New Capabilities
- `open-5ch-io`: 5ch.io ドメインの板/スレを開く動作と、5ch.net を開く際の5ch.io正規化設定を扱う。
- `default-5ch-menu`: 既定の5ch BBSMenu取得先を新ドメインへ移行する動作を扱う。

### Modified Capabilities
- `resolve-url-routing`: 共通URLリゾルバが `5ch.io` / `itest.5ch.io` のURLを解析できるようにする。
- `handle-url-input`: URL入力から `5ch.io` の板/スレを開けるようにし、設定オン時は `5ch.net` 入力を `5ch.io` の板/スレとして開く。
- `handle-deep-link`: Deep Link から `5ch.io` の板/スレを開けるようにし、設定オン時は `5ch.net` Deep Link を `5ch.io` の板/スレとして開く。

## Impact

- 影響範囲: `AndroidManifest.xml`、URLリゾルバ、Deep Link処理、URL入力処理、全般設定画面/設定永続化、BBSMenu既定URL、関連ユニットテスト。
- 投稿リクエスト、スレ立てリクエスト、OkHttpクライアント全体の通信変換は対象外とする。
- 既存の `bbspink.com` / `2ch.sc` のURL処理は維持する。
