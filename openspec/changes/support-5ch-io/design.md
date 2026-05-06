## Context

Issue 472 では、5ch のドメイン変更に合わせて `5ch.io` のDeep Link、既定BBSMenu、旧 `5ch.net` URLの扱いを更新する必要がある。現状は `AndroidManifest.xml`、Deep Link許可リスト、URLリゾルバ、URL入力テストが主に `5ch.net` を前提としている。

板/スレを開く処理は、Deep Link とURL入力のどちらも共通URLリゾルバで `host` / `boardKey` / `threadKey` を得たあと、画面遷移用の `boardUrl` を組み立てる構造になっている。このため `5ch.net` から `5ch.io` への変換は、ネットワーク層や投稿処理ではなく、板/スレを開く直前の `boardUrl` 正規化として扱うのが最小範囲である。

## Goals / Non-Goals

**Goals:**
- `*.5ch.io` と `itest.5ch.io` のDeep LinkをAndroidが受け付けられるようにする。
- URL入力とDeep Linkで `5ch.io` のPC版/itest版の板・スレURLを開けるようにする。
- 既定BBSMenu参照先を `5ch.io` 側へ変更する。
- 全般設定で `5ch.net` の板/スレを `5ch.io` として開くか切り替えられるようにする。
- 設定オン時は、Deep LinkまたはURL入力で `5ch.net` の板/スレを開く直前に `boardUrl` のhostだけを `5ch.io` へ正規化する。

**Non-Goals:**
- 投稿処理、スレ立て処理、OkHttpクライアント全体でのURL変換は行わない。
- 保存済みブックマーク、履歴、既存タブの一括移行は行わない。
- `bbspink.com` / `2ch.sc` のドメイン変換は行わない。
- dat形式やoyster形式など、既存で対象外のURLパターンを新たに開けるようにはしない。

## Decisions

### 1. リダイレクトは「開く直前の boardUrl 正規化」に限定する

Deep Link処理とURL入力処理は、解析済みの `host` と `boardKey` から `boardUrl` を作って画面遷移している。ここに設定値を反映し、`*.5ch.net` のhostのみ `*.5ch.io` に変換してから `AppRoute.Board` / `AppRoute.Thread` に渡す。

代替案として OkHttp Interceptor による全通信変換も考えられるが、投稿や画像取得など対象外の通信まで巻き込むため採用しない。今回の要件では「板、スレを開くとき」に限定する方が副作用が少ない。

### 2. 設定は全般設定のUiStateとして扱う

既存の全般設定は `SettingsViewModel` が `SettingsUiState` を所有し、`SettingsRepository` 経由で DataStore を購読している。この流れに `redirect5chNetTo5chIo` を追加し、デフォルト値は `true` とする。

設定画面には `SwitchSpec` を使った項目を追加する。ラベルと説明文は string resource に定義し、プレビューにもデフォルトオン状態を反映する。

### 3. URL解析は `5ch.io` を既存5ch系パターンに追加する

共通URLリゾルバはhost自体を固定列挙していないため、PC版 `*.5ch.io` は既存ロジックで解析できる見込みがある。Deep Linkの許可判定には `5ch.io` を追加し、itestは `itest.` prefix から `server.5ch.io` を構築する既存方式を使う。

ただし仕様として `5ch.io` を正式に対象に含め、ユニットテストで `agree.5ch.io` と `itest.5ch.io` を確認する。

### 4. 既定BBSMenu URLは `5ch.io` 側に変更する

`BbsServiceRepository` の既定BBSMenu URLを `https://menu.5ch.io/bbsmenu.json` に変更する。URL入力やitest板URLのhost補完でメニュー参照を行う場合も、新しいメニューを参照する。

## Risks / Trade-offs

- [Risk] `https://menu.5ch.io/bbsmenu.json` が利用できない、または形式が既存と異なる可能性がある → 実装時に取得可否を確認し、既存パーサーで扱えない場合は追加対応を検討する。
- [Risk] `5ch.net` URLを `5ch.io` として開くため、保存されるタブや履歴のURLが入力元と異なる → 設定名と説明文で「開く先を5ch.ioにする」ことを明示する。
- [Risk] itest板URLは板host解決にBBSMenuを使うため、メニュー上に該当boardKeyがない場合は開けない → 既存と同様にエラー扱いとし、今回の変更ではフォールバック探索を増やさない。
- [Risk] 投稿時変換を行わないため、既存の `5ch.net` として保存済みのスレから投稿すると旧hostへ送信される可能性がある → 今回の対象は「URL入力/Deep Linkで開く時の正規化」とし、保存済みデータ移行は非対象として明確化する。

## Migration Plan

1. DataStoreに新しいboolean設定キーを追加し、未設定時は `true` として扱う。
2. ManifestとURL許可判定に `5ch.io` を追加する。
3. Deep Link / URL入力で開く直前の `boardUrl` 正規化を追加する。
4. 既定BBSMenu URLを `5ch.io` 側に変更する。
5. 関連ユニットテストを追加/更新する。

Rollback は、設定項目と正規化処理を戻し、既定BBSMenu URLとManifest/許可リストから `5ch.io` を除外すればよい。DataStoreキーが残っても未参照であれば動作影響はない。

## Open Questions

- `https://menu.5ch.io/bbsmenu.json` が本番で安定提供されているかは実装時に確認する。
