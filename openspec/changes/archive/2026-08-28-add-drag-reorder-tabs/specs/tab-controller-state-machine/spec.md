## ADDED Requirements

### Requirement: reorder commandのpending projectionとcanonical確認
Domain Controllerは、受理したreorder commandを既存のpending operationとして登録し、Room writeの完了を待たずにstable key順序をpresentationへ投影しなければならない（SHALL）。commandは、baselineより新しいRoom snapshotが期待順序へ収束したことを確認した後にだけSuccessを返さなければならない（SHALL）。

#### Scenario: reorder commandを受理する
- **WHEN** ViewModelが正常終了したドラッグのstable key順序をControllerへ渡す
- **THEN** Controllerはpending reorderを登録して投影順序を即時公開し、順序保存を開始する

#### Scenario: canonical順序を確認する
- **WHEN** 順序保存後にbaselineより新しいRoom snapshotが期待した残存key順序を通知する
- **THEN** Controllerはpending reorderを除去し、commandへSuccessを1回だけ返す

### Requirement: reorder失敗時の収束
reorder persistenceが失敗した場合、Controllerは未確認のpending reorderを除去し、最後に確認済みのRoom canonical順序をpresentationへ公開しなければならない（SHALL）。失敗したreorderを後続commandへ成功済みとして引き継いではならない（MUST NOT）。

#### Scenario: repositoryがFailureを返す
- **WHEN** reorder commandの順序保存がFailureを返す
- **THEN** Controllerはpending reorderを除去し、canonical順序へ戻してFailureを1回だけ返す

### Requirement: reorderと後続タブcommandの順序整合
Controllerは、reorderの受理後にadd、delete、pin、infoまたはscroll commandを受理した場合、各commandのstable keyに基づき受理順と最終存在intentを保たなければならない（SHALL）。reorderはタブの存在、固定状態、情報、スクロール位置を変更してはならない（MUST NOT）。

#### Scenario: reorder待機中にタブを追加する
- **WHEN** pending reorderのcanonical確認前に新規タブ追加commandを受理する
- **THEN** Controllerはreorder対象の相対順を維持し、新規タブを末尾へ投影する

#### Scenario: reorder待機中にタブを削除する
- **WHEN** pending reorderのcanonical確認前に対象タブのdelete commandを受理する
- **THEN** Controllerは削除keyを投影順序から除外し、残存タブのreorder順を維持する

### Requirement: reorder専用の順序列更新
Controllerはreorderを、タブEntity集合のfull replacementではなく`sortOrder`列だけを更新する明示的な複数行commandとして永続化しなければならない（SHALL）。通常のadd、delete、pin、info、scroll commandのtargeted persistence契約を変更してはならない（MUST NOT）。

#### Scenario: reorderを永続化する
- **WHEN** Controllerがreorder commandを実行する
- **THEN** repositoryは単一write gate・単一transaction内で順序列だけを更新し、`upsertAll`、`deleteNotIn`、全Entity置換を呼ばない
