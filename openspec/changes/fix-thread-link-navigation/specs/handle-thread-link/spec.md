## MODIFIED Requirements

### Requirement: スレ内リンクの判定を共通化する
システムはスレ内リンクをタップした際、共通URLリゾルバで判定しスレに該当する場合のみアプリ内遷移することを SHALL 要求する。スレに該当する場合、システムは永続化済みの `5ch.net` を `5ch.io` として開く設定値を取得し、その値に基づいてrouteを正規化してからスレッドタブ保証とスレッドタブ選択を行うことを SHALL 要求する。板画面からスレッドリンクを開く場合、システムはスレッド画面 route を履歴に積み、戻る操作で直前の板画面へ戻れるようにすることを SHALL 要求する。スレッド画面からスレッドリンクを開く場合、システムは現在のThread destinationを維持し、同種のrouteを履歴へ追加してはならないMUST NOT。

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

#### Scenario: スレッド画面から別スレッドのリンクを開く
- **WHEN** ユーザーがスレッドAの画面でスレッドBへのリンクを選択する
- **THEN** システムはスレッドBのタブを保証して選択し、現在のThread destinationとNavigation back stackを維持する
- **AND** Androidの戻る操作ではスレッドAではなく、現在のThread destinationへ入る前の画面へ戻る

#### Scenario: スレッド画面から既存タブのリンクを開く
- **WHEN** ユーザーがスレッドAの画面で、既にタブとして存在するスレッドBへのリンクを選択する
- **THEN** システムはスレッドBのタブを重複作成せず、既存タブを選択する
