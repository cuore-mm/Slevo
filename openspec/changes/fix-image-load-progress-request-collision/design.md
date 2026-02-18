## Context

現行実装は `ImageLoadProgressRegistry` が `Map<url, progressState>` を保持し、`finish(url)` が URL 単位で状態を削除する。これにより同一 URL の並行読み込み時、先に完了したリクエストが他方の表示中リクエスト状態まで削除し、進捗表示が不安定になる。

進捗取得は `ImageLoadProgressInterceptor` から行っており、UI は URL 単位の `progressByUrl` を購読している。既存 UI 呼び出し側への影響を最小化しつつ、内部状態のみ request 単位へ細分化する設計が必要である。

## Goals / Non-Goals

**Goals:**
- 同一 URL の並行読み込みで進捗状態が相互干渉しないようにする。
- 既存 UI が参照する `progressByUrl` 契約を維持する。
- 読み込み完了・失敗・キャンセル時に、該当 request のみを確実に終了できるようにする。

**Non-Goals:**
- 進捗インジケータの見た目変更。
- 画像読み込み戦略（Coil キャッシュ、デコード）自体の変更。
- 進捗表示対象画面の追加。

## Decisions

1. **Registry の内部キーは requestId 単位に変更する。**
   - 各 `start/update/finish` は `requestId` と `url` を受け取り、内部で `Map<requestId, Entry(url, state)>` を管理する。
   - 理由: 同一 URL の並行読み込みを独立したライフサイクルで追跡できる。

2. **公開値 `progressByUrl` は内部 request 状態を URL 単位で集約して算出する。**
   - 集約は URL ごとに request 一覧を折り畳んで生成し、UI 側は従来どおり URL キー参照を継続する。
   - 理由: 呼び出し側 API 互換を維持し、変更範囲を最小化する。

3. **Interceptor で requestId を生成して Registry API へ渡す。**
   - request ごとに一意な ID を払い出し、read 完了または例外時に同 ID で `finish` する。
   - 理由: request ライフサイクルと進捗状態の対応を保証する。

4. **集約ルールは保守的に設計する。**
   - 同一 URL 内に `Indeterminate` が含まれる間は `Indeterminate` を優先し、それ以外は `Determinate` の代表値を表示する。
   - 理由: 並行時に進捗表示が過剰に先行して見えることを避ける。

## Risks / Trade-offs

- [リスク] request 状態のリークでメモリに残留する可能性 → [緩和] 完了/例外パスで `finish(requestId)` を必ず実行し、ガードを追加する。
- [リスク] 集約ルールが体感と一致しない可能性 → [緩和] 並行読込ケースで手動確認し、必要なら代表値ロジックを調整する。
- [リスク] API 変更で呼び出し側修正漏れが起きる可能性 → [緩和] `ImageLoadProgressRegistry` 利用箇所を網羅検索して一括更新する。

## Migration Plan

1. Registry の内部データ構造と API を request 単位へ変更する。
2. Interceptor で requestId 生成と `start/update/finish` 呼び出しを切り替える。
3. 既存 UI 参照側が `progressByUrl` 互換で動作することを確認する。
4. 同一 URL 並行読込ケース（メイン画像 + サムネイル）で進捗表示干渉がないことを確認する。

## Open Questions

- requestId の生成方式を何にするか（単純インクリメント/UUID）を実装時に決定する。
