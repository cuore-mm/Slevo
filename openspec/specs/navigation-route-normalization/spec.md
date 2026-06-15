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
システムは板/スレを開く場合、正規化済みrouteをタブ保証、タブ選択、画面遷移に一貫して使用することを SHALL 要求する。タブ保存に使うroute、選択中タブ key の導出に使うroute、実際に遷移するrouteが異なってはならない。

#### Scenario: スレrouteを正規化して開く
- **WHEN** 設定オンで `https://agree.5ch.net/operate/` のスレrouteを開く
- **THEN** システムは `https://agree.5ch.io/operate/` のスレタブを保証する
- **AND** システムは同じ `https://agree.5ch.io/operate/` から選択中スレッドタブ key を更新する
- **AND** システムは同じ `https://agree.5ch.io/operate/` のrouteでスレ画面へ遷移する

#### Scenario: 板routeを正規化して開く
- **WHEN** 設定オンで `https://agree.5ch.net/operate/` の板routeを開く
- **THEN** システムは `https://agree.5ch.io/operate/` の板タブを保証する
- **AND** システムは同じ `https://agree.5ch.io/operate/` から選択中板タブ key を更新する
- **AND** システムは同じ `https://agree.5ch.io/operate/` のrouteで板画面へ遷移する

### Requirement: NavigationExtensionsの責務限定
システムは共通ナビゲーション関数で、設定値の取得や `5ch.net` から `5ch.io` への正規化を行わないことを SHALL 要求する。共通関数は、呼び出し元から渡された正規化済みrouteを使い、タブ登録、タブ選択、画面遷移のうち呼び出し元が要求した責務だけを実行することを SHALL 要求する。タブ選択だけを行う操作で NavController の back stack を追加してはならないMUST NOT。

#### Scenario: 正規化済みrouteをタブ選択関数に渡す
- **WHEN** 呼び出し元が正規化済みrouteをタブ登録・選択関数に渡す
- **THEN** システムは渡されたrouteを変更せず、同じrouteでタブ保証と選択中タブ key 更新を行う

#### Scenario: 正規化済みrouteを画面遷移関数に渡す
- **WHEN** 呼び出し元が正規化済みrouteを画面遷移関数に渡す
- **THEN** システムは渡されたrouteを変更せず、呼び出し元が指定した NavOptions で対象画面種別へ遷移する

#### Scenario: タブ選択だけの操作を実行する
- **WHEN** ユーザーがタブ一覧シートから既存タブを選択する
- **THEN** システムは選択中タブ key を更新し、同種別の back stack entry を追加しない

