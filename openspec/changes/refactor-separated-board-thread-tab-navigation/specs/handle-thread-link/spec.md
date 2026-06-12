## MODIFIED Requirements

### Requirement: スレ内リンクの判定を共通化する
システムはスレ内リンクをタップした際、共通URLリゾルバで判定しスレに該当する場合のみアプリ内遷移することを SHALL 要求する。スレに該当する場合、システムは永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいてrouteを正規化してからスレッドタブ保証、スレッドタブ選択、スレッド画面遷移を行うことを SHALL 要求する。板画面からスレッドリンクを開く場合、システムはスレッド画面 route を履歴に積み、戻る操作で直前の板画面へ戻れるようにすることを SHALL 要求する。

#### Scenario: スレURLのリンクをタップする
- **WHEN** `https://{host}/test/read.cgi/{board}/{thread}/` のリンクをタップする
- **THEN** システムは永続化済み設定値に基づきrouteを正規化してから該当スレを表示する

#### Scenario: スレ内リンクを設定オフでタップする
- **WHEN** ユーザーが設定をオフにしており、`https://agree.5ch.net/test/read.cgi/operate/1234567890/` のリンクをタップする
- **THEN** システムは `https://agree.5ch.net/operate/` を保持したrouteで該当スレを表示する

#### Scenario: スレ内リンクを設定オンでタップする
- **WHEN** ユーザーが設定をオンにしており、`https://agree.5ch.net/test/read.cgi/operate/1234567890/` のリンクをタップする
- **THEN** システムは `https://agree.5ch.io/operate/` に正規化したrouteで該当スレを表示する

#### Scenario: 板画面からスレッドリンクを開いて戻る
- **WHEN** ユーザーが板画面でスレッドリンクを開き、表示されたスレッド画面で戻る操作を行う
- **THEN** システムはスレッドを開く前の板画面へ戻る
