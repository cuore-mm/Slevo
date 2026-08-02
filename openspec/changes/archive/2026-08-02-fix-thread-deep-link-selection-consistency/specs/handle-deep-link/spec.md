## ADDED Requirements

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
