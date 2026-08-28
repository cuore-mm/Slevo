## ADDED Requirements

### Requirement: ドラッグ中の一時順序とセッション正本の境界
タブ一覧のViewModelは、実行中のドラッグに限り、開始前と現在のstable key順序を画面ライフサイクルに紐づく一時状態として保持しなければならない（SHALL）。タブEntityまたは表示メタデータのコピーを一時順序へ保持してはならない（MUST NOT）。

#### Scenario: ドラッグ中に表示情報が更新される
- **WHEN** ViewModelがstable keyの一時順序を保持している間にタイトル、レス数、固定状態等の表示情報が更新される
- **THEN** 画面は最新のタブ情報を一時key順序で並べて表示する

### Requirement: ドロップ後の所有権移譲
ViewModelは、正常なドロップ時に最終key順序をTabSessionStore経由でDomain Controllerへ渡し、Controllerがpending projectionを受理した後に一時順序を破棄しなければならない（SHALL）。ViewModelがRoom canonical確認や保存Successをpresentationの差分から推論してはならない（MUST NOT）。

#### Scenario: Controllerがreorderを受理する
- **WHEN** ViewModelが最終key順序を送信し、Controllerがpending reorderをpresentationへ登録する
- **THEN** ViewModelは一時順序を破棄し、以後の表示順序をTabSessionStoreへ戻す

#### Scenario: ドラッグ中に画面を離れる
- **WHEN** 正常なドロップ前にタブ一覧画面のライフサイクルが終了する
- **THEN** ViewModelは一時順序を破棄し、永続化要求を発行しない

### Requirement: pointer機構状態の局所管理
pointer ID、down座標、長押し時間、touch slop、drag offsetおよびライブラリ内部のdragging keyは、画面の業務状態としてViewModelへ毎イベント通知してはならない（MUST NOT）。ViewModelはメニューのPreview/Open状態とstable key順序だけを公開しなければならない（SHALL）。

#### Scenario: ドラッグ位置が連続更新される
- **WHEN** 利用者がカードをドラッグしてpointer座標とoffsetが連続的に変化する
- **THEN** 描画機構は位置を局所的に更新し、ViewModelには順序が入れ替わった時だけkey順序の変更を通知する
