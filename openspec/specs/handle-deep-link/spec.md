# handle-deep-link Specification

## Purpose
TBD - created by archiving change add-bbs-deep-link. Update Purpose after archive.
## Requirements
### Requirement: Deep Link の受付と正規化
システムはアプリ内部で対応想定しているURLパターンに一致する http/https の Deep Link のみ受け付け、スキームの正規化を行わずに解析することを SHALL 要求する。対象は PC 版/itest 版の板・スレ 4 種とし、`2ch.sc` ドメインでは itest パターンを許可しない。

- PC版・板: `https://{server}.{domain}/{board}/`
- PC版・スレ: `https://{server}.{domain}/test/read.cgi/{board}/{threadKey}/[option]`
- itest版・板: `https://itest.{domain}/subback/{board}`
- itest版・スレ: `https://itest.{domain}/{server}/test/read.cgi/{board}/{threadKey}/[option]`
- `{domain}` は `5ch.net` / `bbspink.com` / `2ch.sc` を想定するが、itest は `5ch.net` / `bbspink.com` のみを許可する

#### Scenario: http のDeep Linkを受け付ける
- **WHEN** ユーザーが対象パターンに一致する http の Deep Link を開く
- **THEN** システムはスキームを変更せずに解析を継続する

### Requirement: Deep Link の板/スレ遷移
システムは共通URLリゾルバを用いて Deep Link を解析し、板またはスレに解決できる場合のみ遷移することを SHALL 要求する。対象ドメインには `5ch.net` と `5ch.io` を含めることを SHALL 要求する。

#### Scenario: PC版のスレURLを開く
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは該当スレを表示する

#### Scenario: itestの板URLを開く
- **WHEN** `https://itest.{domain}/subback/{board}` の Deep Link を開く
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: itestのスレURLを開く
- **WHEN** `https://itest.{domain}/{server}/test/read.cgi/{board}/{thread}/` の Deep Link を開く
- **THEN** システムは該当スレを表示する

#### Scenario: 5ch.io のPC版スレDeep Linkを開く
- **WHEN** `https://agree.5ch.io/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

#### Scenario: 5ch.io のitest版スレDeep Linkを開く
- **WHEN** `https://itest.5ch.io/agree/test/read.cgi/operate/1234567890/` の Deep Link を開く
- **THEN** システムは `agree.5ch.io` の `operate` 板にある `1234567890` スレを表示する

### Requirement: 未対応URLの通知
システムは許可ドメイン外、対象パターン外、または itest 非対応ドメインの Deep Link を受け取った場合、遷移を行わずエラートーストで通知することを SHALL 要求する。

#### Scenario: dat形式のURLを開く
- **WHEN** `https://{host}/{board}/dat/{thread}.dat` の Deep Link を開く
- **THEN** システムはエラートーストを表示する

#### Scenario: 2ch.sc の itest URL を開く
- **WHEN** `https://itest.2ch.sc/subback/{board}` の Deep Link を開く
- **THEN** システムはエラートーストを表示する

### Requirement: Deep Link の明示 command result による遷移
システムは Board／Thread Deep Link の tab registration、persistence、selection を Controller command として実行し、明示 terminal result が成功した場合だけ navigation することを SHALL 要求する。presentation または selected key Flow の観測を command success の代用にしてはならない。

#### Scenario: Board Deep Link 成功
- **WHEN** Board Deep Link の ensure/persistence と selection command が成功 result を返す
- **THEN** システムは target Board へ一度だけ navigation する

#### Scenario: Board persistence failure
- **WHEN** Board Deep Link の targeted repository command が失敗する
- **THEN** command は terminal failure で有限に完了し、navigation せず既存 selection/page を保持し、`Selected(target)` を無期限に待たない

#### Scenario: Thread Deep Link 成功
- **WHEN** Thread Deep Link の readiness、ensure/persistence、selection command が成功 result を返す
- **THEN** システムは registration を重複せず target Thread へ一度だけ navigation する

#### Scenario: caller cancellation
- **WHEN** Deep Link caller が accepted command の完了前に cancel される
- **THEN** navigation と caller 待機は停止するが accepted mutation は Controller ownership で継続し、既存画面の選択を caller 側から変更しない

#### Scenario: Controller failure または no target
- **WHEN** Controller が failure または navigation 不可の no-op result を返す
- **THEN** システムは navigation せず既存 error path と現在の selection/page を維持し、新しい UI 文言を追加しない

### Requirement: スレッド Deep Link のタブ確定後遷移
システムはスレッド Deep Link を処理するとき、スレッドタブの初期読込、対象タブ mutation の完了、Room canonical state での対象存在確認を順に待たなければならないMUST。対象を canonical state で確認した後にのみ selected key を対象へ更新し、その選択成功後にのみ thread screen へ遷移しなければならないMUST。

#### Scenario: 初回タブ読込が停止している
- **WHEN** スレッド Deep Link を受け取り、スレッドタブの初回 Room Flow emission がまだ届いていない
- **THEN** システムは現在の selection と画面を維持して readiness を待ち、タブ mutation、対象選択、thread navigation を先行実行しない

#### Scenario: 未登録 target を開く
- **WHEN** readiness 完了後に未登録スレッドの Deep Link を処理する
- **THEN** システムは対象タブの targeted mutation と canonical Flow confirmation を待ち、対象が canonical 一覧に存在することを確認してから選択し、その後に一度だけ navigation する

#### Scenario: 登録済み target を開く
- **WHEN** canonical 一覧に既に存在するスレッドの Deep Link を処理する
- **THEN** システムは対象存在を確認して選択し、選択成功後に対象 thread screen へ遷移する

#### Scenario: mutation 完了前に古い Flow を受信する
- **WHEN** Deep Link target の add が pending の間に対象を含まない古い Room Flow snapshot を受信する
- **THEN** システムは target を selected key に昇格させず、現在の selection と Pager page を維持して canonical confirmation を待つ

### Requirement: スレッド Deep Link 失敗時の選択維持と recovery
システムはスレッド Deep Link の readiness、registration、canonical confirmation、selection のいずれかが失敗した場合、処理開始前の selection を維持し、対象 thread screen へ遷移してはならないMUST NOT。システムは既存 Deep Link error notification と consume 経路を使用し、新しい UI または文言を追加してはならないMUST NOT。

#### Scenario: タブ登録が失敗する
- **WHEN** Deep Link target の repository mutation が失敗する
- **THEN** システムは pending target を破棄し、既存 selection と現在画面を維持し、既存 error notification を一度実行して Deep Link を consume する

#### Scenario: canonical target を確認できない
- **WHEN** mutation result または Flow reconciliation により対象を canonical state で確認できない
- **THEN** システムは selected key を対象または null に変更せず、navigation を行わず、既存 recovery 経路を実行する

#### Scenario: target 選択が失敗する
- **WHEN** canonical confirmation 後の selection API が対象不在を報告する
- **THEN** システムは処理前の selection を維持して navigation を行わず、既存 recovery 経路を実行する

#### Scenario: Deep Link 処理がキャンセルされる
- **WHEN** 新しい intent または lifecycle により未完了の Deep Link 処理がキャンセルされる
- **THEN** システムは古い target の selection、navigation、通常失敗 notification を実行せず、pending operation を cleanup する

### Requirement: Deep Link と destination の登録処理を重複させない
システムは同じ thread route に対して Deep Link handler と thread destination が競合するタブ登録または選択 operation を発行してはならないMUST NOT。両 entry point は同じ readiness、mutation completion、canonical confirmation 契約を使用しなければならないMUST。

#### Scenario: Deep Link 成功後に ThreadScaffold を表示する
- **WHEN** Deep Link handler が canonical target を選択して thread destination へ遷移する
- **THEN** destination は対象が選択済み canonical tab であることを認識し、同じ ensure/select mutation を再発行しない

#### Scenario: Deep Link 以外から thread route を表示する
- **WHEN** tab click など別 entry point から未確認 route を thread destination が受け取る
- **THEN** destination は共通 completion 契約で readiness と registration を待ち、canonical confirmation 前に選択しない
