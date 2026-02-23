## Context

現状の失敗状態は各 Composable 内の `remember` マップで保持しており、Lazy リスト項目の破棄再生成や親コンポーザブル再生成で初期化されます。この初期化により、失敗表示固定のはずの項目が未失敗扱いへ戻り、再描画時にローディング表示と再リクエストが発生します。

本変更では、失敗状態保持の責務を `UiState`/ViewModel へ移し、UI 再生成境界をまたいでも同一状態を参照できる構成へ変更します。

## Goals / Non-Goals

**Goals:**
- 失敗状態を `UiState` 管理に移行し、再生成後も失敗表示を維持する。
- 投稿サムネイル、ビューア本体、下部サムネイルで同一の状態管理契約を適用する。
- 再取得は明示リロード操作時のみ開始する契約を維持する。

**Non-Goals:**
- 失敗時の自動リトライ導入。
- TopBar の操作追加やレイアウト変更。
- 画像取得ライブラリ設定の全面刷新。

## Decisions

1. 失敗状態は URL キーの集合/マップとして `UiState` に保持する。
   - 代替案: index キーのまま `UiState` へ移す。
   - 採用理由: 並び順変化や差し替えに対して URL キーの方が安定し、誤対応を防ぎやすいため。

2. UI は `UiState` の失敗状態を参照し、失敗中は画像リクエストを発行しない。
   - 代替案: リクエストは常に発行しつつ表示だけ失敗固定にする。
   - 採用理由: 通信/ローディング再発を抑止できず、目的に反するため。

3. 画像読み込みイベント（start/success/error/retry）は ViewModel イベントで状態遷移を統一する。
   - 代替案: 各 Composable が独自に `remember` と `UiState` を併用する。
   - 採用理由: 二重管理は整合崩れの原因になるため、単一責務へ寄せる。

## Risks / Trade-offs

- [Risk] `UiState` への失敗URL蓄積で状態が肥大化する
  → Mitigation: 表示対象一覧更新時に不要URLを pruning する。

- [Risk] 既存タップ契約（遷移可否・ページ切替）へ副作用が及ぶ
  → Mitigation: タップ契約は既存条件を維持し、状態取得元のみ変更する。

- [Risk] スレッド画面とビューアでイベント定義が分裂する
  → Mitigation: load success/error/retry のイベント名と遷移規約を共通化する。

## Migration Plan

1. スレッド画面側 `UiState`/ViewModel に失敗URL状態と更新イベントを追加する。
2. 画像ビューア側 `ImageViewerUiState`/ViewModel に失敗URL状態と更新イベントを追加する。
3. `ImageThumbnailGrid` / `ImageViewerPager` / `ImageViewerThumbnailBar` からローカル失敗保持を除去し、`UiState` 参照へ置き換える。
4. 失敗中は非リクエスト、明示リロード時のみ再取得、成功時に失敗状態解除の3条件を回帰確認する。

## Open Questions

- スレッド画面で失敗状態を保持する `UiState` の配置（既存のどの UiState へ統合するか）は実装時に現行構成を確認して確定する。
