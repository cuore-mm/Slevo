## Context

現状の `ReplyPopup` は `ThreadScreen` content ツリー内で描画されるため、Scaffold の `bottomBar` はポップアップオーバーレイの外側にある。
この構造では bottomBar タップが先に処理され、ポップアップ外タップとして閉じる経路に到達しない。

## Goals / Non-Goals

**Goals:**
- ポップアップ表示中、bottomBar タップを「ポップアップ外タップ」として扱い最上位ポップアップを閉じる。
- ポップアップ表示レイヤーを Scaffold 全体の上位に統一する。
- 既存のポップアップ配置・アニメーション・タップ起点契約を維持する。

**Non-Goals:**
- bottomBar 自体のデザイン・機能変更。
- ポップアップ段数別レイアウト仕様の変更。
- `ViewModel` のポップアップスタック管理ルール変更。

## Decisions

- **`ReplyPopup` を Scaffold 全体を覆うレイヤーへ移設する**
  - `ThreadScreen` content 内ではなく、`BbsRouteScaffold` の外側上位（同一画面スコープ）で描画する。
  - これにより bottomBar を含む全領域がポップアップ外判定対象になる。

- **ポップアップ描画責務を `ThreadScaffold` 側に寄せる**
  - `ThreadScreen` は投稿表示とタップ起点オフセット通知に責務を限定し、ポップアップ表示責務は上位へ移す。
  - 可視状態通知は既存の `popupStack.isNotEmpty()` を利用して同期する。

- **座標系と共有トランジションスコープを維持する**
  - `positionInRoot()` ベースのオフセット計算と `SharedTransitionScope` / `AnimatedVisibilityScope` を移設後も同一契約で受け渡す。
  - 既存アニメーションとタップ位置基準の見え方を維持する。

## Risks / Trade-offs

- [Risk] レイヤー移動で座標系が変わり、ポップアップ表示位置がずれる可能性。
  - Mitigation: 1〜3段表示で移設前後の表示位置を比較し、基準投稿との重なりが維持されることを確認する。

- [Risk] 既存の content 内入力無効化と上位オーバーレイで入力制御が二重になる可能性。
  - Mitigation: 移設時に重複する入力遮断処理を整理し、単一経路へ統合する。

- [Trade-off] `ThreadScaffold` 側の責務が増える。
  - Mitigation: ポップアップ関連の引数群をまとまりで管理し、UI責務境界をコメントで明示する。
