## ADDED Requirements

### Requirement: タブカードを対応する表示ページ全体へ拡大・縮小する
システムは全画面Tabsで選択された板またはスレッドのカード全体を、同じタブidentityを持つBoard / Threadの現在表示ページ全体へShared Boundsで拡大しなければならないMUST。保持中のTabsへBackで戻る場合は、現在表示ページ全体を対応カードへ縮小しなければならないMUST。タイトルカードを単独のTabs遷移先にしてはならないMUST NOT。

#### Scenario: 板カードから現在のBoardページ全体へ拡大する
- **WHEN** ユーザーが全画面Tabsの可視な板カードを選択し、同じ板タブidentityがBoardのsettle済み現在ページとして表示される
- **THEN** システムは選択カード全体を、本文、ページ背景、下部ツールバー、およびステータスバー保護を含む現在表示ページ全体へ拡大する

#### Scenario: スレッドカードから現在のThreadページ全体へ拡大する
- **WHEN** ユーザーが全画面Tabsの可視なスレッドカードを選択し、同じスレッドタブidentityがThreadのsettle済み現在ページとして表示される
- **THEN** システムは選択カード全体を、本文、ページ背景、下部ツールバー、およびステータスバー保護を含む現在表示ページ全体へ拡大する

#### Scenario: Backで元のカードへ縮小する
- **WHEN** ルートTabsから開いたBoardまたはThreadでシステムBackを実行し、同じタブidentityを持つカードが保持中のTabsでcomposeされる
- **THEN** システムは現在表示ページ全体を対応カード全体へ縮小する

#### Scenario: 対応カードが表示範囲外にある
- **WHEN** Back先の対応カードがviewport外、検索切替中、削除済み、または未composeでShared Boundsの照合が成立しない
- **THEN** システムは別のカードへ接続せず、短いfadeで画面遷移を完了する

### Requirement: ページ全体のShared Bounds対象を現在のviewportに限定する
システムはページ全体のShared Bounds対象を、Pagerがsettleして横ドラッグされていない現在表示ページのviewportに限定しなければならないMUST。前後の非表示Pagerページ、他の板・スレッドタブ、Dialog、BottomSheet、Popup、Drawer、Snackbar、およびアプリ共通chromeをページ全体の拡縮対象に含めてはならないMUST NOT。

#### Scenario: BBS Pagerを横ドラッグする
- **WHEN** BoardまたはThreadのPagerが横ドラッグ中である
- **THEN** システムはページ全体をTabsカードとのShared Bounds候補にしない

#### Scenario: Pagerの前後ページがcomposeされる
- **WHEN** 現在ページとともに前後のPagerページが事前composeされる
- **THEN** システムはsettle済み現在ページのviewportだけを共有対象とし、前後ページへ個別のページ共有keyを付けない

#### Scenario: モーダル表示をページ拡縮から除外する
- **WHEN** BoardまたはThreadにDialog、BottomSheet、またはPopupが存在する
- **THEN** システムはそれらをページShared Boundsの外側に配置し、現在表示ページ本体だけを拡縮する

### Requirement: タブ一覧カードの通常表示と操作を維持する
システムは各タブカードへ固有のページ共有identityを割り当てても、カードのサイズ、padding、形状、色、文字、アイコン、並び、検索結果、アクセシビリティ、および既存ジェスチャーを変更してはならないMUST NOT。削除、並べ替え、長押しPreview、または複数選択の操作中は対象カードをページShared Bounds候補にしてはならないMUST NOT。

#### Scenario: 通常状態でタブ一覧を操作する
- **WHEN** Shared Transitionが実行中でない状態でタブ一覧を表示・操作する
- **THEN** システムは導入前と同じカード表示、検索、タップ、close、スワイプ削除、並べ替え、長押し、および複数選択を提供する

#### Scenario: カード操作中にNavigation候補から除外する
- **WHEN** カードが削除、並べ替え、長押しPreview、または複数選択の対象になっている
- **THEN** システムはその操作を優先し、対象カードをページShared Bounds候補にしない
