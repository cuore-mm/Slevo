## Context

Issue 472 では、5ch のドメイン変更に合わせて `5ch.io` のDeep Link、既定BBSMenu、旧 `5ch.net` URLの扱いを更新する必要がある。現状は `AndroidManifest.xml`、Deep Link許可リスト、URLリゾルバ、URL入力テストが主に `5ch.net` を前提としている。

板/スレを開く処理は、Deep Link とURL入力だけでなく、既存タブ、ブックマーク、履歴、板一覧、レス本文中リンクなど複数の入口から `AppRoute.Board` / `AppRoute.Thread` を作って共通ナビゲーションへ渡す構造になっている。このため `5ch.net` から `5ch.io` への変換は、ネットワーク層や投稿処理ではなく、共通ナビゲーションで板/スレを開く直前の `boardUrl` 正規化として扱うのが最小範囲である。

## Goals / Non-Goals

**Goals:**
- `*.5ch.io` と `itest.5ch.io` のDeep LinkをAndroidが受け付けられるようにする。
- URL入力とDeep Linkで `5ch.io` のPC版/itest版の板・スレURLを開けるようにする。
- 既定BBSMenu参照先を `5ch.io` 側へ変更する。
- 全般設定で `5ch.net` の板/スレを `5ch.io` として開くか切り替えられるようにする。
- 設定オン時は、Deep Link、URL入力、既存タブ、ブックマーク、履歴など全ての入口で `5ch.net` の板/スレを開く直前に `boardUrl` のhostだけを `5ch.io` へ正規化する。

**Non-Goals:**
- 投稿処理、スレ立て処理、OkHttpクライアント全体でのURL変換は行わない。
- 保存済みブックマーク、履歴、既存タブなどの永続化データの一括移行や直接更新は行わない。
- `bbspink.com` / `2ch.sc` のドメイン変換は行わない。
- dat形式やoyster形式など、既存で対象外のURLパターンを新たに開けるようにはしない。

## Decisions

### 1. リダイレクトは「共通ナビゲーションでのroute正規化」に限定する

各画面は `AppRoute.Board` / `AppRoute.Thread` を作り、`navigateToBoard` / `navigateToThread` を経由して板/スレを開いている。ここに設定値を反映し、共通ナビゲーション関数の入口でrouteを正規化する。`*.5ch.net` のhostのみ `*.5ch.io` に変換し、変換済みrouteをタブ保証と画面遷移へ渡す。

代替案として各入口で個別に変換する方法もあるが、入口が多く漏れやすいため採用しない。OkHttp Interceptor による全通信変換も考えられるが、投稿や画像取得など対象外の通信まで巻き込むため採用しない。今回の要件では「板、スレを開くとき」に限定し、共通ナビゲーションで集約する方が副作用が少ない。

`AppRoute.Thread.threadTitle` は表示用の名称でありURLではないため、5ch.net→5ch.ioのURL正規化対象に含めない。URL入力、Deep Link、レス本文リンクのようにタイトル未取得の入口では `threadTitle` を未設定として扱う。未設定時の初期表示は、正規化後 `boardUrl` と `threadKey` から組み立てたスレURLを使い、スレ読み込み後に実タイトルで更新する。仮タイトルとして元URLを入れる方式は、タブ名や画面表示に旧hostが残るため採用しない。

### 2. 既存保存データは一括移行せず、タブ保存は正規化後routeにそろえる

ブックマーク、履歴、DB内の板URLはユーザーの保存済みデータとしてそのまま保持し、一括移行や直接更新は行わない。一方で、設定オン時に板/スレを開く操作で作成または保証されるタブは、正規化後routeを保存対象とする。これにより「開く先」と「現在開いているタブの保存URL」がどちらも `5ch.io` になる。

既存の `5ch.net` タブについては自動更新や統合を行わず、設定オンで同じ板/スレを開いた場合は正規化後routeの `5ch.io` タブを別タブとして作成してよい。既存のタブ識別はhostを含むため、`5ch.net` と `5ch.io` を同一視する特別な照合ロジックは追加しない。これにより実装を単純に保ち、既存タブを勝手に書き換えない。

### 3. 設定は全般設定のUiStateとして扱う

既存の全般設定は `SettingsViewModel` が `SettingsUiState` を所有し、`SettingsRepository` 経由で DataStore を購読している。この流れに `redirect5chNetTo5chIo` を追加し、デフォルト値は `true` とする。

ただしタブ画面側のナビゲーション判定では、設定値の初期読込前にデフォルト `true` を仮適用しない。設定値が未読込の瞬間は `5ch.net` → `5ch.io` 変換を保留し、初期起動直後のDeep Link/復元タブで「設定オフなのに `5ch.io` へ変換される」誤判定を防ぐ。

また itest host補完のメニュー選択は `SettingsRepository` 経由で永続化済み設定の現在値を取得して判定する。これにより `TabsViewModel` の一時キャッシュ状態に依存せず、起動直後でも設定オフを反映できる。

加えて host補完のDBキャッシュ参照では、`BoardRepository` 側で `menuDomain` 一致条件を含めて検索する。例えば `itest.5ch.net` かつ設定オフで `menuDomain=5ch.net` の場合、`agree.5ch.io` は検索段階で除外し、`.5ch.net` host が見つからない時だけ `5ch.net` 側メニュー参照にフォールバックする。

設定画面には `SwitchSpec` を使った項目を追加する。ラベルと説明文は string resource に定義し、プレビューにもデフォルトオン状態を反映する。

### 4. URL解析は `5ch.io` を既存5ch系パターンに追加する

共通URLリゾルバはhost自体を固定列挙していないため、PC版 `*.5ch.io` は既存ロジックで解析できる見込みがある。Deep Linkの許可判定には `5ch.io` を追加し、itestは `itest.` prefix から `server.5ch.io` を構築する既存方式を使う。

ただし仕様として `5ch.io` を正式に対象に含め、ユニットテストで `agree.5ch.io` と `itest.5ch.io` を確認する。

### 5. 既定BBSMenu URLは `5ch.io` 側に変更する

`BbsServiceRepository` の既定BBSMenu URLを `https://menu.5ch.io/bbsmenu.html` に変更する。URL入力やitest板URLのhost補完でメニュー参照を行う場合も、新しいメニューを参照する。

ただし `itest.5ch.net/subback/{board}` のようにhost未解決な板URLは、全般設定の `5ch.net` → `5ch.io` 切り替え状態に従って参照先メニューを切り替える。設定オフ時は `5ch.net` 側メニューを使って `.5ch.net` host を補完し、設定オン時は `5ch.io` 側メニューを使って `.5ch.io` host を補完する。

## Risks / Trade-offs

- [Risk] `https://menu.5ch.io/bbsmenu.html` が利用できない、または形式が既存と異なる可能性がある → 実装時に取得可否を確認し、既存パーサーで扱えない場合は追加対応を検討する。
- [Risk] `5ch.net` URLを `5ch.io` として開くため、表示中の画面URLと保存済みのタブ/履歴/ブックマークURLが異なる場合がある → 設定名と説明文で「保存データは変えず、開く先を5ch.ioにする」ことを明示する。
- [Risk] itest板URLは板host解決にBBSMenuを使うため、メニュー上に該当boardKeyがない場合は開けない → 既存と同様にエラー扱いとし、今回の変更ではフォールバック探索を増やさない。
- [Risk] 投稿時変換を行わないため、正規化を通らずに `5ch.net` の保存済みURLを直接利用する処理が残ると旧hostへ送信される可能性がある → 板/スレを開く入口は共通ナビゲーションで正規化し、投稿処理自体には追加変換を入れないことを検証する。

## Migration Plan

1. DataStoreに新しいboolean設定キーを追加し、未設定時は `true` として扱う。
2. ManifestとURL許可判定に `5ch.io` を追加する。
3. 共通ナビゲーションで板/スレを開く直前の `boardUrl` 正規化を追加する。
4. 既定BBSMenu URLを `5ch.io` 側に変更する。
5. 既存タブ、ブックマーク、履歴などから開く場合も保存データを変更せず正規化後routeで開くことを確認する。
6. 関連ユニットテストを追加/更新する。

Rollback は、設定項目と正規化処理を戻し、既定BBSMenu URLとManifest/許可リストから `5ch.io` を除外すればよい。DataStoreキーが残っても未参照であれば動作影響はない。

## Open Questions

- `https://menu.5ch.io/bbsmenu.html` が本番で安定提供されているかは実装時に確認する。
