## ADDED Requirements

### Requirement: contextual Tabs内の移動有無で履歴統合を区別する
システムはBoardまたはThreadから開いたcontextual TabsでBoardまたはThreadを直接選択した場合だけ、Tabsと遷移元詳細画面を選択先へ統合しなければならない（MUST）。contextual TabsからBookmarkまたはBBSサービス一覧へ移動した後にBoardまたはThreadを開く場合は、その中間履歴を保持しなければならない（MUST）。

#### Scenario: Boardから開いたTabsでBoardを直接選ぶ
- **WHEN** 履歴が`X → Board A → Tabs`で、ユーザーがTabsからBoard Bを直接選択する
- **THEN** システムは最終履歴を`X → Board B`にする
- **AND** BackでTabsまたはBoard Aを再表示しない

#### Scenario: Boardから開いたTabsを経由してBookmarkからBoardを開く
- **WHEN** 履歴が`X → Board A → Tabs → Bookmark`で、ユーザーがBookmarkからBoard Bを開く
- **THEN** システムは最終履歴を`X → Board A → Tabs → Bookmark → Board B`として保持する
- **AND** Backは`Board B → Bookmark → Tabs → Board A → X`の順で戻る

#### Scenario: Threadから開いたTabsでThreadを直接選ぶ
- **WHEN** 履歴が`X → Thread A → Tabs`で、ユーザーがTabsからThread Bを直接選択する
- **THEN** システムは最終履歴を`X → Thread B`にする

#### Scenario: contextual Tabsで別種別を直接選ぶ
- **WHEN** BoardまたはThreadから開いたTabsでユーザーが反対種別のタブを直接選択する
- **THEN** システムは既存のBoard / Thread間のpush、popまたはreplace規則を適用し、中間Tabsを最終履歴に残さない
