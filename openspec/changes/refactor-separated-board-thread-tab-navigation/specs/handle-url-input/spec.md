## MODIFIED Requirements

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
