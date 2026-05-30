## ADDED Requirements

### Requirement: URL入力処理の検証状態完了保証
システムは URL 入力処理を非同期に実行する場合、板 URL、スレッド URL、itest 板 URL のいずれの分岐でも検証状態を必ず完了させなければならないMUST。ナビゲーション、route 正規化、ホスト解決の途中で例外が発生しても、URL 入力ダイアログを検証中のまま残してはならないMUST NOT。

#### Scenario: スレッドURL遷移中に例外が発生する
- **WHEN** ユーザーがスレッド URL を入力し、route 正規化またはナビゲーション中に例外が発生する
- **THEN** システムは URL 検証状態を終了し、ダイアログを永続的な検証中状態にしない

#### Scenario: 板URL遷移中に例外が発生する
- **WHEN** ユーザーが板 URL を入力し、route 正規化またはナビゲーション中に例外が発生する
- **THEN** システムは URL 検証状態を終了し、ダイアログを永続的な検証中状態にしない

### Requirement: URL入力の責務分離
システムは URL 入力ダイアログの Composable に、URL 種別判定、非同期ホスト解決、route 正規化、ナビゲーション後処理を長い inline 処理として持たせてはならないMUST NOT。Composable は入力イベントを ViewModel または専用ハンドラーへ委譲し、描画とイベント接続を中心に扱わなければならないMUST。

#### Scenario: URL入力イベントを委譲する
- **WHEN** ユーザーが URL 入力ダイアログで開く操作を実行する
- **THEN** Composable は URL 文字列を ViewModel または専用ハンドラーへ渡し、URL 種別ごとの詳細処理を直接 inline で実行しない

#### Scenario: ナビゲーション要求を画面側で実行する
- **WHEN** URL 入力処理の結果として板またはスレッドへの遷移が必要になる
- **THEN** システムは ViewModel または専用ハンドラーから得た typed な遷移要求に基づき、画面側で既存の navigation API を呼び出す
