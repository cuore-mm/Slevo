## ADDED Requirements

### Requirement: 選択先との距離に応じてPagerを同期する
システムは、選択中keyの変更によりスレッドPagerを同期する場合、現在表示中タブと選択先タブが隣接していればページ移動をアニメーションし、2ページ以上離れていれば即時移動しなければならない（MUST）。同じタブが選択された場合はページ移動を行ってはならない（MUST NOT）。

#### Scenario: 新規タブを隣へ追加して選択する
- **WHEN** 現在表示中タブの直後へ新規タブを追加して選択する
- **THEN** Pagerは新規タブへアニメーション移動する

#### Scenario: 離れた既存タブを選択する
- **WHEN** 現在表示中タブから2ページ以上離れた既存タブを選択する
- **THEN** Pagerは対象タブへアニメーションなしで即時移動する

#### Scenario: 現在のタブを再選択する
- **WHEN** 現在表示中のタブと同じstable keyを選択する
- **THEN** Pagerはスクロールまたはアニメーションを開始しない

### Requirement: Pager item参照の範囲安全性
システムは、Pagerのページ数、各ページのstable key、および各ページ内容を同じ整合したタブスナップショットから解決しなければならない（MUST）。タブ追加、削除、並び替え、canonical reconciliationの途中にPagerが一時的に古いpage indexを要求しても、タブ一覧を範囲外参照してはならない（MUST NOT）。

#### Scenario: タブ追加中に新しいpage indexを要求する
- **WHEN** タブ一覧の追加更新とPager item provider更新の間にPagerが追加後の末尾page indexを要求する
- **THEN** システムは整合したタブスナップショットからpageを解決し、範囲外例外を発生させない

#### Scenario: タブ削除中に古いpage indexを要求する
- **WHEN** タブ削除後にPagerが削除前のpage indexを一時的に要求する
- **THEN** システムは範囲外のタブ内容またはstable keyを参照しない
- **AND** 有効な選択先が解決済みなら、現在pageが範囲外でも選択先へアニメーションなしで即時同期する
- **AND** 選択先自体がまだ解決できない場合だけ、次の有効な選択同期まで現在の画面を安全に維持する
