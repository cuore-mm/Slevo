## MODIFIED Requirements

### Requirement: 同種別タブ切り替えと別種別遷移の履歴を区別する
システムは全画面タブ一覧または横スワイプによる同種別タブの切り替えで、不要な navigation back stack を積んではならないMUST NOT。BoardまたはThreadから全画面タブ一覧を開いた場合、タブ選択完了時に全画面タブ一覧をback stackから除去し、別種別タブの選択は元の画面種別とその直前のback stack entryに応じたpush、pop、replaceを行わなければならないMUST。タブ選択より前に存在したBookmarkなどのdestinationを破棄してはならないMUST NOT。選択対象の登録または解決に失敗した場合は、全画面タブ一覧を維持しなければならないMUST。

#### Scenario: タブ一覧シートで同種別タブを選ぶ
- **WHEN** ユーザーがBoard画面から全画面タブ一覧を開いて板タブを選択する
- **THEN** システムは選択中の板タブを更新し、全画面タブ一覧をback stackから除去して元のBoard destinationへ戻る
- **AND** Board destinationを追加で積まない

#### Scenario: Board画面のタブ一覧シートでThreadタブを選ぶ
- **WHEN** ユーザーがBoard画面から全画面タブ一覧を開いてスレッドタブを選択する
- **THEN** システムは選択中のスレッドタブを更新し、全画面タブ一覧をback stackから除去する
- **AND** 元のBoard destinationをback stackに残してThread destinationをpushする
- **AND** 戻る操作で元のBoard画面へ戻る

#### Scenario: Threadから開いた全画面タブ一覧でThreadタブを選ぶ
- **WHEN** ユーザーがThread画面から全画面タブ一覧を開いてスレッドタブを選択する
- **THEN** システムは選択中のスレッドタブを更新し、全画面タブ一覧をback stackから除去して元のThread destinationへ戻る
- **AND** Thread destinationを追加で積まない

#### Scenario: Thread画面のタブ一覧シートでBoardタブを選ぶ
- **WHEN** ユーザーが直前のback stack entryにBoard destinationを持つThread画面から全画面タブ一覧を開いて板タブを選択する
- **THEN** システムは選択中の板タブを更新し、全画面タブ一覧とThread destinationをpopして既存のBoard destinationへ戻る
- **AND** 背後のBoard destinationを新しいrouteで置き換えない

#### Scenario: 背後にBoardがないThreadから全画面タブ一覧を経由してBoardタブを選ぶ
- **WHEN** ユーザーが直前のback stack entryにBoard destinationを持たないThread画面から全画面タブ一覧を開いて板タブを選択する
- **THEN** システムは選択中の板タブを更新し、全画面タブ一覧を除去してThread destinationを選択済みBoard destinationへreplaceする
- **AND** 戻る操作で破棄したThread画面を再表示しない

#### Scenario: タブ一覧より前の履歴を維持する
- **WHEN** BookmarkなどのdestinationからBoardまたはThreadを開き、全画面タブ一覧で同種または別種タブを選択する
- **THEN** システムは全画面タブ一覧と規則上除去すべきBoardまたはThreadだけをback stackから除去する
- **AND** Bookmarkなどそれ以前のdestinationを維持する

#### Scenario: ルートの全画面タブ一覧からタブを選ぶ
- **WHEN** ユーザーがBoardまたはThreadを遷移元に持たないルートの全画面タブ一覧でタブを選択する
- **THEN** システムは全画面タブ一覧をback stackに残して選択したBoardまたはThread destinationへ遷移する

#### Scenario: タブ選択対象を解決できない
- **WHEN** 全画面タブ一覧で選択したタブの登録、正規化、または解決が完了しない
- **THEN** システムはBoardまたはThread destinationへ遷移せず、全画面タブ一覧を維持する

#### Scenario: 横スワイプでタブを切り替える
- **WHEN** ユーザーがBoardまたはThread画面のPagerを横スワイプして別タブへ移動する
- **THEN** システムはNavControllerのback stackを変更せず、対応する選択中タブだけを更新する
