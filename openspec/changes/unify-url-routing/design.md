# Design: URL処理フローの共通化

## 目的
- Deep Link／URL入力／スレ内リンクのURL解析を共通化し、判定ルールの重複と差異を減らす。
- エラー通知UIは入口ごとに現状を維持する。
- URL入力の許可パターンは `docs/external/5ch.md` の A〜D に準拠させる。
- dat URL は全入口で非対応とする。

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
  - 種別: `Board` / `ItestBoard` / `Thread` / `Unknown`
  - 抽出パーツ: `server` / `boardKey` / `threadKey`
- `docs/external/5ch.md` の入力パターンA〜Dに沿った解析を提供する。
- itest板URLは `ItestBoard` として返し、ホスト解決は入口側で行う。
- dat / oyster 形式は `Unknown` として扱う。

## ResolvedUrl の実装案（具体化）
- `ResolvedUrl` を sealed interface とし、以下の形を想定する。
  - `ResolvedUrl.Board`:
    - `rawUrl: String`
    - `server: String`
    - `boardKey: String`
  - `ResolvedUrl.ItestBoard`:
    - `rawUrl: String`
    - `boardKey: String`
    - `server: String?`（未解決のため `null`）
  - `ResolvedUrl.Thread`:
    - `rawUrl: String`
    - `server: String`
    - `boardKey: String`
    - `threadKey: String`
  - `ResolvedUrl.Unknown`:
    - `rawUrl: String`
    - `reason: String`（判定失敗や未対応理由の識別）
- `UrlSource` は廃止し、種別で itest の板URLを明示する。
- 解析時に `server` が未解決（itest板URL）の場合は `null` を返し、入口側でホスト解決を行う。
- 正規化（http → https）は行わず、リゾルバは入力URLをそのまま解析する。

## 入口別のポリシー例
- Deep Link
  - 追加制約: 許可ドメイン（`*.5ch.net`, `*.bbspink.com`, `*.2ch.sc`）のみ。
  - 許可: `Board` / `ItestBoard` / `Thread`
  - 不許可: `Unknown`
  - http/https は受け付けるが正規化は行わない。
- URL入力
  - `docs/external/5ch.md` の A〜D のみを許可。
  - `Unknown` は不許可。
  - itest板URLはホスト解決に失敗したらエラー表示。
- スレ内リンク
  - `Thread`（`test/read.cgi`）のみ許可。
  - それ以外は外部ブラウザへ委譲。

## 影響範囲
- 既存の `parseThreadUrl` / `parseBoardUrl` / `parseItestUrl` は共通リゾルバ内に取り込み、入口側からの直接利用を削減する想定。
- 入口側は「解析結果の利用」「エラーUI」を担当する。
