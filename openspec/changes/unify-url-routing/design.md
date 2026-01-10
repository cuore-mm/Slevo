# Design: URL処理フローの共通化

## 目的
- Deep Link／URL入力／スレ内リンクのURL解析を共通化し、判定ルールの重複と差異を減らす。
- エラー通知UIは入口ごとに現状を維持する。
- URL入力の許可パターンは `docs/external/5ch.md` の A〜D に準拠させる。

## 非目的
- URL入力におけるエラートースト化など、UI通知の統一は行わない。
- データ取得（subject.txt/dat/oyster）のロジックは変更しない。

## 方式案
### 案A: 共有リゾルバ + 入口別ポリシー（採用）
- URL解析を共通の「リゾルバ」で実施し、結果は構造化された `ResolvedUrl` として返す。
- 入口は `ResolvePolicy` を持ち、許可/拒否を判定する。
- エラー通知は入口側が継続して担当する。

### 案B: 既存関数の呼び順を統一（不採用）
- `parseItestUrl` → `parseThreadUrl` → `parseBoardUrl` の順序統一のみで差異を抑える。
- 入口ごとの許可パターンを表現しにくく、`docs/external/5ch.md` に完全準拠しづらい。

## 共有リゾルバの責務
- URLを以下の観点で解析し、`ResolvedUrl` を返す。
  - 種別: `Board` / `Thread` / `Dat` / `Oyster` / `Unknown`
  - 入力系統: `Pc` / `Itest` / `Kako`（必要なら）
  - 抽出パーツ: `server` / `boardKey` / `threadKey`
- `docs/external/5ch.md` の入力パターンA〜Dに沿った解析を提供する。
- itest URLで `server` が含まれない場合は `server = null` とし、解決は入口側が行う。

## 入口別のポリシー例
- Deep Link
  - 追加制約: 許可ドメイン（`*.5ch.net`, `*.bbspink.com`, `*.2ch.sc`）のみ。
  - 許可: `Board` / `Thread`
  - 不許可: `Dat` / `Oyster` / `Unknown`
  - http → https 正規化は Deep Link 側で継続。
- URL入力
  - `docs/external/5ch.md` の A〜D のみを許可。
  - `Dat` / `Oyster` は不許可。
  - itest板URLはホスト解決に失敗したらエラー表示。
- スレ内リンク
  - `Thread`（`test/read.cgi`）と `Dat` を許可。
  - それ以外は外部ブラウザへ委譲。

## 影響範囲
- 既存の `parseThreadUrl` / `parseBoardUrl` / `parseItestUrl` は共通リゾルバ内に取り込み、入口側からの直接利用を削減する想定。
- 入口側は「解析結果の利用」「エラーUI」を担当する。

