# navigation-route-normalization Specification

## Purpose
TBD - created by archiving change refactor-route-normalization-settings. Update Purpose after archive.
## Requirements
### Requirement: 永続化済み設定値によるroute正規化
システムは `5ch.net` の板/スレ route を開く直前に、永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいて `boardUrl` を正規化することを SHALL 要求する。正規化判定は `TabSessionStore` の一時キャッシュや未読込状態に依存してはならない。

#### Scenario: 未設定デフォルトオンで起動直後に5ch.net routeを開く
- **WHEN** 設定が未設定で、アプリ起動直後に `https://agree.5ch.net/operate/` の板routeまたはスレrouteを開く
- **THEN** システムは永続化設定のデフォルトオンを適用し、`boardUrl` を `https://agree.5ch.io/operate/` に正規化してから遷移する

#### Scenario: 設定オフで起動直後に5ch.net routeを開く
- **WHEN** ユーザーが過去に設定をオフにしており、アプリ起動直後に `https://agree.5ch.net/operate/` の板routeまたはスレrouteを開く
- **THEN** システムは永続化済みの設定オフを適用し、`boardUrl` を `https://agree.5ch.net/operate/` のまま遷移する

#### Scenario: 設定オンで保存済みデータ由来の5ch.net routeを開く
- **WHEN** 設定がオンの状態で、保存済みタブ、ブックマーク、履歴、板DBのいずれかに保持された `https://agree.5ch.net/operate/` を開く
- **THEN** システムは保存済みデータを直接変更せず、開くrouteの `boardUrl` のみ `https://agree.5ch.io/operate/` に正規化する

### Requirement: 正規化済みrouteの一貫利用
システムは板/スレを開く場合、正規化済みrouteをタブ保証と画面遷移の両方に使用することを SHALL 要求する。タブ保存に使うrouteと実際に遷移するrouteが異なってはならない。

#### Scenario: スレrouteを正規化して開く
- **WHEN** 設定オンで `https://agree.5ch.net/operate/` のスレrouteを開く
- **THEN** システムは `https://agree.5ch.io/operate/` のスレタブを保証する
- **AND** システムは同じ `https://agree.5ch.io/operate/` のrouteでスレ画面へ遷移する

#### Scenario: 板routeを正規化して開く
- **WHEN** 設定オンで `https://agree.5ch.net/operate/` の板routeを開く
- **THEN** システムは `https://agree.5ch.io/operate/` の板タブを保証する
- **AND** システムは同じ `https://agree.5ch.io/operate/` のrouteで板画面へ遷移する

### Requirement: NavigationExtensionsの責務限定
システムは `navigateToBoard` / `navigateToThread` の共通ナビゲーション関数で、設定値の取得や `5ch.net` から `5ch.io` への正規化を行わないことを SHALL 要求する。これらの関数は、呼び出し元から渡されたrouteを使ってタブ保証と画面遷移を行うことを SHALL 要求する。

#### Scenario: 正規化済みrouteをナビゲーション関数に渡す
- **WHEN** 呼び出し元が正規化済みrouteを `navigateToThread` に渡す
- **THEN** システムは渡されたrouteを変更せず、同じrouteでタブ保証と画面遷移を行う

#### Scenario: 未正規化routeをナビゲーション関数が受け取る
- **WHEN** 呼び出し元が未正規化routeを `navigateToBoard` または `navigateToThread` に渡す
- **THEN** システムはナビゲーション関数内では設定値を参照せず、渡されたrouteをそのまま扱う
