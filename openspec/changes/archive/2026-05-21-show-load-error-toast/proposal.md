## Why

板画面とスレ画面の読み込み処理は、通信・DB・パースなどの失敗を捕捉しても UI に通知しない経路があり、ユーザーには更新が止まった理由が分からない。読み込み失敗時に短い Toast を表示し、詳細はログへ残すことで、利用者へのフィードバックと不具合調査のしやすさを改善する。

## What Changes

- 板画面の板一覧読み込み・更新失敗時に Toast でエラーメッセージを表示する。
- スレ画面のスレッド読み込み・更新失敗時に Toast でエラーメッセージを表示する。
- ViewModel は直接 Toast を表示せず、画面側が収集する one-shot UI event を発行する。
- 例外を握りつぶさず、原因調査用に `Timber` へ詳細ログを出力する。
- 読み込み処理が `false` などの失敗結果を返す場合も Toast 表示対象にする。
- 既存の画像保存 Toast や URL 不正 Toast の挙動は変更しない。

## Capabilities

### New Capabilities
- `load-error-toast`: 板画面・スレ画面の読み込み失敗を one-shot Toast としてユーザーに通知する振る舞いを定義する。

### Modified Capabilities
- `thread-load-state`: スレッド読み込み失敗時のロード状態解除に加え、ユーザー通知イベントを発行する要件を追加する。

## Impact

- 影響範囲: `BoardViewModel`、`ThreadViewModel`、`BoardScaffold`、`ThreadScaffold`、読み込み関連 UI state / UI event、関連テスト。
- 画面表示は Toast の追加のみで、既存の一覧・本文表示レイアウトは変更しない。
- ネットワーク層や DB 層の API 変更は最小限に抑え、まずは ViewModel 境界で失敗を UI event 化する。
