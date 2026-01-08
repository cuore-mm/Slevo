# Change: BBS Deep Link を追加

## Why
外部からBBSのURLを開いた際に、板/スレをアプリ内で直接表示できるようにするため。

## What Changes
- AndroidのDeep Linkを設定し、以下のドメインを受け付ける
  - *.bbspink.com
  - *.5ch.net
  - *.2ch.sc
  - itest.5ch.net
- httpのURLはhttpsに正規化してから処理する
- 既存のURL入力/リンク遷移の解析ルールに合わせて板/スレに遷移する
- 未対応URL（dat形式や解析不能）はエラートーストを表示する

## Impact
- Affected specs: handle-deep-link
- Affected code: app/src/main/AndroidManifest.xml, app/src/main/java/com/websarva/wings/android/slevo/MainActivity.kt, app/src/main/java/com/websarva/wings/android/slevo/ui/**
