# Change: 外部ブラウザ起動時にSlevoを除外した選択ダイアログを表示

## Why
「外部ブラウザで開く」を選んでもSlevoが候補に含まれ、再度Slevoで開かれてしまう。外部ブラウザのみを明示的に選択できるようにし、意図したアプリで開けるようにする。

## What Changes
- 「外部ブラウザで開く」操作で、Slevoを除外したブラウザのみの選択ダイアログを表示する
- 候補が存在しない場合は通知して遷移しない

## Impact
- Affected specs: open-external-browser
- Affected code: スレッド情報シートの「外部ブラウザで開く」処理、共有用のURL起動ユーティリティ
