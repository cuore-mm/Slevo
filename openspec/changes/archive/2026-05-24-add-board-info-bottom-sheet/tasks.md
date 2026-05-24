## 1. 共通情報シートUIの準備

- [x] 1.1 `ThreadInfoBottomSheet` のタイトル、サブ情報、アクションボタングリッドの構造を確認し、共通化する範囲を確定する
- [x] 1.2 タイトル、任意のサブ情報、アクションボタン一覧を受け取る共通情報シート本文コンポーネントを追加する
- [x] 1.3 アクションボタン表示情報を表す共通データ構造を追加し、コピー、外部ブラウザ、共有などの呼び出し元固有処理をコールバックで渡せるようにする
- [x] 1.4 共通情報シート本文コンポーネントの Preview を追加する

## 2. ThreadInfoBottomSheet の共通UI利用

- [x] 2.1 `ThreadInfoBottomSheetContent` を共通情報シートUIの利用へ置き換える
- [x] 2.2 スレッド情報シートでレス数、日付、勢い、既存アクション、`showBoardAction` の分岐が従来通り表示されることを確認する
- [x] 2.3 `ThreadInfoBottomSheetContentPreview` を共通UI利用後の構造に合わせて更新する

## 3. BoardInfoBottomSheet の追加

- [x] 3.1 `BoardInfoBottomSheet` を追加し、`SlevoBottomSheet` と共通情報シートUIで板名、サービス名、板用アクションを表示する
- [x] 3.2 コピーアクションで板名、板URL、板名と板URLの組み合わせを `CopyDialog` に表示する
- [x] 3.3 外部ブラウザで開くアクションで板URLを `ExternalBrowserUtil` に渡し、無効URLやブラウザ未検出時のフィードバックを既存仕様に揃える
- [x] 3.4 共有アクションで板URLを含む共有 Intent を起動する
- [x] 3.5 `BoardInfoBottomSheet` または本文コンポーネントの Preview を追加する

## 4. 板画面への接続

- [x] 4.1 `BoardUiState` の板情報表示状態を `showBoardInfoSheet` として定義し、旧 `showInfoDialog` 状態を置き換える
- [x] 4.2 `BoardViewModel` に `openBoardInfoSheet` と `closeBoardInfoSheet` を追加し、旧ダイアログ開閉関数を置き換える
- [x] 4.3 `BoardScaffold` の `TabToolBar.onTitleClick` から `openBoardInfoSheet` を呼び出す
- [x] 4.4 `BoardScaffold` で旧 `BoardInfoDialog` の表示を `BoardInfoBottomSheet` の表示に置き換える
- [x] 4.5 `BoardInfoDialog` が未使用になった場合は参照確認後に削除する

## 5. テストと検証

- [x] 5.1 `BoardViewModelTest` に板情報シートの開閉状態を検証する単体テストを追加する
- [x] 5.2 既存のスレッド情報シート関連の挙動が共通化後も変わらないことを確認する
- [ ] 5.3 `./gradlew test` でユニットテストが通ることを確認する
- [ ] 5.4 `./gradlew build` でビルドが通ることを確認する
