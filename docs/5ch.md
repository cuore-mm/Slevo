# 5ch.net / bbspink.com 取得仕様

このドキュメントでは、**5ch.net** および **bbspink.com** のURL構造と、専用ブラウザ（専ブラ）がデータを取得するための仕様をまとめる。

### bbspink.com の扱いについて
**bbspink.com** は 5ch.net と同一のシステムを採用しており、ドメイン名以外のURL構造・データ形式は共通である。
したがって、本ドキュメントにおける `5ch.net` の記述は、特記がない限り `bbspink.com` にも同様に適用される。

*   **共通事項**: URLのディレクトリ構成、`subject.txt` や `dat` のデータフォーマット、パラメータ仕様。
*   **相違点**: ホストのドメインが `*.bbspink.com` となる点。

---

## 1. URL パターン一覧

以下は 5ch.net 関連で登場する主要なURL形式である。

### 一般的なブラウザ向け (HTML)
*   **板トップ**
    *   `https://<server>.5ch.net/<board>/`
*   **スレッド (現行)**
    *   `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/[option]`
*   **スレッド (過去ログ)**
    *   `https://kako.5ch.net/test/read.cgi/<board>/<threadKey>/[option]`

### スマートフォンブラウザ向け (itest)
*   **板トップ (subback)**
    *   `https://itest.5ch.net/subback/<board>`
*   **スレッド**
    *   `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/[option]`

### 専用ブラウザ・データ取得向け
*   **スレッド一覧 (subject.txt)**
    *   `https://<server>.5ch.net/<board>/subject.txt`
*   **板設定 (SETTING.TXT)**
    *   `https://<server>.5ch.net/<board>/SETTING.TXT`
*   **スレッドデータ (dat - 現行)**
    *   `https://<server>.5ch.net/<board>/dat/<threadKey>.dat`
*   **スレッドデータ (dat - 過去ログ/oyster)**
    *   `https://<server>.5ch.net/<board>/oyster/<threadKey上位4桁>/<threadKey>.dat`

---

## 2. データ取得仕様詳細

このセクションでは、ユーザーが入力する **「一般的なブラウザ向けURL (PC/HTML)」** および **「スマートフォンブラウザ向けURL (itest)」** を起点に、アプリがデータを取得するためのエンドポイント（`dat` や `subject.txt`）へ変換するロジックを定義する。

**※ 本仕様では、`dat` や `subject.txt` のURLが直接入力されるケースは考慮しない。**

---

### 2.0 前提：識別子とURLパーツ

内部処理においては以下の識別子を抽出し、データ取得用URLを組み立てる。

- `server` (host): サーバ名（例：`agree`） ※ドメイン `5ch.net` は省略して扱う場合がある
- `board` : 板キー（例：`operate`）
- `threadKey` : スレッドID（UNIX時刻由来の数値文字列）
- `prefix` : 過去ログ（`oyster`）のディレクトリ名（`threadKey` 先頭4桁）

---

### 2.1 入力URLのパターン判定

ユーザー入力URLを以下の4パターンに分類する。

#### A. PC版・板URL
- パターン: `https://<server>.5ch.net/<board>/`
- 例: `https://agree.5ch.net/operate/`

#### B. PC版・スレURL
- パターン: `https://<server>.5ch.net/test/read.cgi/<board>/<threadKey>/[option]`
- 例: `https://agree.5ch.net/test/read.cgi/operate/1767525739/`
- 備考: `kako.5ch.net` の場合も含むが、取得ロジックとしてはサーバー名が `kako` となるだけである。

#### C. itest版・板URL (subback)
- パターン: `https://itest.5ch.net/subback/<board>`
- 例: `https://itest.5ch.net/subback/operate`
- **注意**: URL内に `server` 情報が含まれない。

#### D. itest版・スレURL
- パターン: `https://itest.5ch.net/<server>/test/read.cgi/<board>/<threadKey>/[option]`
- 例: `https://itest.5ch.net/agree/test/read.cgi/operate/1767525739/l50`

---

### 2.2 データ取得URLへの変換ルール

入力パターンごとに、データ取得用URL（`subject.txt` または `dat`）への変換を行う。

#### 2.2.1 板の閲覧（PC版 A / itest版 C）→ スレ一覧 (subject.txt)

板が開かれた場合、スレッド一覧 `subject.txt` を取得する。

**変換ロジック**

1.  **`<board>` の特定**
    *   (A) PC版: URLパスから抽出。
    *   (C) itest版: URLパス `subback/` の後ろから抽出。
2.  **`<server>` の特定**
    *   (A) PC版: ドメイン部分 `<server>.5ch.net` から抽出。
    *   (C) itest版: **URLに含まれないため、アプリ内の板一覧データ（BBSMENU等）を参照し、`<board>` に対応する `<server>` を解決する。**
3.  **URL生成**
    *   `https://<server>.5ch.net/<board>/subject.txt`

---

#### 2.2.2 スレッドの閲覧（PC版 B / itest版 D）→ 本文 (dat)

スレッドが開かれた場合、スレッドデータ `dat` を取得する。

**変換ロジック**

1.  **各パーツの抽出**
    *   (B) PC版:
        *   `<server>`: ドメインから抽出。
        *   `<board>`, `<threadKey>`: パス `/test/read.cgi/<board>/<threadKey>` から抽出。
    *   (D) itest版:
        *   `<server>`: パス先頭 `/<server>/test/...` から抽出。
        *   `<board>`, `<threadKey>`: パス後続部分から抽出。
2.  **URL生成**
    *   `https://<server>.5ch.net/<board>/dat/<threadKey>.dat`

---

### 2.3 過去ログへのフォールバック

上記 2.2.2 で生成した `dat` URL での取得に失敗した場合（404 Not Found 等）、過去ログ倉庫 (`oyster`) を試行する。

- **フォールバック先URL:**
  `https://<server>.5ch.net/<board>/oyster/<prefix>/<threadKey>.dat`
  - `<prefix>`: `threadKey` の先頭4桁

---

## 3. 取得フローまとめ

### 3.1 板URL (PC/itest) が入力された場合
1.  URLパターン判定 (A or C)。
2.  `<board>` を抽出。itest (C) の場合は板マスタから `<server>` を補完。
3.  `subject.txt` (`https://<server>.5ch.net/<board>/subject.txt`) を取得。
4.  スレ一覧をパースして表示。

### 3.2 スレURL (PC/itest) が入力された場合
1.  URLパターン判定 (B or D)。
2.  `<server>`, `<board>`, `<threadKey>` を抽出。
3.  現行 `dat` (`https://<server>.5ch.net/<board>/dat/<threadKey>.dat`) の取得を試行。
4.  **成功:** パースして表示。
5.  **失敗:** 過去ログ `oyster` (`.../oyster/<prefix>/<threadKey>.dat`) の取得を試行。

---

## 4. エンドポイント仕様

### 4.1 スレ一覧（subject.txt）

**URL**
- `https://<server>.5ch.net/<board>/subject.txt`

**文字コード**
- Shift_JIS

**フォーマット**
- 1行につき1スレッド  
-書式：`[スレッド番号].dat<>[スレッドタイトル] ([レス数])`

**例**
```
1767525739.dat<>スレタイ例 (123)
1766802272.dat<>雑談スレッド ★18 (456)
```

**パース指針**
- `<>` で2分割（左：`xxxx.dat`、右：`タイトル (レス数)`）  
- 左側は `.dat` を除去して `threadKey` として扱う  
- 右側末尾の `(数字)` をレス数として抽出、残りをタイトル  
- タイトルは HTML を含む場合があるためデコードする

---


### 4.2 現行スレ本文（dat）

**URL**
- `https://<server>.5ch.net/<board>/dat/<threadKey>.dat`

**文字コード**
- Shift_JIS

**レスポンス**
- 1レス = 1行  
- 区切り：`<>`  
- 改行：LF

**1行のフォーマット**
`[名前]<>[メール]<>[日付+ID等]<>[本文]<>[スレッドタイトル]`

**重要な挙動**
- スレッドタイトルは **1番レスにのみ入る**のが基本  
  → `1` を含まない差分取得ではタイトルが欠落し得る
- `日付+ID等` には `ID:xxxxx` や `BE:xxxx-xxxxx` が混在することがある

**本文・名前の整形**
- `名前` にHTMLタグが混ざることがある  
- `本文` は `<br>` 改行やHTMLエンティティ（例：`&amp;`）を含むことがある  
- 推奨整形：  
  - `<br>` → `\n`  
  - HTMLエンティティをデコード  
  - タグ除去（リンク/アンカーを保持するかはUI方針で決める）

---


### 4.3 過去ログ本文（oyster / dat）

**URL**
- `https://<server>.5ch.net/<board>/oyster/<prefix>/<threadKey>.dat`

**文字コード / フォーマット**
- 現行 `dat` と同様

---


### 4.4 掲示板設定（SETTING.TXT）

**URL**
- `https://<server>.5ch.net/<board>/SETTING.TXT`

**文字コード**
- Shift_JIS

**フォーマット**
- LF区切り、1行が `KEY=VALUE`  
- **最初の `=` までがKEY**（VALUEに `=` が含まれる可能性あり）

**主なキー（例）**
- `BBS_TITLE_ORIG`：板タイトル（優先）
- `BBS_TITLE`：板タイトル（フォールバック）
- `BBS_NONAME_NAME`：デフォ名無し
- `BBS_THREAD_STOP`：最大レス数
- `BBS_DELETE_NAME`：あぼーん名無し
