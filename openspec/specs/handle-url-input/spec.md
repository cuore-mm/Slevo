# handle-url-input Specification

## Purpose
TBD - created by archiving change unify-url-routing. Update Purpose after archive.
## Requirements
### Requirement: URL入力の判定を共通化する
システムはURL入力時に共通URLリゾルバを使用し、`docs/external/5ch.md` の入力URLパターン A〜D に一致する場合のみ板/スレへ遷移することを SHALL 要求する。対象ドメインには `5ch.net` と `5ch.io` を含めることを SHALL 要求する。

#### Scenario: PC版板URLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/` を入力する
- **THEN** システムは該当板を表示する

#### Scenario: PC版スレURLを入力する
- **WHEN** `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: itest版板URLを入力する
- **WHEN** `https://itest.5ch.net/subback/<board>` を入力する
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: itest版スレURLを入力する
- **WHEN** `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: 5ch.io のPC版板URLを入力する
- **WHEN** `https://<server>.5ch.io/<board>/` を入力する
- **THEN** システムは該当板を表示する

#### Scenario: 5ch.io のPC版スレURLを入力する
- **WHEN** `https://<server>.5ch.io/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

#### Scenario: 5ch.io のitest版板URLを入力する
- **WHEN** `https://itest.5ch.io/subback/<board>` を入力する
- **THEN** システムは板ホストを解決し該当板を表示する

#### Scenario: 5ch.io のitest版スレURLを入力する
- **WHEN** `https://itest.5ch.io/<server>/test/read.cgi/<board>/<threadKey>/` を入力する
- **THEN** システムは該当スレを表示する

### Requirement: URL入力の対象外判定
システムは入力パターン A〜D に一致しないURLを入力された場合、ダイアログ内でエラーを表示することを SHALL 要求する。

#### Scenario: dat形式のURLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/dat/<threadKey>.dat` を入力する
- **THEN** システムはダイアログ内にエラーを表示する

#### Scenario: oyster形式のURLを入力する
- **WHEN** `https://<server>.5ch.net/<board>/oyster/<prefix>/<threadKey>.dat` を入力する
- **THEN** システムはダイアログ内にエラーを表示する

### Requirement: URL入力処理の検証状態完了保証
システムは URL 入力処理を非同期に実行する場合、板 URL、スレッド URL、itest 板 URL のいずれの分岐でも検証状態を必ず完了させなければならないMUST。ナビゲーション、route 正規化、ホスト解決の途中で例外が発生しても、URL 入力ダイアログを検証中のまま残してはならないMUST NOT。

#### Scenario: スレッドURL遷移中に例外が発生する
- **WHEN** ユーザーがスレッド URL を入力し、route 正規化またはナビゲーション中に例外が発生する
- **THEN** システムは URL 検証状態を終了し、ダイアログを永続的な検証中状態にしない

#### Scenario: 板URL遷移中に例外が発生する
- **WHEN** ユーザーが板 URL を入力し、route 正規化またはナビゲーション中に例外が発生する
- **THEN** システムは URL 検証状態を終了し、ダイアログを永続的な検証中状態にしない

### Requirement: URL入力の責務分離
システムは URL 入力ダイアログの Composable に、URL 種別判定、非同期ホスト解決、route 正規化、タブ登録、タブ選択、ナビゲーション後処理を長い inline 処理として持たせてはならないMUST NOT。Composable は入力イベントを ViewModel または専用ハンドラーへ委譲し、描画とイベント接続を中心に扱わなければならないMUST。URL 入力処理の結果として板またはスレッドを開く場合、システムは正規化済み route からタブ登録・選択を行い、必要な場合のみ板画面またはスレッド画面種別へ遷移しなければならないMUST。

#### Scenario: URL入力イベントを委譲する
- **WHEN** ユーザーが URL 入力ダイアログで開く操作を実行する
- **THEN** Composable は URL 文字列を ViewModel または専用ハンドラーへ渡し、URL 種別ごとの詳細処理を直接 inline で実行しない

#### Scenario: URL入力結果で板タブを開く
- **WHEN** URL 入力処理の結果として板を開く必要がある
- **THEN** システムは正規化済み板 route で板タブを登録・選択し、必要な場合のみ板画面種別へ遷移する

#### Scenario: URL入力結果でスレッドタブを開く
- **WHEN** URL 入力処理の結果としてスレッドを開く必要がある
- **THEN** システムは正規化済みスレッド route でスレッドタブを登録・選択し、必要な場合のみスレッド画面種別へ遷移する

