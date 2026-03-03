## Why

タブ一覧の下部操作群はスクロールコンテンツの上に重なるため、背景の情報量が多い場面でボタンと切替UIの視認性が落ちる。`haze` の Masking を使って下端へ向かうぼかしグラデーションを適用し、操作面の可読性を安定させる必要がある。

## What Changes

- タブ一覧画面の下部操作群に `haze` を適用し、背景コンテンツをソフトにぼかす。
- Masking を用いて、下端ほど効果が強くなるグラデーション状の見え方を導入する。
- 板/スレ切替で操作ボタン数が変わっても、固定スロット配置（左予備・中央作成・右更新）を維持したまま同じ視認性を保証する。
- 既存のタブ遷移・タブ作成・更新契約は変更せず、見た目と可読性のみを改善する。

## Capabilities

### New Capabilities
- `tablist-bottom-haze-masking`: タブ一覧下部操作群に対する Haze と Masking の適用要件、配置安定要件、視認性要件を定義する。

### Modified Capabilities
- なし

## Impact

- 対象UI: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabScreenContent.kt`
- 対象UI: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabListBottomControls.kt`
- 依存: Haze ライブラリ導入済み前提で利用方法を統一（未導入なら依存追加が必要）
- 影響範囲: タブ一覧ボトムシートの見た目とレイヤリング（機能契約・ルーティングは非変更）
