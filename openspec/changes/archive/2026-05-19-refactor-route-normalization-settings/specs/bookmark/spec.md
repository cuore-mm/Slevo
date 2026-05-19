## ADDED Requirements

### Requirement: ブックマークから開くrouteの正規化
システムはブックマークから板またはスレを開く場合、ブックマークに保存されたURLを変更せず、開くために生成したrouteのみを永続化済み設定値に基づいて正規化することを SHALL 要求する。

#### Scenario: 設定オンで5ch.netスレブックマークを開く
- **WHEN** 設定がオンの状態で、`https://agree.5ch.net/operate/` を保持するスレブックマークを開く
- **THEN** システムはブックマークの保存URLを変更しない
- **AND** システムは `https://agree.5ch.io/operate/` に正規化したrouteでスレを表示する

#### Scenario: 設定オフで5ch.netスレブックマークを開く
- **WHEN** 設定がオフの状態で、`https://agree.5ch.net/operate/` を保持するスレブックマークを開く
- **THEN** システムはブックマークの保存URLを変更しない
- **AND** システムは `https://agree.5ch.net/operate/` のrouteでスレを表示する

#### Scenario: 起動直後にブックマークから5ch.netスレを開く
- **WHEN** アプリ起動直後に、`https://agree.5ch.net/operate/` を保持するスレブックマークを開く
- **THEN** システムは設定Flowの初期キャッシュではなく永続化済み設定値を使ってrouteを正規化する
