## Context

現状の板・スレッド画面は `BbsRouteScaffold` の Pager ページごとに `TabSessionStore.getOrCreateBoardViewModel()` / `getOrCreateThreadViewModel()` を呼び、`TabViewModelRegistry` がタブ key 単位で ViewModel をキャッシュしている。これはタブ切替の体感速度とタブごとの UI 状態分離には有利だが、ViewModel の寿命を Android 標準の `ViewModelStoreOwner` ではなく独自 Map で扱うため、生成・解放・監視 Flow の停止責務が曖昧になりやすい。

特に `ThreadViewModel` はスレ本文取得、レス表示変換、NG、検索、ツリー表示、ポップアップ、ブックマーク、投稿ダイアログ、画像保存、自動更新、スクロール保存、新着情報同期を抱えており、同時に開いたスレッドタブ数ぶん重いインスタンスが常駐する。加えて `ThreadTabInfo` と `ThreadUiState` の双方に新着・既読・スクロール周辺の状態が存在し、正本が分かりにくい。

この変更では、タブを「独立した Android ViewModel の所有単位」ではなく「画面内のセッション単位」として扱う。ViewModel は板画面 route / スレッド画面 route に 1 つずつ置き、選択中タブのセッション状態とデータ層を合成して表示状態を作る。

## Goals / Non-Goals

**Goals:**

- タブごとの `BoardViewModel` / `ThreadViewModel` キャッシュを廃止できる設計にする。
- タブ固有状態、スレッド・板データの正本、画面表示用 `UiState` の責務境界を明確にする。
- `ThreadViewModel` / `BoardViewModel` を軽量な route-level ViewModel に再構成し、データ取得・変換・更新処理を UseCase / Repository / coordinator へ分離する。
- 既存のタブ切替、スクロール位置復元、新着表示、検索、ポップアップ、投稿ダイアログ、更新操作の体験を維持する。
- 段階移行できるように、互換レイヤーとテスト観点を定義する。

**Non-Goals:**

- UI デザインやナビゲーション仕様そのものの変更。
- 板・スレッドデータの保存形式を必ず変更すること。
- すべての `ThreadViewModel` 内部ロジックを一度の変更で完全に最適化すること。
- Compose の Pager やタブ一覧 UI の大規模な見た目変更。

## Decisions

### Decision 1: ViewModel はタブ単位ではなく route 単位で所有する

`BoardRouteViewModel` 相当と `ThreadRouteViewModel` 相当を、板画面 route / スレッド画面 route の `ViewModelStoreOwner` に紐づけて 1 つずつ保持する。Pager の各ページは ViewModel インスタンスを直接所有せず、選択中または表示対象タブ key を ViewModel に渡して表示状態を得る。

代替案として per-tab ViewModel を維持する案もあるが、`TabViewModelRegistry` による独自ライフサイクル管理、タブ数に比例するメモリ増加、状態重複の問題が残るため採用しない。

### Decision 2: タブ固有状態は TabSessionStore 配下の Session State に集約する

タブの並び順、選択状態、ピン留め、スクロール位置、検索クエリ、表示モード、ポップアップスタック、投稿ダイアログ下書きなど「タブを閉じるまで保持したい UI セッション状態」は `TabSessionStore` 配下に集約する。ViewModel はこれらの正本を複製せず、Flow として購読して `UiState` に反映する。

代替案として `UiState` にタブ固有状態を残す案もあるが、タブ切替時に ViewModel 側と Store 側の同期が必要になり、現在の重複状態問題を解消できない。

### Decision 3: 板・スレ内容の正本は Repository / DB / UseCase に置く

板一覧、スレ本文、パース済み投稿、既読、ブックマーク、NG 設定、投稿履歴は Repository / DB / UseCase を正本とする。ViewModel は長期キャッシュではなく、選択中タブと各データソースを合成する collector / presenter として振る舞う。

代替案として `TabSessionStore` がスレ本文や板一覧を直接保持する案もあるが、永続化・更新・キャッシュ整合性の責務が肥大化するため採用しない。

### Decision 4: 重い処理は UseCase / coordinator へ抽出して ViewModel を薄くする

レスの表示行生成、検索・NG 適用、ツリー派生情報、更新処理、新着計算、自動更新判定などは、単体テスト可能な UseCase / coordinator へ移す。ViewModel はイベントを受け取り、UseCase を呼び、結果を `UiState` と `TabSessionStore` に反映する。

代替案として ViewModel のままメソッド分割する案もあるが、route-level にした後も巨大 ViewModel が残り、テスト容易性と責務分離の改善が限定的になる。

### Decision 5: 移行は互換層を挟んだ段階移行にする

最初にタブ固有状態の定義と正本を整理し、次に Thread / Board のデータ合成処理を UseCase 化する。その後 `BbsRouteScaffold` が per-tab ViewModel を要求しない形に変更し、最後に `TabViewModelRegistry` と手動 release を削除または互換用途のみに縮小する。

一括置換は差分が大きく、スクロール復元・新着同期・投稿ダイアログなどの退行リスクが高いため採用しない。

## Risks / Trade-offs

- [Risk] 非表示タブの UI セッション状態が失われる → `TabSessionStore` にタブ固有状態を移し、タブ切替・画面離脱・タブ削除の各タイミングで保存を検証する。
- [Risk] 選択中タブのみ合成すると隣接 Pager ページの表示が遅れる → Pager 表示範囲のタブ key について必要な `UiState` を軽量に合成するか、Repository キャッシュを活用して初回表示遅延を抑える。
- [Risk] `ThreadViewModel` 分割中に既存挙動が壊れる → UseCase 抽出ごとに既存ユニットテストを追加し、移行中は互換 API を残す。
- [Risk] 自動更新やバックグラウンド更新の責務が曖昧になる → 更新ポリシーを route-level ViewModel ではなく UseCase / coordinator に置き、表示中タブと一括更新のトリガーを明示する。
- [Risk] `UiState` から正本状態を削ることで Compose 側の参照が大きく変わる → まず `UiState` のフィールドを読み取り専用の合成結果として維持し、内部の供給元だけを段階的に置き換える。

## Migration Plan

1. 現状の `ThreadUiState` / `BoardUiState` と `ThreadTabInfo` / `BoardTabInfo` の重複項目を棚卸しし、正本を `TabSessionStore`、Repository、ViewModel 合成結果に分類する。
2. `ThreadSessionState` / `BoardSessionState` 相当のタブ固有状態モデルを導入し、検索・表示モード・ポップアップ・ダイアログ下書きなどを移せる受け皿を作る。
3. スレッド表示行生成、NG・検索適用、新着計算、板スレ一覧変換を UseCase / coordinator として切り出し、既存 ViewModel から利用する。
4. route-level ViewModel を導入し、選択中タブ key とセッション状態を購読して `UiState` を生成する。
5. `BbsRouteScaffold` のページ生成を per-tab ViewModel 取得から、タブ key と route-level ViewModel の状態参照へ切り替える。
6. `TabViewModelRegistry`、`BaseViewModel.release()`、per-tab ViewModel factory 依存を削除または互換層として縮小する。
7. スクロール復元、タブ切替、新着表示、更新、投稿ダイアログ、検索、ポップアップの回帰テストを追加・更新する。

## Open Questions

- 非表示タブの検索クエリやポップアップスタックを永続化するか、プロセス内セッション状態に限定するか。
- Pager の offscreen page に対して完全な `UiState` を常時合成するか、表示直前に合成するか。
- 自動更新を「表示中タブのみ」「開いている全タブ」「ユーザー操作時のみ」のどの粒度で扱うか。
- 既存の `ThreadTabInfo` / `BoardTabInfo` にセッション状態を追加するか、新しい Session State モデルを分けるか。
