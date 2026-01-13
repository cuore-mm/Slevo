## Context
BoardViewModel と ThreadViewModel はどちらも画面表示までの初期化を担うが、
UIState更新・DB補完・監視開始・BaseViewModel.initialize の順序や責務が一致していない。
結果として、同等の画面でも挙動の差が生まれやすく、修正時の認知負荷が高い。

## Goals / Non-Goals
- Goals:
  - 初期化処理の順序と責務を統一し、Board/Thread 間で同じ読み方ができる
  - 重複初期化のガードと強制再初期化の導線を揃える
  - 初期化で必要な UIState 反映が同じタイミングで行われるように整理する
- Non-Goals:
  - 画面構成や UI デザインの変更
  - 新規の共通基盤（大きな抽象化）の導入
  - 既存のデータ取得ロジック自体の置き換え

## Decisions
- Decision: Board/Thread の初期化を同一のフェーズ順で実行する
  - 1) UIState の基礎情報反映（BoardInfo/ThreadInfo、postDialog placeholder 等）
  - 2) 永続データの補完（ensureBoard、noname 取得）
  - 3) 監視開始（ブックマーク、NG、設定）
  - 4) BaseViewModel.initialize の実行
- Decision: 重複初期化の抑止キーを共通パターンで生成し、refresh 系は強制再初期化を明示する

## Alternatives considered
- 共有クラス（ScreenInitCoordinator）を導入して Board/Thread の差分だけ差し込む
  - 将来の再利用性は高いが、今回のスコープでは過剰と判断

## Risks / Trade-offs
- 既存の初期化順序に依存した副作用がある場合、順序変更で挙動が変わる可能性がある
  - 既存の UIState 更新タイミングと監視開始の影響を確認しながら段階的に整理する

## Migration Plan
- 既存処理を壊さない形で初期化フェーズを整理し、最終的に順序を揃える
- Board/Thread で共通の初期化パターンに揃えた後に UI 動作確認を行う

## Open Questions
- 初期化フェーズの「監視開始」に含める対象（NG/Bookmark/Settings）の最終的な順序の優先度
- Thread で実施しているタブ情報更新の責務をどのタイミングに固定するか
