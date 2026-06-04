## Context

現在の `TabsViewModel` は `MainActivity` で `by viewModels()` により生成され、Activity スコープでアプリ全体から参照されている。この ViewModel は、開いている板/スレッドタブ、タブ永続化、スレッドタブ更新、ページ状態、`BoardViewModel` / `ThreadViewModel` のキャッシュに加えて、タブ一覧画面だけで必要な検索モード、検索クエリ、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログの表示状態も保持している。

タブ一覧画面の UI 状態は画面ライフサイクルに紐付く ViewModel が持つのが自然である。一方、タブセッション管理は特定画面の UI 状態ではなく、DeepLink、履歴、ブックマーク、板画面、スレッド画面からも利用されるアプリ内セッション機能であるため、ViewModel ではなく非 ViewModel の状態管理コンポーネントとして表現する方が責務に合う。

## Goals / Non-Goals

**Goals:**

- タブ一覧画面固有の UI 状態を、タブ一覧画面の NavBackStackEntry / Composable ライフサイクルに紐付く ViewModel で管理する。
- 画面に直接紐づかないタブセッション管理を、ViewModel ではない `TabSessionStore` へ分離する。
- 検索、長押し選択、詳細 BottomSheet、削除待ちなどの既存ユーザー挙動を維持する。
- 子 `BoardViewModel` / `ThreadViewModel` の再利用とタブ永続化の動作を維持する。
- UI 状態収集は画面上位で行い、子 Composable へ必要な値と操作を渡す既存方針を維持する。

**Non-Goals:**

- タブ一覧の見た目、カードレイアウト、スワイプ削除、固定タブ表示、更新処理の仕様変更。
- Room / DataStore のスキーマ変更。
- `BoardViewModel` / `ThreadViewModel` の生成方式そのものの変更。
- DeepLink、履歴、ブックマークからタブを開く機能の仕様変更。

## Decisions

### 1. タブセッション管理は ViewModel ではなく `TabSessionStore` として表現する

`TabsViewModel` を Activity スコープのまま「セッション ViewModel」として残すのではなく、画面に直接紐づかない状態と操作を `TabSessionStore` へ移す。開いているタブ一覧、タブ追加/削除/固定、スレッド更新、ページ状態、`TabViewModelRegistry` による子 ViewModel 再利用は、タブ一覧画面だけではなくアプリ内の複数導線から利用されるため、画面用 ViewModel の責務として扱わない。

名称は `TabSessionStore` とする。これは、このコンポーネントがタブ操作の調停役だけではなく、アプリ内タブセッション状態の正本を `StateFlow` で公開し、状態変更操作も受け付けるためである。`TabSessionController` は操作調停の印象が強く、`TabsController` は UI タブとの意味衝突が起きやすいため採用しない。

代替案として Activity スコープ ViewModel を `TabSessionViewModel` にリネームする案があるが、名前を変えても「画面ではない機能セッションを ViewModel が所有する」構図は残る。ViewModel は画面または NavGraph の UI 状態所有者に限定し、セッション管理は通常クラスとして DI する方針を採用する。

### 2. `TabSessionStore` は `@ActivityRetainedScoped` とする

タブセッションは構成変更をまたいで維持したいが、プロセス全体で無期限に長生きさせる必要はない。そのため、スコープは `@ActivityRetainedScoped` とする。永続化済みのタブ一覧は `TabsRepository` が担当し、実行中の更新ジョブ、ページ状態、子 ViewModel キャッシュなどのセッション中状態は `TabSessionStore` が担当する。

代替案として `@Singleton` にする案があるが、アプリプロセス全体に状態が残りすぎる可能性がある。永続化の正本は Repository、実行中セッションの正本は Activity Retained のセッションコンポーネントに分ける。

`TabSessionStore` は ViewModel ではないため、`viewModelScope` を利用しない。スレッドタブ更新などの実行中ジョブを保持する場合は、Activity retained lifecycle に合わせてキャンセルされる CoroutineScope を明示的に用意し、Activity retained スコープ終了時に未完了ジョブをキャンセルする。

### 3. タブ一覧画面専用 ViewModel を新設する

タブ一覧画面専用に `TabListViewModel` 相当の ViewModel を導入する。この ViewModel はタブセッションコンポーネントが提供する状態を入力として参照し、画面固有状態を合成して `TabListUiState` 相当の状態を公開する。

画面専用 ViewModel の所有候補は以下とする。

- 検索モードと検索クエリ
- 検索済み板/スレッドタブ一覧
- 長押し選択中のタブと bounds
- 削除待ちタブ
- 詳細 BottomSheet 表示対象と表示フラグ
- URL入力ダイアログの表示・検証・エラー状態

代替案として Composable の `rememberSaveable` で一時状態を保持する案があるが、長押し選択や BottomSheet は複数 Composable にまたがるため、画面専用 ViewModel に集約した方が状態更新とテストの境界が明確になる。

### 4. 画面 ViewModel からセッションコンポーネントへの操作は明示的なコマンド委譲にする

`TabListViewModel` はタブの追加/削除/固定、スレッド更新、ページ切替、子 ViewModel 取得などのセッション操作を自分で実装せず、タブセッションコンポーネントへ委譲する。これにより、永続化と共有状態の正本を二重化しない。

画面側からは、タブ一覧画面固有の操作（検索開始、検索終了、長押し選択、詳細表示、削除確認）と、セッション操作（タブを閉じる、固定を切り替える、更新する）を区別して扱う。

### 5. 移行は段階的に行う

まず `TabsViewModel` 内のタブセッション責務を `TabSessionStore` へ抽出し、その後に `TabListViewModel` を追加して検索状態と検索フィルタを画面専用 ViewModel へ移す。続いて長押し選択、詳細 BottomSheet、削除待ち、URL入力ダイアログを移し、最後に旧 `TabsViewModel` を削除または必要最小限の画面 ViewModel へ縮退させる。

段階移行により、既存のタブ一覧 UI 仕様、スレッド更新、ナビゲーション、子 ViewModel キャッシュへの影響を小さくする。

## Risks / Trade-offs

- [Risk] `TabSessionStore` と `TabListViewModel` の両方が同じ状態を持ち、正本が曖昧になる。 → 画面固有状態は `TabListViewModel`、タブセッション状態は `@ActivityRetainedScoped` の `TabSessionStore` という境界を tasks で明示し、重複するフィールドは段階的に削除する。
- [Risk] ViewModel 以外の状態管理コンポーネントを Compose / Navigation から扱う接続コードが増える。 → 画面上位で状態とイベントを束ね、子 Composable には必要な値と操作だけを渡す。
- [Risk] `@ActivityRetainedScoped` コンポーネントが子 `BoardViewModel` / `ThreadViewModel` を保持する構成は、ViewModel 間管理の違和感を完全には解消しない可能性がある。 → 子 ViewModel キャッシュの責務と release 条件を明示し、将来的に NavGraph スコープや専用 state holder へ移す余地を残す。
- [Risk] タブ一覧から離れた際に検索状態などがリセットされ、既存挙動との差分として見える可能性がある。 → 仕様として画面固有状態は画面ライフサイクルに従うことを明文化し、タブ本体や更新状態は維持する。
- [Risk] スレッド更新中にタブ一覧画面を離れた場合の進捗表示が失われる。 → 更新処理と進捗の正本は `TabSessionStore` に残し、画面復帰時に再収集して表示する。
- [Risk] Hilt のスコープ指定や Navigation の ViewModel 取得箇所を誤ると、意図せず ViewModel 化または再生成される。 → `TabListViewModel` はタブ一覧画面の NavBackStackEntry から取得し、`TabSessionStore` は DI で `@ActivityRetainedScoped` として提供する。
- [Risk] `viewModelScope` から外すことで更新ジョブのキャンセル境界が曖昧になる。 → `TabSessionStore` 専用の Activity retained CoroutineScope を用意し、スコープ終了時にジョブをキャンセルする。

## Migration Plan

1. `TabsViewModel` の状態を、タブセッション状態、タブ一覧画面固有 UI 状態、純粋な画面 ViewModel 責務に分類する。
2. `@ActivityRetainedScoped` の `TabSessionStore` を追加し、タブセッション状態とセッション操作を移す。
3. `TabListViewModel` と `TabListUiState` を追加し、検索状態とフィルタ済み一覧を移す。
4. タブ一覧画面の上位 Composable で画面スコープの `TabListViewModel` を取得し、タブセッションコンポーネントの状態と操作を注入または受け渡しする。
5. 長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログを `TabListViewModel` へ移す。
6. `TabsViewModel` から移行済みのセッション状態と画面固有状態を削除し、不要になった場合は `TabsViewModel` 自体を削除する。
7. 既存テストを更新し、セッション管理コンポーネントの単体テストと画面固有状態の ViewModel テストを分離する。

Rollback は、移行対象を `TabsViewModel` 側の既存状態へ戻すことで可能とする。DB スキーマ変更を行わないため、データ移行や永続化ロールバックは不要。

## Open Questions

- URL入力ダイアログをタブ一覧専用 UI とみなすか、他画面からも開く導線がある場合にセッション側へ残すか。
