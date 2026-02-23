## ADDED Requirements

### Requirement: 画面共通ジェスチャースクロール実行
システムは `ThreadScreen` と `BoardScreen` で利用する `ToTop` / `ToBottom` アクション実行を共通化し、同一条件で同一スクロール結果を返さなければならないMUST。

#### Scenario: ToTop 実行時に先頭へ移動する
- **WHEN** ユーザーが `ToTop` ジェスチャーを実行する
- **THEN** システムは `showBottomBar` を呼び出した後にリスト先頭へスクロールする

#### Scenario: ToBottom 実行時に末尾ターゲットへ移動する
- **WHEN** ユーザーが `ToBottom` ジェスチャーを実行する
- **THEN** システムは viewport 更新待ちと末尾ターゲット算出を共通ロジックで実行し、算出インデックスへスクロールする

### Requirement: 末尾スクロール補助ロジックの統一
システムは末尾移動で使用する viewport 更新待ちおよび末尾インデックス算出を単一の共通補助として提供し、画面ごとの実装差分を持ってはならないMUST。

#### Scenario: totalItemsCount が利用可能な場合
- **WHEN** レイアウト情報に `totalItemsCount > 0` がある
- **THEN** システムは `totalItemsCount - 1` を末尾ターゲットとして返す

#### Scenario: レイアウト情報が未確定な場合
- **WHEN** `totalItemsCount == 0` かつ fallback 件数が存在する
- **THEN** システムは fallback 件数を使用して末尾ターゲットを返す

### Requirement: GestureHint invalid リセットの共通化
システムは `GestureHint.Invalid` 表示時の遅延リセットを共通 effect で管理し、`ThreadScreen` と `BoardScreen` で同じタイミングで `Hidden` に戻さなければならないMUST。

#### Scenario: invalid 表示後にタイムアウトで非表示化される
- **WHEN** 画面のジェスチャーヒント状態が `Invalid` になる
- **THEN** システムは既定遅延後に `Hidden` へ戻す

### Requirement: Scaffold ジェスチャーアクションの共通ディスパッチ
システムは `ThreadScaffold` と `BoardScaffold` の `onGestureAction` ハンドリングで共通アクションのディスパッチ手順を共有し、画面固有処理は注入可能なハンドラで分離しなければならないMUST。

#### Scenario: 共通アクションが同一規則で処理される
- **WHEN** `Refresh`、`Search`、`OpenTabList`、`OpenBookmarkList`、`OpenBoardList`、`OpenHistory`、`OpenNewTab`、`SwitchToNextTab`、`SwitchToPreviousTab`、`CloseTab` が発生する
- **THEN** システムは共通ディスパッチ規則に従って処理し、画面ごとの差分は固有ハンドラ側でのみ処理する

### Requirement: 画像ビューア遷移準備の共通化
システムは画像ビューア遷移時の URL エンコード、初期インデックス補正、route 構築を共通ロジックへ集約し、`ThreadScreen` / `ThreadScaffold` / `BoardScaffold` で同じ遷移規則を適用しなければならないMUST。

#### Scenario: 画像URL配列が空の場合は遷移しない
- **WHEN** 画像 URL 配列が空で画像遷移要求が発生する
- **THEN** システムは route を生成せず画面遷移を行わない

#### Scenario: 画像URL配列が存在する場合は共通ルールで遷移する
- **WHEN** 画像 URL 配列が1件以上あり遷移要求が発生する
- **THEN** システムは URL エンコードと `initialIndex` の clamp を共通ロジックで実行し、`AppRoute.ImageViewer` を生成する
